# axin-agent 项目分析与未来规划

> 文档创建时间：2026-04-28
> 基于项目版本：0.0.1-SNAPSHOT（Spring Boot 3.5 + Spring AI 1.1.2 + DashScope）

---

## 1. 项目现有能力

本项目是一个**学习型 Agent 框架**，完成了相当扎实的基础工程建设：

| 能力模块 | 实现状态 | 说明 |
|---|---|---|
| ReAct Agent 执行引擎 | ✅ | BaseAgent → ReActAgent → ToolCallAgent → AxinManus 完整继承链 |
| 手动工具调用管理 | ✅ | 绕过 Spring AI 自动调用，通过 ToolCallingManager 完全掌控每步 |
| Web 搜索工具 | ✅ | 调用 SearchAPI.io（百度引擎），返回前 5 条结果 |
| 网页抓取工具 | ✅ | Jsoup 抓取指定 URL 内容 |
| PDF 生成工具 | ✅ | iText 9 生成中文 PDF，保存至 `./tmp/pdf/` |
| 终止信号工具 | ✅ | TerminateTool 触发 Agent 状态流转为 FINISHED |
| MCP 协议客户端 | ✅ | 支持接入外部 MCP Server（含 axin-agent-image-mcp-server） |
| RAG 双模式 | ✅ | 本地 Redis 向量库 + DashScope 云端知识库，策略模式切换 |
| 文件持久化记忆 | ✅ | FileBasedChatMemory（Kryo 序列化），已实现但**未启用** |
| Re2 提示增强 | ✅ | Re2Advisor 将用户问题重复一次提升 LLM 理解准确率 |
| SSE 流式输出 | ✅ | 支持 Flux 和 SseEmitter 两种流式输出方式 |
| 结构化输出 | ✅ | LoveReport 结构化报告，使用 jsonschema-generator |
| 恋爱顾问应用 | ✅ | LoveApp，集成 ChatMemory + RAG + Tools |

---

## 2. 项目不足与市面差距

### 2.1 工程层 Bug / 隐患

**1. PDF 中文字体依赖错误（高危）**
- 问题：`pom.xml` 中 `font-asian` 依赖的 `scope` 为 `test`，生产打包时会排除该依赖
- 影响：生产环境中文 PDF 生成必然失败
- 位置：`pom.xml` line 126，`PDFGenerationTool.java`
- 修复：将 `<scope>test</scope>` 改为 `<scope>compile</scope>`（或直接删除 scope）

**2. WebSearchTool 越界异常**
- 问题：`organicResults.subList(0, 5)` 无边界检查，搜索结果不足 5 条时抛 `IndexOutOfBoundsException`
- 位置：`WebSearchTool.java`
- 修复：改为 `subList(0, Math.min(5, organicResults.size()))`

**3. WebScrapingTool 返回原始 HTML**
- 问题：`doc.html()` 返回完整 HTML，大型页面可能消耗数千 token
- 位置：`WebScrapingTool.java`
- 修复：改为 `doc.text()` 并增加长度截断（如 maxLength=3000）

**4. FileBasedChatMemory 未启用**
- 问题：已实现 Kryo 文件持久化记忆，但 `LoveApp` 仍使用 `new InMemoryChatMemoryRepository()`
- 影响：服务重启后所有对话历史丢失
- 修复：在 `LoveApp` 构造函数中替换为 `FileBasedChatMemory`

**5. Agent 无跨请求会话**
- 问题：每次 HTTP 请求都 `new AxinManus(...)`，任务中断后无法恢复
- 位置：`AiController.java` line 87
- 影响：长任务如果网络中断则完全丢失，无法继续

**6. API Key 明文硬编码**
- 问题：`dashscope.api-key` 和 `search-api.api-key` 直接写在 `application.yml`
- 影响：代码提交到 Git 存在密钥泄露风险
- 修复：改用环境变量 `${DASHSCOPE_API_KEY}` 或配置中心

### 2.2 设计层局限

**1. 无任务规划层**
- 当前：用户输入直接进入 ReAct 循环
- 差距：市面产品（OpenManus、Dify）先做任务分解（Task Decomposition），将复杂需求拆分为有序子任务列表再执行

