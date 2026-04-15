# Agent skills

SocialGraph ships a small set of **repo-local skills** under
[`.claude/skills/`](../.claude/skills/README.md). These are concise procedural
guides designed to be consumed by LLM agents (Claude Code, Codex, Copilot,
Gemini CLI, or any MCP-aware client that can load markdown skills from the
repo). Each skill routes the agent to the right documentation pages and encodes
workflow rules that experience has shown to trip up naive edits.

Skills are **not a replacement for the docs**. They are short triggers plus
decision trees; the canonical information lives in [`docs/`](README.md).

## Skill index

| Skill | Triggers on | What it does | Canonical docs |
|-------|-------------|--------------|----------------|
| [`api-endpoint-work`](../.claude/skills/api-endpoint-work/SKILL.md) | Adding, removing, or modifying any `/api/...` route, or adjusting a controller, DTO, or error mapping. | Enforces the end-to-end checklist: code change → controller test → `ApiSurfaceRegressionTest` → API reference doc → error table. | [API reference](api/README.md), [errors](api/errors.md), [testing](testing.md) |
| [`redis-schema-work`](../.claude/skills/redis-schema-work/SKILL.md) | Adding or changing any Redis key — new hash field, new list, new zset, new counter, new prefix. | Walks the reader through the write / read / test / doc update sequence and points at the keys table. | [Redis schema](internals/redis-schema.md), [timeline delivery](internals/timeline-delivery.md) |
| [`storage-provider-work`](../.claude/skills/storage-provider-work/SKILL.md) | Adding a new object-storage backend, changing the `ObjectStorageService` contract, or adjusting upload / signed-URL behavior. | Details the `@ConditionalOnProperty` pattern, properties class, `application.yml` block, configuration doc, and tests. | [Storage providers](internals/storage-providers.md), [configuration](configuration.md), [API: storage](api/storage.md) |

## When to invoke a skill

- **Starting any change** that matches a skill trigger. Reading the skill first
  keeps you from forgetting the doc or regression-test update that the rest of
  the workflow expects.
- **Writing a plan** for a non-trivial change. Skills encode "don't forget this"
  items that belong in the plan checklist.
- **Reviewing a PR** that touches the matching surface. The skill doubles as a
  review checklist.

## When NOT to invoke a skill

- **One-file refactors** that stay inside a single service and don't change
  any HTTP response, Redis key, env var, or error code.
- **Pure read tasks** — if you are asking a question about the system, go to
  [`docs/`](README.md) directly.

## Skills index file

`.claude/skills/README.md` is the canonical index inside the skills directory.
It mirrors the table above and is the entry point an agent loads at session
start.

## Adding a new skill

1. Create a subdirectory under `.claude/skills/<kebab-case-name>/`.
2. Add `SKILL.md` with frontmatter:

   ```markdown
   ---
   name: <kebab-case-name>
   description: <one-line trigger sentence>
   when-to-use: <short clause — "use when adding an X or changing Y">
   ---

   # <Human-readable title>

   ...procedural body with explicit steps and links into docs/...
   ```
3. Add a row to both this page and `.claude/skills/README.md`.
4. Cross-link the skill from the relevant docs page so readers who reach the
   docs first also find the skill.

Keep skills **short and procedural**. If the skill grows past ~200 lines, split
it or move the reference material into `docs/`.
