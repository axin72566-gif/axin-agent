# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
# Compile and run tests
mvn clean test

# Run a single test class
mvn test -Dtest=LoveAppTest

# Run a single test method
mvn test -Dtest=LoveAppTest#methodName

# Package (skip tests)
mvn clean package -DskipTests

# Start the application
mvn spring-boot:run
```

The project requires **Java 21** and uses Lombok annotation processing (configured in the compiler plugin in `pom.xml`).

## Architecture Overview

This is a **Spring Boot 3.5 AI Agent framework** built on Spring AI 1.1.2 + Alibaba DashScope (Qwen models). It supports single-agent ReAct execution and multi-agent orchestrated collaboration, with async task dispatch via RocketMQ.

### Class Hierarchy (Agent Engine)

```
BaseAgent                  — State machine, message list, sliding window truncation, summary compression
  └── ReActAgent           — think() / act() template method loop
        └── ToolCallAgent  — Manual tool calling via ToolCallingManager (Spring AI auto-call disabled)
              ├── AxinManus        — General-purpose agent with all tools, up to 20 steps
              ├── ResearchAgent    — Search-only agent (webSearch, webScraping, terminate)
              └── WriterAgent      — Content-generation agent (pdfGeneration, fileSystem, terminate)
```

`AxinManus` is instantiated per-request (not singleton) because `BaseAgent` holds mutable state (`messageList`, `state`, `currentStep`). In the async task path, `AgentTaskConsumer` creates an anonymous subclass of `AxinManus` that overrides `step()` to add progress push and cancellation checks.

### Multi-Agent Orchestration

```
PlannerAgent (LIGHT model) → TaskPlan (JSON structured output)
  └── OrchestratorAgent → dispatches SubTasks by AgentRole:
        RESEARCH → ResearchAgent
        WRITER   → WriterAgent
        MANUS    → AxinManus (PRIMARY model)
```

`PlannerAgent` uses the LIGHT model (`qwen-turbo`) for cost-efficient task decomposition. `OrchestratorAgent` runs sub-tasks sequentially, injecting prior results as context and checking dependencies.

### Task Scheduling Layer (RocketMQ)

```
HTTP POST /api/ai/manus/task/submit → AgentTaskService.submitTask()
  → writes Redis Hash (state=PENDING) + DB entity
  → sends RocketMQ message
  → AgentTaskConsumer.onMessage()
    → SINGLE mode: AxinManus with progress-aware step()
    → MULTI mode: OrchestratorAgent.execute()
    → pushes step results to Redis List for SSE streaming
```

Clients subscribe to real-time progress via `GET /api/ai/manus/task/progress/{taskId}` (SSE long-polling from Redis List). Task cancellation is cooperative: a cancel flag in Redis is checked before each step.

### Two-Model Router

`ModelConfig` clones the DashScope `ChatModel` into two beans:
- **`primaryChatModel`** — `qwen-max`, temperature 0.7, 4096 tokens (Agent ReAct loops)
- **`lightChatModel`** — `qwen-turbo`, temperature 0.3, 2048 tokens (planning, classification)

`ModelRouter` returns the right model by `ModelType` enum. Configured in `application.yml` under `axin.model.*`.

### Memory Architecture

| Layer | Implementation | Storage |
|-------|---------------|---------|
| Short-term (conversation) | `MessageWindowChatMemory` → `FileBasedChatMemory` | Kryo-serialized files in `./chat-memory/` |
| Long-term (semantic recall) | `LongTermMemoryService` | Redis vector store |
| Episodic (important events) | `EpisodicMemoryService` | MySQL via JPA |
| Working memory | `BaseAgent.workingMemorySummary` | In-memory, sliding window + LLM summarization |

`MemoryFacade` provides a unified API across all memory types.

### Tool System

`ToolRegistration` is the central `@Configuration` where all tools are registered as Spring beans. Available tools: `WebSearchTool`, `WebScrapingTool`, `PDFGenerationTool`, `EpisodicMemoryTool`, `FileSystemTool`, `HttpRequestTool`, `CodeExecutionTool`, `TerminateTool`. Tool subsets (`searchTools`, `writerTools`, `fileTools`) are filtered by name for role-specific agents. MCP tools are loaded separately via `spring-ai-starter-mcp-client` from `classpath:mcp-servers.json`.

### RAG (Strategy Pattern)

`RagStrategyContext` auto-collects all `RagAdvisorStrategy` implementations at startup. Two strategies: `LocalRagAdvisorStrategy` (Redis vector store) and `CloudRagAdvisorStrategy` (DashScope cloud knowledge base). Switch via `RagStrategy` enum at call time.

### Observability

`AgentTraceService` records per-step trace entries and LLM call records to Redis Lists. `MetricsService` tracks task counts (total/finished/error/cancelled) and tool call success/fail rates via Redis hashes. `CostCalculator` estimates token costs. All data is keyed by `taskId` from `TaskContext` (ThreadLocal).

## Key Configuration

- Server port: `8123`, context path: `/api`
- API docs: `/api/swagger-ui.html` (Knife4j)
- Redis: `192.168.60.131:6379`, database 12
- MySQL: `192.168.60.131:3306/axin_agent`
- RocketMQ: `192.168.60.131:9876`
- Local file storage root: `./tmp`
- RAG init on startup controlled by `axin.rag.init-on-startup`

## Known Issues (from future.md)

- `PDFGenerationTool` Chinese font may fail in production — `font-asian` dependency scope should be `compile`
- `WebSearchTool.subList(0, 5)` can throw IndexOutOfBoundsException when results < 5
- `WebScrapingTool` returns raw HTML — should truncate to ~3000 chars
- API keys are hardcoded in `application.yml` — should use environment variables for production
