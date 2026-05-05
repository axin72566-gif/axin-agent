# axin-agent 架构设计文档 v1

> 更新时间：2026-05-04
> 版本：0.0.1-SNAPSHOT（精简重构后）

---

## 一、项目定位

**通用 AI Agent** — 类似豆包。用户发送消息，Agent 自主推理、调用工具、返回结果。

| 属性 | 值 |
|---|---|
| Spring Boot | 3.5.12 |
| Spring AI | 1.1.2（BOM 管理版本） |
| LLM 引擎 | 阿里 DashScope（qwen-max / qwen-turbo） |
| Java | 21 |
| 端口 | 8123，context-path=/api |
| API 文档 | /api/swagger-ui.html（Knife4j） |

---

## 二、分层架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│                        Controller 层                              │
│                                                                   │
│   AiController                                                   │
│   ├── GET /ai/chat/sync   同步聊天（ChatClient + 工具）           │
│   ├── GET /ai/chat/sse    SSE 流式聊天（Flux）                    │
│   └── GET /ai/agent/sse   Agent ReAct 自主推理（SseEmitter）      │
└──────────────────────────────┬───────────────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
┌─────────────┴──────┐  ┌─────┴──────┐  ┌──────┴──────────────┐
│   ChatClient 快捷模式 │  │ Agent 执行层 │  │    LLM 模型路由层   │
│                     │  │             │  │                    │
│   同步/流式调用      │  │ BaseAgent   │  │  ModelRouter       │
│   + Advisor 链      │  │   ↓         │  │  ├── PRIMARY       │
│   + 工具自动调用     │  │ ReActAgent  │  │  │   qwen-max      │
│                     │  │   ↓         │  │  └── LIGHT         │
│                     │  │ ToolCallAgent│  │      qwen-turbo   │
│                     │  │   ↓         │  │                    │
│                     │  │ AxinManus   │  │                    │
└──────────┬──────────┘  └──────┬──────┘  └────────────────────┘
           │                    │
           └────────┬───────────┘
                    ▼
┌──────────────────────────────────────────────────────────────────┐
│                         工具层 (Tool)                              │
│                                                                   │
│  WebSearch  WebScraping  PDF   FileSystem  HttpRequest  CodeExec │
│  (百度搜索)  (网页抓取)  (PDF)  (文件读写)   (HTTP请求)  (代码执行)│
│                                                                   │
│                         TerminateTool                             │
│                         (终止信号)                                 │
└──────────────────────────────┬───────────────────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
┌─────────┴──────┐  ┌─────────┴──────┐  ┌─────────┴──────────────┐
│  SearchAPI.io  │  │   Jsoup/HTTP   │  │   本地文件系统           │
│  (外部搜索API)  │  │  (外部HTTP调用) │  │   ./tmp/  ./workspace/  │
└────────────────┘  └────────────────┘  └────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                      拦截增强层 (Advisor)                          │
│                                                                   │
│  LogAdvisor          — 打印每次 LLM 调用的输入输出                 │
│  MessageChatMemory   — 自动管理对话历史窗口（最近 20 条）          │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                      基础设施层 (Infrastructure)                   │
│                                                                   │
│  StorageService → LocalStorageServiceImpl                        │
│  └── 本地文件存储：./tmp/pdf/、./tmp/code/                       │
│      路径穿越防护 + 文件名消毒                                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 三、API 数据流

### 3.1 快捷聊天模式（`/ai/chat/sync` 和 `/ai/chat/sse`）

适用场景：简单对话、带工具调用的单轮问答。Spring AI 框架自动处理工具调用。

```
Client                    AiController              ChatClient + Advisor 链          DashScope LLM
  │                            │                           │                              │
  │  GET /ai/chat/sync         │                           │                              │
  │  ?message=xxx&chatId=xxx   │                           │                              │
  │───────────────────────────►│                           │                              │
  │                            │                           │                              │
  │                            │  1. 构建 ChatMemory       │                              │
  │                            │     (InMemory, 20条)      │                              │
  │                            │                           │                              │
  │                            │  2. 构建 ChatClient       │                              │
  │                            │     + MessageChatMemory   │                              │
  │                            │     + LogAdvisor          │                              │
  │                            │     + allTools            │                              │
  │                            │──────────────────────────►│                              │
  │                            │                           │                              │
  │                            │                           │  3. 注入对话历史 + 工具定义   │
  │                            │                           │─────────────────────────────►│
  │                            │                           │                              │
  │                            │                           │  4. LLM 决定是否调用工具      │
  │                            │                           │◄─────────────────────────────│
  │                            │                           │                              │
  │                            │                           │  5. 如需工具 → 自动执行       │
  │                            │                           │     结果注入 → 再次调 LLM     │
  │                            │                           │─────────────────────────────►│
  │                            │                           │◄─────────────────────────────│
  │                            │                           │                              │
  │                            │  6. 保存本轮对话到 Memory  │                              │
  │                            │◄──────────────────────────│                              │
  │                            │                           │                              │
  │  7. 返回结果               │                           │                              │
  │  （sync=String             │                           │                              │
  │   sse=Flux<String>）       │                           │                              │
  │◄───────────────────────────│                           │                              │
```