**2. 单 Agent 架构**
- 当前：只有 AxinManus 一个 Agent 角色
- 差距：Planner + Researcher + Coder + Critic 多角色分工，复杂任务完成质量提升 30%+

**3. 缺乏上下文管理**
- 当前：`BaseAgent.messageList` 无限增长（最多 maxSteps×2 条），无截断保护
- 差距：生产级系统使用滑动窗口 + 摘要压缩，防止超出模型 context window

**4. 缺乏可观测性**
- 当前：仅有 LogAdvisor 简单日志
- 差距：完整的链路追踪（Trace）、Token 用量统计、每步耗时、工具调用成功率监控

---

## 3. 生产级 Agent 架构

### 3.1 架构总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            用户接入层 (Access Layer)                          │
│   Web UI  │  Mobile App  │  REST API  │  WebSocket  │  第三方平台(钉钉/企微)  │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────────────────┐
│                          网关 & 安全层 (Gateway)                               │
│    认证/授权(JWT/OAuth)  │  限流(Rate Limit)  │  多租户隔离  │  请求路由        │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────────────────┐
│                        任务调度层 (Task Orchestration)                         │
│                                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                    │
│  │ 任务队列      │   │  会话管理     │   │  任务状态机   │                    │
│  │ (MQ/Redis)   │   │ Session Mgr  │   │ PENDING/RUN/ │                    │
│  │ 异步解耦      │   │ 跨请求持久化  │   │ DONE/FAILED  │                    │
│  └──────────────┘   └──────────────┘   └──────────────┘                    │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────────────────┐
│                        Agent 执行层 (Agent Engine)                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    规划器 (Planner)                                   │   │
│  │   Task Decomposition → SubTask List → 依赖分析 → 执行顺序排列          │   │
│  └───────────────────────────┬─────────────────────────────────────────┘   │
│                              │ 子任务                                        │
│  ┌───────────────────────────▼─────────────────────────────────────────┐   │
│  │                  多 Agent 协作 (Multi-Agent)                          │   │
│  │                                                                      │   │
│  │  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │   │
│  │  │ Orchestrator │  │  Research   │  │   Coder     │  │  Critic  │ │   │
│  │  │    Agent     │→ │   Agent     │  │   Agent     │  │  Agent   │ │   │
│  │  │  (主控分发)   │  │  (信息收集)  │  │  (代码生成)  │  │  (评审)  │ │   │
│  │  └──────────────┘  └─────────────┘  └─────────────┘  └──────────┘ │   │
│  │                                                                      │   │
│  │              每个 Agent 内部：ReAct Loop（Think → Act）               │   │
│  └───────────────────────────┬─────────────────────────────────────────┘   │
└──────────────┬────────────────┼────────────────────────────┬────────────────┘
               │                │                            │
