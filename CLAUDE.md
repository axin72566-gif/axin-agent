# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
mvn clean test                              # Compile and run all tests
mvn test -Dtest=AxinAgentApplicationTests   # Run a single test
mvn clean package -DskipTests               # Package (skip tests)
mvn spring-boot:run                         # Start the application
```

The project requires **Java 21** (`D:/java/jdk`). Set `JAVA_HOME=D:/java/jdk` if your default is JDK 17. Lombok annotation processing is configured in the compiler plugin.

## Architecture Overview

This is a **general-purpose AI Agent** (like Doubao) built on Spring Boot 3.5 + Spring AI 1.1.2 + Alibaba DashScope (Qwen models). You send a message, the agent reasons and uses tools, you get a result.

### Agent Engine

```
BaseAgent                  — State machine (IDLE→RUNNING→FINISHED/ERROR), sliding window truncation, LLM summary compression
  └── ReActAgent           — think() / act() template method loop
        └── ToolCallAgent  — Manual tool calling via ToolCallingManager (Spring AI auto-call disabled)
              └── AxinManus — General-purpose agent, registered with all 7 tools, max 20 steps
```

`AxinManus` is `@Component` but instantiated per-request in the controller because `BaseAgent` holds mutable per-conversation state (`messageList`, `state`, `currentStep`).

### API Endpoints

| Endpoint | Description |
|---|---|
| `GET /api/ai/chat/sync?message=xxx&chatId=xxx` | Sync chat with tool access + conversation memory |
| `GET /api/ai/chat/sse?message=xxx&chatId=xxx` | SSE streaming chat (Flux) |
| `GET /api/ai/agent/sse?message=xxx` | AxinManus ReAct agent with per-step SSE push |

### Two-Model Router

`ModelConfig` clones DashScope `ChatModel` into two beans:
- **`primaryChatModel`** — `qwen-max`, temp 0.7, 4096 tokens (agent ReAct loops)
- **`lightChatModel`** — `qwen-turbo`, temp 0.3, 2048 tokens (classification, routing)

`ModelRouter.get(ModelType)` returns the right bean. Configured under `axin.model.*` in `application.yml`.

### Tool System

`ToolRegistration` is the central `@Configuration`. Seven tools registered as `allTools` bean:

| Tool | Purpose |
|---|---|
| `WebSearchTool` | Web search via SearchAPI.io |
| `WebScrapingTool` | Scrape and extract webpage content |
| `PDFGenerationTool` | Generate Chinese PDF from markdown |
| `FileSystemTool` | Read/write/list files in `./workspace` |
| `HttpRequestTool` | Send arbitrary HTTP GET/POST requests |
| `CodeExecutionTool` | Execute Python/JS code snippets |
| `TerminateTool` | Agent signals task completion |

MCP tools are loaded via `spring-ai-starter-mcp-client` from `classpath:mcp-servers.json` (Amap Maps + image search).

### Chat Memory

Conversation memory uses `MessageWindowChatMemory` with `InMemoryChatMemoryRepository` (max 20 messages per conversation). `chatId` parameter groups messages into conversations.

## Key Configuration

- Server: port `8123`, context path `/api`
- API docs: `/api/swagger-ui.html` (Knife4j)
- LLM API key: `spring.ai.dashscope.api-key` (use env var `DASHSCOPE_API_KEY` in production)
- Search API key: `search-api.api-key` (use env var `SEARCH_API_KEY` in production)
- Local file storage: `./tmp` (PDF output) and `./workspace` (file system tool sandbox)

## Known Issues

- `PDFGenerationTool` Chinese font: `font-asian` dependency scope should be `compile` for production
- `WebSearchTool.subList(0, 5)` can throw `IndexOutOfBoundsException` when results < 5
- `WebScrapingTool` returns raw HTML — should truncate to prevent token waste