### 3.2 Agent 自主推理模式（`/ai/agent/sse`）

适用场景：复杂多步任务。AxinManus 手动控制每一步 Think→Act 循环。

```
Client              AiController           AxinManus                   DashScope LLM
  │                      │                     │                            │
  │  GET /ai/agent/sse   │                     │                            │
  │  ?message=xxx        │                     │                            │
  │─────────────────────►│                     │                            │
  │                      │                     │                            │
  │                      │  new AxinManus(     │                            │
  │                      │    allTools,        │                            │
  │                      │    primaryModel,    │                            │
  │                      │    logAdvisor       │                            │
  │                      │  )                  │                            │
  │                      │────────────────────►│                            │
  │                      │                     │                            │
  │                      │  runStream(msg)     │                            │
  │                      │────────────────────►│                            │
  │                      │                     │                            │
  │                      │                     │  ┌─── ReAct 循环 ───┐     │
  │                      │                     │  │                  │     │
  │                      │                     │  │ think() ─────────►│     │
  │                      │                     │  │        调用 LLM   │     │
  │                      │                     │  │◄─────────────────│     │
  │                      │                     │  │                  │     │
  │                      │                     │  │ 有工具调用?       │     │
  │                      │                     │  │   YES → act()    │     │
  │                      │                     │  │   执行工具        │     │
  │                      │                     │  │   结果注入上下文   │     │
  │                      │                     │  │   NO  → 结束循环  │     │
  │                      │                     │  │                  │     │
  │                      │                     │  │ 达到20步上限?     │     │
  │                      │                     │  │   YES → FINISHED  │     │
  │                      │                     │  │                  │     │
  │                      │                     │  │ TerminateTool?   │     │
  │                      │                     │  │   YES → FINISHED  │     │
  │                      │                     │  └──────────────────┘     │
  │                      │                     │                            │
  │                      │  SSE: Step 1: xxx   │                            │
  │  ◄──SSE──────────────│◄────────────────────│                            │
  │  ◄──SSE──────────────│◄────────────────────│                            │
  │  ◄──SSE──────────────│◄────────────────────│                            │
  │                      │                     │                            │
```

---

## 四、Agent 引擎内部结构

### 4.1 类继承体系

```
BaseAgent                         抽象基类
├── name, systemPrompt            Agent 身份定义
├── state: IDLE→RUNNING→FINISHED  状态机
├── messageList: List<Message>     自维护对话上下文
├── maxSteps = 10/20              最大步数上限
├── maxMessageWindowSize = 40     滑动窗口大小
├── workingMemorySummary          压缩摘要记忆
│
├── run(String) → String          同步执行
├── runStream(String) → SseEmitter SSE 流式执行
├── step()                         抽象方法，子类实现
├── trimMessageList()             滑动窗口截断 + LLM 摘要压缩
│
└── ReActAgent                    实现 ReAct 范式
    ├── think() → boolean         调用 LLM，返回是否需要行动
    ├── act() → String            执行工具调用
    └── step() = think + act      模板方法
        │
        └── ToolCallAgent         具体实现
            ├── ChatClient         持有 LLM 客户端
            ├── ToolCallback[]     可用工具列表
            ├── ToolCallingManager 手动执行工具调用
            │
            └── AxinManus         最终 Agent 实体
                ├── SYSTEM_PROMPT  通用助手身份
                ├── maxSteps = 20
                └── 注入 allTools + LogAdvisor
```

### 4.2 滑动窗口记忆压缩

```
消息列表增长超过 40 条时触发 trim:

Before: [Msg1, Msg2, Msg3, ..., Msg38, Msg39, Msg40, Msg41, Msg42]
                                                          ↑ 超过 40 条

After:  [Msg1, SystemMessage("历史摘要: ..."), Msg40, Msg41, Msg42]
          ↑          ↑                                ↑
     初始用户消息   压缩摘要（≤120字/次，合并≤500字）   最新消息
```

---

## 五、工具系统

### 5.1 工具注册

`ToolRegistration` 是唯一入口，将所有工具封装为 `@Bean("allTools")`：

```
ToolRegistration.allTools()
├── WebSearchTool(apiKey)     → 调用 SearchAPI.io 百度搜索，返回前5条
├── WebScrapingTool()         → Jsoup 抓取网页文本，3000字截断
├── PDFGenerationTool(store)  → iText 生成中文 PDF，落盘 ./tmp/pdf/
├── FileSystemTool()          → workspace/ 沙箱中读/写/列文件
├── HttpRequestTool()         → GET/POST 外部 API，5000字截断
├── CodeExecutionTool()       → 进程级执行 Python/Shell，30s 超时
└── TerminateTool()           → 发送终止信号，Agent 状态 → FINISHED
```

