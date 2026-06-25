# AI Coding Assistant Project

Spring Boot 3.4.4 (Java 21, Maven) + Vue 3 (TypeScript, Vite, Pinia).

## Quick start

```bash
# 1. Backend (Spring Boot, port 8080)
mvn compile                    # also: mvn test, mvn clean

# 2. Frontend (Vite, port 5173, proxies /api -> localhost:8080)
cd frontend && npm run dev
```

## Architecture

- **Backend entry**: `com.org.ai.AIApp`
- **Controllers**: `com.org.ai.web.controller` — `/api/agents`, `/api/code-assistant/chat`, `/api/workspace`
- **Code assistant agent** (`com.org.ai.agents.codeassistant.service.CodeAssistantAgentService`): reads system prompt from `classpath:prompts/code-assistant-system.md` with `${}` placeholders substituted at runtime
- **Customer service agent** is a **stub** (not functional)
- **Empty stubs** exist: `SensitiveWordAdvisor`, `QwenConfig`, all `customer_service` classes
- **Chat memory**: last 50 messages (`MessageWindowChatMemory`)
- **AI model**: `deepseek-v4-flash` via OpenAI-compatible API (`https://api.deepseek.com`)

## Tools available to the agent

| Tool | Source | Notes |
|------|--------|-------|
| `readFile`, `writeFile`, `listDirectory`, `findFiles`, `deleteFile`, `confirmOperation` | `ProjectCodeTool` | Paths relative to workspace; write/delete requires `write-enabled=true`; `deleteFile` returns deleted file list |
| `executeCommand` | `ProjectCodeTool` | **Only** `mvn compile`, `mvn test`, `mvn clean` — anything else rejected |
| `webSearch` | `WebSearchTool` | Defaults to Baidu; requires `agent.web-search.enabled=true` |
| `listSkills`, `loadSkill` | `SkillTool` | Skills loaded from `<workspace>/skills/<name>/SKILL.md` |

## Skills system

4 skills at `skills/`: `brainstorming` (方案设计), `code-review` (代码审查), `debugging` (系统化调试), `tdd` (测试驱动开发).
The system prompt instructs the agent to call `loadSkill()` before starting relevant tasks.

## Permission system (security)

Tool-level permission control configured in `application.yml` via `agent.code-assistant.permission`:

- `deny` — tool call is blocked with an error message
- `ask` — tool call returns a request for user confirmation
- Default (not listed) — `allow`, tool works normally

Supports argument pattern matching: `"executeCommand: mvn clean"` blocks only that specific command. Current defaults: `deleteFile` is in `deny`, `"executeCommand: mvn clean"` is in `ask`.

## Testing

- No tests exist (`src/test/java` is empty)
- Backend: `mvn test`
- Frontend: no test framework configured

## Code conventions

- Backend: Lombok (`@Slf4j`, `@Data`, `@Builder`, `@RequiredArgsConstructor`), SLF4J logging, `Class<T>` naming
- Frontend: Strict TypeScript (`noUnusedLocals`, `noUnusedParameters`, `erasableSyntaxOnly`), Vue 3 `<script setup>` SFCs, Pinia stores, CSS variables from `src/style.css`
