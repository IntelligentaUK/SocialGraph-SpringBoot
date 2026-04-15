# Repo-local skills

Short, procedural guides for LLM agents working in this repo. Each skill below
routes to canonical documentation in [`docs/`](../../docs/README.md) and encodes
workflow rules that are easy to miss on a first read of the codebase.

Start by loading `docs/README.md` for orientation. Invoke a skill **before**
making changes that match its triggers.

## Available skills

| Skill | When to use |
|-------|-------------|
| [`api-endpoint-work/SKILL.md`](api-endpoint-work/SKILL.md) | Adding, removing, or modifying any `/api/...` route, controller, request / response DTO, or error code. |
| [`redis-schema-work/SKILL.md`](redis-schema-work/SKILL.md) | Adding or changing any Redis key — new hash field, new list, new zset, new counter, new prefix. |
| [`storage-provider-work/SKILL.md`](storage-provider-work/SKILL.md) | Adding a new object-storage backend, changing the `ObjectStorageService` contract, or adjusting upload / signed-URL behavior. |

## Canonical references for agents

- [`docs/README.md`](../../docs/README.md) — documentation index
- [`docs/architecture.md`](../../docs/architecture.md) — component view and
  request lifecycle
- [`docs/api/README.md`](../../docs/api/README.md) — full HTTP surface
- [`docs/internals/redis-schema.md`](../../docs/internals/redis-schema.md) —
  every Redis key the app touches
- [`../../llms.txt`](../../llms.txt) — compact LLM index
- [`../../llms-full.txt`](../../llms-full.txt) — single-file documentation bundle

## How these skills work

Each skill is a single `SKILL.md` with YAML frontmatter (`name`, `description`,
`when-to-use`) followed by a procedural body. The body is deliberately terse —
agents should rely on the linked doc pages for depth.

Skills do **not** duplicate the docs. If a skill says "add an entry to the
Redis schema table," that entry belongs in
[`docs/internals/redis-schema.md`](../../docs/internals/redis-schema.md), not
inside the skill.

See [`docs/agent-skills.md`](../../docs/agent-skills.md) for the human-facing
description of the skills model and how to add a new one.