### 5.2 工具调用流程（Agent 模式）

```
think() 阶段:
  ChatClient.prompt(messageList)
    .system(systemPrompt)
    .tools(webSearch, webScraping, pdf, fileSystem, httpRequest, codeExecution, terminate)
    .call()
    → LLM 返回 AssistantMessage（含 ToolCall 列表）

act() 阶段:
  ToolCallingManager.executeToolCalls(prompt, chatResponse)
    → 依次执行每个 ToolCall
    → 将 AssistantMessage + ToolResponseMessage 写回 messageList
    → 检测到 terminateTool → state = FINISHED
```

---

## 六、模型路由层

```
application.yml                    ModelConfig                   ModelRouter
────────────────────────────────────────────────────────────────────────
axin.model.primary:               primaryChatModel Bean         ModelType.PRIMARY
  model-name: qwen-max             ├── model: qwen-max          → qwen-max
  temperature: 0.7                 ├── temperature: 0.7         (Agent ReAct 推理)
  max-tokens: 4096                 └── maxTokens: 4096

axin.model.light:                 lightChatModel Bean           ModelType.LIGHT
  model-name: qwen-turbo           ├── model: qwen-turbo        → qwen-turbo
  temperature: 0.3                 ├── temperature: 0.3         (规划/分类/路由)
  max-tokens: 2048                 └── maxTokens: 2048
```

两个 Bean 都基于同一个 DashScope `ChatModel` 实例 `mutate()` 克隆，仅参数不同。

---

## 七、外部依赖拓扑

```
axin-agent
├── [LLM] DashScope API (阿里云)
│   └── qwen-max / qwen-turbo
├── [MCP] amap-maps (高德地图)
│   └── npx @amap/amap-maps-mcp-server
├── [搜索] SearchAPI.io
│   └── 百度搜索引擎
├── [文件] 本地文件系统
│   ├── ./tmp/pdf/      PDF 输出
│   ├── ./tmp/code/     代码执行临时文件
│   └── ./workspace/    文件工具沙箱
└── [HTTP] 任意外部 API
    └── HttpRequestTool 发出的 GET/POST 请求
```

---

## 八、目录结构

```
axin-agent/
├── pom.xml
├── CLAUDE.md
├── ds-structure-v1.md                    ← 本文档
├── future.md
├── structure.md
│
├── src/main/java/com/axin/axinagent/
│   ├── AxinAgentApplication.java        启动类
│   ├── agent/
│   │   ├── BaseAgent.java               状态机基类 + 滑动窗口记忆
│   │   ├── ReActAgent.java              think/act 模板
│   │   ├── ToolCallAgent.java           工具调用实现
│   │   ├── AxinManus.java              最终 Agent（@Component）
│   │   └── model/AgentState.java        状态枚举
│   ├── advisor/LogAdvisor.java          日志拦截
│   ├── tool/
│   │   ├── ToolRegistration.java        工具注册中心（@Configuration）
│   │   ├── WebSearchTool.java           百度搜索
│   │   ├── WebScrapingTool.java         网页抓取
│   │   ├── PDFGenerationTool.java       PDF 生成
│   │   ├── FileSystemTool.java          文件读写
│   │   ├── HttpRequestTool.java         HTTP 客户端
│   │   ├── CodeExecutionTool.java       代码执行
│   │   └── TerminateTool.java           终止信号
│   ├── llm/
│   │   ├── ModelType.java               枚举 PRIMARY/LIGHT
│   │   └── ModelRouter.java             模型路由
│   ├── infrastructure/storage/
│   │   ├── StorageService.java          存储接口
│   │   └── LocalStorageServiceImpl.java 本地文件实现
│   ├── config/
│   │   ├── CorsConfig.java              跨域配置
│   │   ├── ModelConfig.java             双模型 Bean 配置
│   │   └── ModelProperties.java         @ConfigurationProperties
│   ├── controller/AiController.java     3 个 API 端点
│   ├── common/
│   │   ├── BaseResponse.java            统一响应体
│   │   └── ResultUtils.java             响应工厂
│   └── exception/
│       ├── ErrorCode.java               错误码枚举
│       ├── BusinessException.java       业务异常
│       ├── GlobalExceptionHandler.java  全局异常处理
│       └── ThrowUtils.java              条件抛出工具
│
├── src/main/resources/
│   ├── application.yml                  配置
│   └── mcp-servers.json                 MCP Server 注册
│
└── src/test/java/
    └── AxinAgentApplicationTests.java   上下文加载测试
```