┌──────────────▼──┐  ┌──────────▼────────────────┐  ┌──────▼──────────────┐
│   工具层          │  │      记忆层 (Memory)        │  │   模型层 (LLM)      │
│  (Tool Layer)   │  │                            │  │                    │
│                 │  │  ┌─────────┐ ┌──────────┐  │  │  ┌──────────────┐ │
│  ・搜索工具      │  │  │ 短期记忆 │ │  长期记忆 │  │  │  │ 主力模型     │ │
│  ・代码执行      │  │  │(会话窗口)│ │ (向量DB) │  │  │  │(DashScope/  │ │
│  ・文件系统      │  │  └─────────┘ └──────────┘  │  │  │  GPT-4)     │ │
│  ・数据库查询    │  │  ┌─────────┐ ┌──────────┐  │  │  └──────────────┘ │
│  ・HTTP 请求    │  │  │ 情节记忆 │ │  工作记忆 │  │  │  ┌──────────────┐ │
│  ・邮件/日历    │  │  │(重要事件)│ │(任务上下文│  │  │  │ 轻量模型     │ │
│  ・MCP 工具     │  │  └─────────┘ └──────────┘  │  │  │(分类/路由)   │ │
└─────────────────┘  └────────────────────────────┘  │  └──────────────┘ │
                                                      └────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────────────────┐
│                        基础设施层 (Infrastructure)                             │
│                                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │  向量数据库  │  │  关系型 DB   │  │  缓存(Redis) │  │  对象存储    │   │
│  │(Redis/Milvus│  │ (任务/用户)  │  │  (会话/限流) │  │ (文件/PDF)   │   │
│  └─────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────────────────┐
│                        可观测性层 (Observability)                              │
│                                                                              │
│  链路追踪(Trace)  │  指标监控(Metrics)  │  日志聚合  │  告警  │  Cost 统计     │
│  每步耗时/Token量 │  成功率/错误率       │  ELK Stack │        │  每次调用费用  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 各模块详解

#### 用户接入层
多渠道统一接入。生产级必须支持 **WebSocket 长连接**推送 Agent 实时进度，而不只是单向 SSE。

#### 网关 & 安全层
- **认证授权**：JWT / OAuth2，每个用户的 Agent 会话严格隔离
- **限流**：防止单用户无限调用 LLM（成本控制），令牌桶算法
- **多租户**：SaaS 场景下每个租户独立的数据空间和配额

#### 任务调度层（当前项目最缺的一层）
- **异步任务队列**（Kafka / RabbitMQ）：Agent 任务异步化，HTTP 请求立即返回 `taskId`，前端通过 WebSocket 订阅进度
- **会话管理**：`sessionId → AgentState` 持久化到 Redis/DB，支持"暂停/恢复/取消"
- **任务状态机**：`PENDING → RUNNING → PAUSED → DONE / FAILED / TIMEOUT`

#### Agent 执行层
- **规划器（Planner）**：先分解任务为有序子任务列表（当前 AxinManus 缺少此步骤）
- **多 Agent 协作**：不同专长的 Agent 并行或串行协作
- **每个 Agent 内部**：ReAct / CoT / ToT 等推理策略（当前已实现 ReAct）

#### 记忆层

| 类型 | 存储介质 | 用途 | 当前项目 |
|---|---|---|---|
| 短期记忆 | 内存 / Redis | 当前会话窗口，20-50 条 | ✅ MessageWindowChatMemory |
| 长期记忆 | 向量 DB | 用户偏好、历史事件语义检索 | ⚠️ 仅 LoveApp 的 RAG |
| 工作记忆 | Agent 内部 | 当前任务上下文、中间结果 | ✅ messageList |
| 情节记忆 | 关系型 DB | 重要事件结构化存储 | ❌ 未实现 |

#### 工具层
生产级最低要求：搜索、**代码沙箱执行**、**文件系统操作**、HTTP 请求、**数据库查询**、浏览器自动化（Playwright）

#### 可观测性层（生产必备）
- **Trace 链路**：每次 Agent 运行的完整调用链（类似 LangSmith），记录每步 Think/Act 耗时
- **Token Cost 统计**：每次任务的模型调用费用，支持按用户/租户汇总
- **性能指标**：P95 响应时间、工具调用成功率、Agent 任务完成率

---

## 4. 项目现状与生产级标准对照

| 模块 | 当前项目 | 生产级标准 | 差距评级 |
|---|---|---|---|
| 用户接入 | REST + SSE ✅ | REST + SSE + WebSocket | 🟡 中 |
| 网关安全 | CORS ✅ | 认证/限流/多租户 | 🔴 大 |
| **任务调度** | 同步阻塞 ❌ | 异步队列 + 会话持久化 | 🔴 **最大短板** |
| **规划器** | 无，直接 ReAct ❌ | Task Decomposition | 🔴 大 |
| **多 Agent** | 单 Agent ❌ | 多角色协作 | 🔴 大 |
| ReAct 引擎 | 完整实现 ✅ | ReAct / CoT / ToT | 🟢 小 |
| 工具层 | 4 个工具 ✅ | 10+ 工具 | 🟡 中 |
| 短期记忆 | MessageWindow ✅ | 滑动窗口 + 摘要压缩 | 🟡 中 |
| 长期记忆 | RAG ⚠️（仅 LoveApp） | 全局向量化长期记忆 | 🟡 中 |
| 文件持久化记忆 | 实现未启用 ⚠️ | 完整接入 | 🟢 小 |
| **可观测性** | LogAdvisor ⚠️ | Trace/Cost/Metrics | 🔴 大 |
| 基础设施 | Redis ✅ | Redis + DB + 对象存储 | 🟡 中 |
| 安全配置 | API Key 明文 ❌ | 环境变量 / 配置中心 | 🔴 高风险 |

---

## 5. 未来规划路线

### 5.1 短期（修 Bug + 基础完善）

优先级最高，直接影响可用性：

- [ ] **[P0]** 修复 `pom.xml` 中 `font-asian` 的 `scope=test` 问题，确保中文 PDF 生产可用
- [ ] **[P0]** `WebSearchTool` 增加 `subList` 边界检查，避免结果不足时崩溃
- [ ] **[P1]** `WebScrapingTool` 改为返回 `doc.text()` 并增加 `maxLength=3000` 截断
- [ ] **[P1]** `LoveApp` 启用 `FileBasedChatMemory` 替代 `InMemoryChatMemoryRepository`
- [ ] **[P1]** `BaseAgent.messageList` 增加滑动窗口截断（参考 `demo/MessageTrimmingHook`）
- [ ] **[P2]** API Key 改用环境变量 `${DASHSCOPE_API_KEY}` 和 `${SEARCH_API_KEY}`

### 5.2 中期（Agent 能力提升）

提升复杂任务处理能力：

- [ ] **引入 Planning 层**
  - 在 `AxinManus.think()` 前增加任务分解步骤
  - 使用结构化输出（已有 `jsonschema-generator` 依赖）将大任务拆分为子任务列表
  - 按顺序/优先级执行子任务，支持依赖关系

- [ ] **Agent 持久化**
  - 将 `messageList` 和 `AgentState` 存入 Redis（`agentSession:{sessionId}`）
  - 支持 `sessionId` 恢复，实现跨请求的"长任务"
  - Controller 层改为先查询 session，有则恢复，无则新建

- [ ] **多 Agent 协作骨架**
  - `OrchestratorAgent`：负责任务分发和结果汇总
  - `ResearchAgent`：专注搜索信息收集
  - `WriterAgent`：专注内容生成和输出

- [ ] **工具扩展**
  - 代码执行工具（沙箱环境，如 Docker 隔离）
  - 文件系统工具（读/写/列出本地文件）
  - HTTP 请求工具（调用外部 REST API）

- [ ] **可观测性基础建设**
  - 自定义 Advisor 统计每次 LLM 调用的 token 用量和耗时
  - 将 Agent 每步 Think/Act 的日志结构化，写入 DB 供查询

### 5.3 长期（产品化方向）

向真正的生产级 Agent 平台演进：

- [ ] **可视化执行流程**：类似 LangSmith / Dify 的 Agent 执行 Trace 可视化界面
- [ ] **Prompt 管理系统**：System Prompt 版本化管理，支持 A/B 测试
- [ ] **多模型支持**：在 DashScope 基础上增加 OpenAI / 本地 Ollama 切换，按任务类型路由
- [ ] **用户认证 + 多租户**：每个用户独立的 Agent 会话和记忆空间，支持 SaaS 部署
- [ ] **Agent 插件市场**：工具以插件形式动态注册，无需重启服务
- [ ] **异步任务队列**：引入 Redis Stream 或 RabbitMQ，Agent 任务完全异步化

---

## 附录：关键文件索引

| 文件 | 作用 |
|---|---|
| `agent/BaseAgent.java` | Agent 状态机基类，维护 messageList 和执行循环 |
| `agent/ReActAgent.java` | ReAct 模板方法，定义 think/act 抽象 |
| `agent/ToolCallAgent.java` | 工具调用实现，手动执行 ToolCallingManager |
| `agent/AxinManus.java` | 最终 Agent 实体，注册所有工具，最多 20 步 |
| `app/LoveApp.java` | 恋爱顾问应用，集成 Memory + RAG + Tools |
| `tool/WebSearchTool.java` | 网页搜索（⚠️ 有越界 Bug） |
| `tool/WebScrapingTool.java` | 网页抓取（⚠️ 返回 HTML 耗 token） |
| `tool/PDFGenerationTool.java` | PDF 生成（⚠️ 中文字体 scope 错误） |
| `chatmemory/FileBasedChatMemory.java` | Kryo 文件持久化记忆（⚠️ 未启用） |
| `advisor/Re2Advisor.java` | Re2 提示增强 |
| `rag/RagStrategyContext.java` | RAG 策略选择器（LOCAL/CLOUD） |
| `pom.xml` | 依赖管理（⚠️ font-asian scope=test） |
