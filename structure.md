# axin-agent 项目结构文档

> 更新时间：2026-04-28

---

## 一、项目概览

| 属性 | 值 |
|---|---|
| 项目名 | axin-agent |
| GroupId | com.axin |
| Spring Boot | 3.5.12 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 1.1.2.0（DashScope） |
| Java 版本 | 21 |
| 服务端口 | 8123 |
| Context Path | /api |

**定位**：基于 Spring AI 构建的多能力 AI Agent 平台，支持单 Agent / 多 Agent 协作两种执行模式，集成 RAG、记忆、可观测性、异步任务队列、MCP 工具扩展等能力。

---

## 二、整体架构分层

```
┌─────────────────────────────────────────────────────────────────┐
│                         Controller 层                            │
│         AiController / ObservabilityController / HealthController│
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                          Task 层（异步任务）                       │
│      AgentTaskService ──RocketMQ──► AgentTaskConsumer           │
│                    ProgressSseService（SSE 推送）                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                          Agent 执行层                             │
│  BaseAgent → ReActAgent → ToolCallAgent → AxinManus（单 Agent）  │
│  OrchestratorAgent（多 Agent 主控）                               │
│    ├── PlannerAgent（任务拆解规划）                                │
│    ├── ResearchAgent（信息收集）                                  │
│    └── WriterAgent（内容生成）                                    │
└──────────┬──────────────────────────┬───────────────────────────┘
           │                          │
┌──────────▼──────┐        ┌──────────▼──────────────────────────┐
│    LLM 模型路由层 │        │            工具层（Tool）              │
│  ModelRouter    │        │  WebSearch / WebScraping / Http      │
│  PRIMARY/LIGHT  │        │  FileSystem / CodeExecution / PDF    │
└─────────────────┘        │  EpisodicMemory / Terminate         │
                           └─────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────────────────┐
│                        Advisor 拦截增强层                          │
│  LogAdvisor / Re2Advisor / TraceTokenAdvisor                    │
│  LoveAppRagLocalAdvisor / LoveAppRagCloudAdvisor               │
└──────────┬──────────────────────────┬───────────────────────────┘
           │                          │
┌──────────▼──────┐        ┌──────────▼──────────────────────────┐
│    记忆层（Memory）│        │           RAG 检索层                  │
│  FileBasedChat  │        │  RagStrategyContext                  │
│  Memory（Kryo）  │        │    ├── LocalRag（Redis 向量）          │
│  EpisodicMemory │        │    └── CloudRag（DashScope 知识库）    │
│  LongTermMemory │        │  RagDataInitializer（启动时写入）       │
└─────────────────┘        └─────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────────────────┐
│                      Infrastructure 基础设施层                     │
│  MySQL（AgentTask 持久化）/ Redis（向量存储）                       │
│  LocalStorageService（文件存储）                                   │
└─────────────────────────────────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────────────────┐
│                        可观测性层（Observability）                  │
│  AgentTraceService / MetricsService / CostCalculator           │
│  TaskContext（ThreadLocal taskId）                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、完整包结构与类说明

### 3.1 入口

```
com.axin.axinagent/
└── AxinAgentApplication.java        启动类，@SpringBootApplication
```

---

### 3.2 agent — Agent 执行层

Agent 采用**模板方法模式**分层继承：`BaseAgent → ReActAgent → ToolCallAgent → AxinManus`。

| 类 | 职责 |
|---|---|
| `BaseAgent` | 抽象基类，定义 `run()` 主循环、状态机（IDLE/RUNNING/FINISHED/ERROR）、最大步数限制，强制子类实现 `step()` |
| `ReActAgent` | 抽象中间层，声明 `think()` 和 `act()` 两个抽象方法，将 ReAct 范式注入继承链 |
| `ToolCallAgent` | 核心实现：持有 `ChatClient`，实现 think（调 LLM）→ act（执行工具调用）循环，集成 Trace、Token 统计 |
| `AxinManus` | 最终单 Agent：注册所有工具（Web/文件/代码/记忆/终止），作为 Spring Bean 供外部直接调用 |
| `PlannerAgent` | 任务规划器：调用轻量模型将复杂任务拆解为有序子任务列表（`TaskPlan`），供 Orchestrator 使用 |
| `OrchestratorAgent` | 多 Agent 主控：先调 PlannerAgent 拆解，再按子任务角色分发给 ResearchAgent / WriterAgent 并行执行，最后聚合结果 |
| `ResearchAgent` | 专注信息收集的子 Agent，持有搜索工具 |
| `WriterAgent` | 专注内容生成的子 Agent，持有文件写入工具 |

**agent/model — Agent 模型**

| 类 | 职责 |
|---|---|
| `AgentState` | 枚举：`IDLE / RUNNING / FINISHED / ERROR / CANCELLED` |
| `AgentRole` | 枚举：`RESEARCH / WRITE / CODE / GENERAL`，标识子 Agent 角色 |
| `SubTask` | 子任务：包含 taskId、description、role、status、result |
| `TaskPlan` | 任务计划：goal + List\<SubTask\> |

---

### 3.3 advisor — Advisor 拦截增强层

Spring AI `Advisor` 链，按 `getOrder()` 顺序执行。

| 类 | order | 职责 |
|---|---|---|
| `LogAdvisor` | 0 | 打印用户输入 / AI 响应日志（同步 + 流式均支持） |
| `Re2Advisor` | 1 | 应用 Re-Reading (Re2) 提示工程：将用户输入重写为「原问题 + Read the question again: 原问题」，提升推理准确性 |
| `TraceTokenAdvisor` | 1 | 记录每次 LLM 调用的耗时、Token 用量（prompt/completion/total），写入 AgentTraceService |

---

### 3.4 app — 业务应用层

| 类 | 职责 |
|---|---|
| `LoveApp` | 恋爱顾问应用：基于 `ChatClient` + `MessageWindowChatMemory`，支持同步对话、流式对话、结构化输出（LoveReport）；可按策略切换本地/云端 RAG |

**app/model**

| 类 | 职责 |
|---|---|
| `LoveReport` | `LoveApp` 的结构化输出模型：title + suggestions 列表 |

---

### 3.5 tool — 工具层

所有工具通过 `ToolRegistration` 统一注册，供 AxinManus / 各子 Agent 使用。

| 类 | 工具方法 | 职责 |
|---|---|---|
| `WebSearchTool` | `webSearch(query)` | 调用 DashScope 搜索服务进行网页检索 |
| `WebScrapingTool` | `scrapeWebPage(url)` | 抓取指定 URL 的网页正文内容 |
| `HttpRequestTool` | `httpGet / httpPost` | 发送自定义 HTTP 请求（15s 超时，5000 字截断） |
| `FileSystemTool` | `readFile / writeFile / listDirectory` | 在 `./workspace` 沙箱目录中读写文件，防路径穿越 |
| `CodeExecutionTool` | `executeCode(lang, code)` | 在沙箱中执行 Python/JavaScript 代码片段，返回 stdout/stderr |
| `PDFGenerationTool` | `generatePDF(filename, content)` | 将 Markdown 文本渲染为 PDF，通过 StorageService 落盘 |
| `EpisodicMemoryTool` | `saveEpisodicMemory / searchEpisodicMemory` | 调用 EpisodicMemoryService 保存/检索情节记忆 |
| `TerminateTool` | `terminate(message)` | Agent 主动声明任务完成，中断 ReAct 循环 |
| `ToolRegistration` | `toolCallbacks()` | 统一将上述工具实例化并注册为 Spring `ToolCallbackProvider` Bean |

---

### 3.6 memory — 记忆层

| 类 | 职责 |
|---|---|
| `MemoryFacade` | 记忆统一门面：聚合短期对话记忆、情节记忆、长期向量记忆，提供一致的读写接口 |
| `LongTermMemoryService` | 长期记忆：基于 Redis VectorStore 进行语义相似度检索 |
| `EpisodicMemoryService` | 情节记忆：将重要事件（eventType/summary/keywords/importance）持久化到 MySQL |
| `EpisodicMemoryRepository` | Spring Data JPA Repository，支持按用户/关键词/重要性查询情节记忆 |
| `EpisodicMemoryEvent` | 情节记忆 JPA 实体 |

---

### 3.7 chatmemory — 对话记忆

| 类 | 职责 |
|---|---|
| `FileBasedChatMemory` | 基于 Kryo 序列化的对话历史持久化实现，每个 conversationId 对应 `./chat-memory/{id}.kryo` 文件，ThreadLocal 保证线程安全 |

---

### 3.8 rag — RAG 检索层

| 类 | 职责 |
|---|---|
| `RagStrategy` | 枚举：`LOCAL`（Redis Stack）/ `CLOUD`（DashScope） |
| `RagAdvisorStrategy` | 策略接口：`getStrategy()` + `getAdvisor()` |
| `LocalRagAdvisorStrategy` | 本地策略实现，注入 `loveAppRagLocalAdvisor` Bean |
| `CloudRagAdvisorStrategy` | 云端策略实现，注入 `loveAppRagCloudAdvisor` Bean |
| `RagStrategyContext` | 策略上下文分发器：收集所有策略实现，按枚举分发对应 Advisor |
| `RagDataInitializer` | 启动时将 `classpath:document/*.md` 按标题切分并写入 Redis 向量存储；已有数据时自动跳过（幂等），可通过 `axin.rag.init-on-startup=false` 关闭 |

**RAG 知识库文档**（`resources/document/`）：
- 恋爱常见问题和回答 - 单身篇.md
- 恋爱常见问题和回答 - 恋爱篇.md
- 恋爱常见问题和回答 - 已婚篇.md

---

### 3.9 llm — 模型路由层

| 类 | 职责 |
|---|---|
| `ModelType` | 枚举：`PRIMARY`（复杂推理）/ `LIGHT`（规划分类） |
| `ModelRouter` | 按 ModelType 返回对应 `ChatModel` Bean（primaryChatModel / lightChatModel） |

---

### 3.10 task — 异步任务层

| 类 | 职责 |
|---|---|
| `AgentTaskService` | 任务管理核心：提交任务（写 MySQL + 发 RocketMQ）、查询状态、取消任务、分页列表 |
| `AgentTaskConsumer` | RocketMQ 消费者：监听 `axin-agent-topic`，根据 mode 分发给 AxinManus（SINGLE）或 OrchestratorAgent（MULTI），更新任务状态 |
| `ProgressSseService` | SSE 进度推送：维护 taskId → SseEmitter 映射，支持实时进度通知和结果推送 |

**task/model**

| 类 | 职责 |
|---|---|
| `AgentTaskMessage` | MQ 消息体：taskId、message、createTime、mode（SINGLE/MULTI） |
| `AgentTaskStatus` | 任务状态查询 VO：taskId、state、message、result、errorMessage、createTime、updateTime |
| `TaskSubmitResponse` | 提交响应 VO：taskId |

---

### 3.11 observability — 可观测性层

| 类 | 职责 |
|---|---|
| `TaskContext` | ThreadLocal 容器，传递当前线程的 taskId，供 Advisor 在任何位置获取上下文 |
| `AgentTraceService` | Trace 写入/读取：记录 Agent 步骤（TraceStepEntry）、LLM 调用记录（LlmCallRecord）、延迟分布，支持按 taskId 查询完整 Trace |
| `MetricsService` | 全局指标统计：任务总数/完成数/失败数/取消数、工具调用成功率、P95 延迟 |
| `CostCalculator` | Token 费用估算：按模型单价将 Token 用量换算为人民币 |

**observability/model**

| 类 | 字段说明 |
|---|---|
| `TraceStepEntry` | step/type/toolName/startTime/endTime/durationMs/success/summary/token信息 |
| `LlmCallRecord` | taskId/callTime/durationMs/promptTokens/completionTokens/totalTokens/model |
| `MetricsResult` | total/finished/error/cancelled/completionRate/toolMetrics/p95LatencyMs |
| `ToolMetrics` | toolName/total/success/fail/successRate |
| `TaskTraceResult` | taskId/steps/llmCalls/totalDurationMs/totalTokens/estimatedCostYuan |

---

### 3.12 infrastructure — 基础设施层

```
infrastructure/
├── db/
│   ├── entity/AgentTaskEntity.java    MyBatis-Plus 实体，对应 agent_task 表
│   └── mapper/AgentTaskMapper.java    MyBatis-Plus Mapper
└── storage/
    ├── StorageService.java            对象存储抽象接口（saveFile / getFilePath）
    └── LocalStorageServiceImpl.java   本地文件系统实现，默认存储目录 ./uploads
```

**数据库表**（`resources/db/schema.sql`）：

```sql
agent_task (
  task_id    VARCHAR(64) PK,
  state      VARCHAR(32),    -- PENDING/RUNNING/FINISHED/ERROR/CANCELLED
  message    TEXT,           -- 用户输入
  mode       VARCHAR(16),    -- SINGLE/MULTI
  error_message TEXT,
  result     LONGTEXT,
  create_time DATETIME,
  update_time DATETIME
)
```

---

### 3.13 config — 配置层

| 类 | 职责 |
|---|---|
| `CorsConfig` | 全局跨域配置，允许所有域名/方法/请求头 |
| `MemoryConfig` | 注册 `FileBasedChatMemory` + `MessageWindowChatMemory`（最大20条） |
| `ModelConfig` | 基于 DashScope 克隆出 `primaryChatModel` / `lightChatModel` 两个 Bean |
| `ModelProperties` | `@ConfigurationProperties(prefix="axin.model")`，绑定 primary/light 模型参数 |
| `MyBatisPlusConfig` | 开启 MyBatis-Plus 分页插件，扫描 Mapper 包 |
| `ObservabilityConfig` | 注册 ObservabilityConfig 相关配置 |
| `RocketMqConfig` | 配置 RocketMQ Producer/Consumer |
| `RagLocalAdvisorConfig` | 构建基于 Redis Stack 向量检索的 RAG Advisor Bean（Top-K=4）|
| `RagCloudAdvisorConfig` | 构建基于 DashScope 云端知识库的 RAG Advisor Bean |

---

### 3.14 controller — 接口层

| 类 | 路径 | 职责 |
|---|---|---|
| `AiController` | `/ai/**` | 恋爱顾问对话、Agent 任务提交/查询/取消/分页列表、SSE 进度订阅 |
| `ObservabilityController` | `/observability/**` | 任务 Trace 查询、全局指标、工具指标、SSE 实时监控 |
| `HealthController` | `/health` | 健康检查 |

---

### 3.15 common — 通用工具

| 类 | 职责 |
|---|---|
| `BaseResponse<T>` | 统一响应体：code / data / message |
| `ResultUtils` | 构造成功/失败响应的工厂方法 |
| `PageRequest` | 分页请求基类 |
| `DeleteRequest` | 删除请求基类 |

---

### 3.16 exception — 异常处理

| 类 | 职责 |
|---|---|
| `ErrorCode` | 错误码枚举（如 PARAMS_ERROR、NOT_LOGIN_ERROR 等） |
| `BusinessException` | 业务异常，携带 ErrorCode |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，统一捕获并返回 BaseResponse |
| `ThrowUtils` | 条件抛出工具方法 |

---

### 3.17 demo — 学习示例（已移至 test 目录）

学习示例代码已全部迁移至 `src/test/java/` 对应包，不再打入生产 jar：

```
src/test/java/com/axin/axinagent/demo/
├── SimpleDashScopeTest.java            DashScope 直接调用示例
└── learning/
    ├── ReactLearning.java              ReAct 模式学习示例
    ├── ReactLearningTest.java          ReactLearning 测试入口
    ├── GuardrailInterceptor.java       输入安全拦截示例
    ├── LoggingHook.java                日志 Hook 示例
    ├── MessageTrimmingHook.java        消息裁剪 Hook 示例
    ├── SearchTool.java                 简单搜索工具示例
    └── ToolErrorInterceptor.java       工具错误拦截示例
```

---

## 四、子模块：axin-agent-image-mcp-server

独立的 **MCP (Model Context Protocol) 服务器**子模块，通过 stdio/SSE 两种传输方式为主模块提供图像搜索能力。

```
axin-agent-image-mcp-server/
└── src/main/java/.../
    ├── AxinAgentImageMcpServerApplication.java   启动类（支持 stdio/SSE 双模式）
    └── mcptool/
        └── ImageSearchTool.java                  图像搜索 MCP 工具
```

**主模块配置**（`resources/mcp-servers.json`）注册了两个 MCP Server：
- `amap-maps`：高德地图，通过 npx 启动
- `axin-agent-image-mcp-server`：本地图像搜索，通过 java -jar 启动

---

## 五、关键依赖

| 依赖 | 用途 |
|---|---|
| `spring-ai-starter-model-dashscope` | 阿里 DashScope 大模型（qwen-max / qwen-turbo）|
| `spring-ai-redis-store-spring-boot-starter` | Redis Stack 向量存储（RAG + 长期记忆）|
| `spring-ai-markdown-document-reader` | Markdown 文档按标题切分 |
| `rocketmq-spring-boot-starter` | 异步任务队列 |
| `mybatis-plus-spring-boot3-starter` | Agent 任务持久化（MySQL）|
| `spring-data-jpa` | 情节记忆持久化（EpisodicMemoryEvent）|
| `kryo` | 对话记忆文件序列化 |
| `itext7-core` | PDF 生成 |
| `hutool-all` | 文件操作、HTTP 客户端 |

---

## 六、配置文件说明

**`application.yml` 关键配置**：

```yaml
server:
  port: 8123
  servlet.context-path: /api

spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}  # 通过环境变量注入
    mcp:
      client.stdio.servers-configuration: classpath:mcp-servers.json

axin:
  model:
    primary:
      model-name: qwen-max     # 主力模型（复杂推理）
      temperature: 0.7
    light:
      model-name: qwen-turbo   # 轻量模型（规划/路由）
      temperature: 0.3
```

---

## 七、核心数据流

### 7.1 单 Agent 异步任务流程

```
Client
  │ POST /ai/agent/submit
  ▼
AiController
  │ AgentTaskService.submit()
  ▼
MySQL(state=PENDING) + RocketMQ
  │ AgentTaskConsumer.onMessage()
  ▼
TaskContext.setTaskId() → AxinManus.run()
  │ ReAct 循环：think(LLM) → act(工具) → 直到 terminate
  ▼
TaskContext.clear() → AgentTaskService.finish()
  │ MySQL(state=FINISHED, result=...)
  ▼
ProgressSseService.sendResult()
  │ SSE 推送给订阅的 Client

Client GET /ai/agent/progress/{taskId}  （SSE）
```

### 7.2 多 Agent 协作流程

```
OrchestratorAgent.run()
  │ PlannerAgent.plan()  → TaskPlan（SubTask列表）
  ▼
按 AgentRole 分发：
  ├── RESEARCH → ResearchAgent（并行）
  └── WRITE    → WriterAgent（顺序依赖研究结果）
  ▼
聚合所有 SubTask.result → 最终答案
```

### 7.3 RAG 对话流程（LoveApp）

```
用户提问
  ▼
ChatClient.prompt()
  │ Advisor 链：LogAdvisor → Re2Advisor → RagAdvisor（LOCAL/CLOUD）
  ▼
RagAdvisor 检索相似文档（Redis 或 DashScope）
  ▼
文档注入 Prompt Context → 调用 LLM → 生成回答
```

---

## 八、目录树

```
axin-agent/
├── axin-agent-image-mcp-server/         MCP 图像服务子模块
│   └── src/main/java/.../mcptool/
│       └── ImageSearchTool.java
├── src/
│   ├── main/
│   │   ├── java/com/axin/axinagent/
│   │   │   ├── AxinAgentApplication.java
│   │   │   ├── agent/                   Agent 执行层
│   │   │   │   ├── BaseAgent.java
│   │   │   │   ├── ReActAgent.java
│   │   │   │   ├── ToolCallAgent.java
│   │   │   │   ├── AxinManus.java
│   │   │   │   ├── PlannerAgent.java
│   │   │   │   ├── OrchestratorAgent.java
│   │   │   │   ├── ResearchAgent.java
│   │   │   │   ├── WriterAgent.java
│   │   │   │   └── model/
│   │   │   │       ├── AgentState.java
│   │   │   │       ├── AgentRole.java
│   │   │   │       ├── SubTask.java
│   │   │   │       └── TaskPlan.java
│   │   │   ├── advisor/                 Advisor 拦截增强层
│   │   │   │   ├── LogAdvisor.java
│   │   │   │   ├── Re2Advisor.java
│   │   │   │   └── TraceTokenAdvisor.java
│   │   │   ├── app/                     业务应用层
│   │   │   │   ├── LoveApp.java
│   │   │   │   └── model/
│   │   │   │       └── LoveReport.java  LoveApp 结构化输出模型
│   │   │   ├── tool/                    工具层
│   │   │   │   ├── ToolRegistration.java
│   │   │   │   ├── WebSearchTool.java
│   │   │   │   ├── WebScrapingTool.java
│   │   │   │   ├── HttpRequestTool.java
│   │   │   │   ├── FileSystemTool.java
│   │   │   │   ├── CodeExecutionTool.java
│   │   │   │   ├── PDFGenerationTool.java
│   │   │   │   ├── EpisodicMemoryTool.java
│   │   │   │   └── TerminateTool.java
│   │   │   ├── memory/                  记忆层
│   │   │   │   ├── MemoryFacade.java
│   │   │   │   ├── LongTermMemoryService.java
│   │   │   │   ├── EpisodicMemoryService.java
│   │   │   │   ├── EpisodicMemoryRepository.java
│   │   │   │   └── EpisodicMemoryEvent.java
│   │   │   ├── chatmemory/              对话记忆
│   │   │   │   └── FileBasedChatMemory.java
│   │   │   ├── rag/                     RAG 检索层
│   │   │   │   ├── RagStrategy.java
│   │   │   │   ├── RagAdvisorStrategy.java
│   │   │   │   ├── LocalRagAdvisorStrategy.java
│   │   │   │   ├── CloudRagAdvisorStrategy.java
│   │   │   │   ├── RagStrategyContext.java
│   │   │   │   └── RagDataInitializer.java
│   │   │   ├── llm/                     模型路由层
│   │   │   │   ├── ModelType.java
│   │   │   │   └── ModelRouter.java
│   │   │   ├── task/                    异步任务层
│   │   │   │   ├── AgentTaskService.java
│   │   │   │   ├── AgentTaskConsumer.java
│   │   │   │   ├── ProgressSseService.java
│   │   │   │   └── model/
│   │   │   │       ├── AgentTaskMessage.java
│   │   │   │       ├── AgentTaskStatus.java
│   │   │   │       └── TaskSubmitResponse.java
│   │   │   ├── observability/           可观测性层
│   │   │   │   ├── TaskContext.java
│   │   │   │   ├── AgentTraceService.java
│   │   │   │   ├── MetricsService.java
│   │   │   │   ├── CostCalculator.java
│   │   │   │   └── model/
│   │   │   │       ├── TraceStepEntry.java
│   │   │   │       ├── LlmCallRecord.java
│   │   │   │       ├── MetricsResult.java
│   │   │   │       ├── ToolMetrics.java
│   │   │   │       └── TaskTraceResult.java
│   │   │   ├── infrastructure/          基础设施层
│   │   │   │   ├── db/
│   │   │   │   │   ├── entity/AgentTaskEntity.java
│   │   │   │   │   └── mapper/AgentTaskMapper.java
│   │   │   │   └── storage/
│   │   │   │       ├── StorageService.java
│   │   │   │       └── LocalStorageServiceImpl.java
│   │   │   ├── config/                  配置层
│   │   │   │   ├── CorsConfig.java
│   │   │   │   ├── MemoryConfig.java
│   │   │   │   ├── ModelConfig.java
│   │   │   │   ├── ModelProperties.java
│   │   │   │   ├── MyBatisPlusConfig.java
│   │   │   │   ├── ObservabilityConfig.java
│   │   │   │   ├── RocketMqConfig.java
│   │   │   │   ├── RagLocalAdvisorConfig.java
│   │   │   │   └── RagCloudAdvisorConfig.java
│   │   │   ├── controller/              接口层
│   │   │   │   ├── AiController.java
│   │   │   │   ├── ObservabilityController.java
│   │   │   │   └── HealthController.java
│   │   │   ├── common/                  通用工具
│   │   │   │   ├── BaseResponse.java
│   │   │   │   ├── ResultUtils.java
│   │   │   │   ├── PageRequest.java
│   │   │   │   └── DeleteRequest.java
│   │   │   ├── exception/               异常处理
│   │   │   │   ├── ErrorCode.java
│   │   │   │   ├── BusinessException.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   └── ThrowUtils.java
│   │   │   ├── model/                   业务模型（已清空，LoveReport 已移至 app/model）
│   │   │   └── demo/                    已清空（学习示例已迁移至 test 目录）
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── mcp-servers.json         MCP Server 注册配置
│   │       ├── db/schema.sql            建表 SQL
│   │       └── document/               RAG 知识库文档
│   │           ├── 恋爱常见问题和回答 - 单身篇.md
│   │           ├── 恋爱常见问题和回答 - 恋爱篇.md
│   │           └── 恋爱常见问题和回答 - 已婚篇.md
│   └── test/
│       └── java/com/axin/axinagent/
│           ├── AxinAgentApplicationTests.java
│           ├── app/LoveAppTest.java
│           └── demo/                        学习示例（从 main 迁移而来，不打包）
│               ├── SimpleDashScopeTest.java
│               └── learning/
│                   ├── ReactLearning.java
│                   ├── ReactLearningTest.java
│                   ├── GuardrailInterceptor.java
│                   ├── LoggingHook.java
│                   ├── MessageTrimmingHook.java
│                   ├── SearchTool.java
│                   └── ToolErrorInterceptor.java
├── pom.xml
├── future.md                            功能规划文档
└── structure.md                         本文档
```

---

## 九、已实施的优化

### 9.1 `demo` 包已迁移至 `test` 目录 ✅

`demo/` 下所有学习示例（`SimpleDashScopeTest`、`learning/*`）已全部迁移至 `src/test/java/` 对应包，不再打入生产 jar。

### 9.2 `model/LoveReport.java` 已移至 `app/model/` ✅

`LoveReport` 与 `LoveApp` 的包内聚性更强，顶层 `model` 包已清空。

### 9.3 RAG Advisor 配置类已迁移至 `config/` 包 ✅

- `advisor/LoveAppRagLocalAdvisor` → `config/RagLocalAdvisorConfig`
- `advisor/LoveAppRagCloudAdvisor` → `config/RagCloudAdvisorConfig`

配置类与真正的 Advisor 实现（`LogAdvisor`、`Re2Advisor`）已明确分离。

### 9.4 RAG 数据初始化已实现幂等控制 ✅

`RagDataInitializer` 启动时先做相似度查询，向量库已有数据则自动跳过，避免重复写入。同时支持通过配置彻底关闭初始化：

```yaml
axin:
  rag:
    init-on-startup: false  # 设为 false 可完全跳过启动初始化
```
