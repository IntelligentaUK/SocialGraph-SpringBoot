# SocialGraph

A social graph REST API written in Java 25 on Spring Boot 4, backed by Redis for all
social state and pluggable object storage (Azure Blob, Google Cloud Storage, or
Oracle Cloud Infrastructure) for media.

> **Status: Alpha.** The API surface is stable enough to use for integration work, but
> storage tuning and operational hardening are still in progress. The JVM test gate is
> `./mvnw test`. Native-image support is configured but requires Graal-specific
> follow-up. CPU-intensive work (hashing, encryption, seam carving) is a candidate for
> a Rust port — not there yet.

## What it does

SocialGraph implements the core social surface a timeline app needs:

- **Accounts** — register, login, activate, profile edit, RSA key exchange bootstrap.
- **Graph** — follow / unfollow, friends (mutual follows), block / unblock, mute / unmute.
- **Posts** — text, photo, and video status updates; replies; reshares; edit; delete.
- **Timelines** — FIFO, personal-importance, and global-importance feeds per user.
- **Reactions** — like, love, fav, and share with listable actor counts per post.
- **Content filters** — negative keyword blocklists and MD5 image blocklists per user.
- **Media** — seam-carved (liquid rescaled) image uploads pushed to the active object
store, plus a provider-neutral signed upload target endpoint.

The legacy advanced-auth helpers from the old codebase (`/api/aes/key`,
`/api/get/image`, etc.) were intentionally **not** migrated. `/api/session` is
preserved as the public RSA bootstrap endpoint.

## Quickstart

```bash
# 1. Start Redis
docker run -d --name socialgraph-redis -p 6379:6379 redis:trixie

# 2. Point storage at a bucket you control, e.g. Azure Blob:
export STORAGE_PROVIDER=azure
export AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;AccountName=...;AccountKey=..."
export AZURE_STORAGE_CONTAINER=photos

# 3. Build and run
./mvnw spring-boot:run
# Server listens on http://localhost:4567
```

Smoke test:

```bash
curl http://localhost:4567/api/ping            # -> "hello"
curl -X POST "http://localhost:4567/api/register?username=alice&password=hunter2&email=alice@example.com"
```

Full install, configuration, and run instructions: [docs/getting-started.md](docs/getting-started.md).

## Documentation

The complete documentation set lives under `[docs/](docs/README.md)`:

- **[Getting started](docs/getting-started.md)** — install, run, environment
- **[Configuration](docs/configuration.md)** — every environment variable and `application.yml` field
- **[Architecture](docs/architecture.md)** — components, Redis-first data flow, Mermaid diagrams
- **[Authentication](docs/authentication.md)** — token model, filter chain, public endpoints
- **[API reference](docs/api/README.md)** — every endpoint: method, path, auth, params, responses
- **[Internals](docs/internals/redis-schema.md)** — Redis schema, timeline delivery, storage providers, image pipeline
- **[Testing](docs/testing.md)** — test surface, how to run, what is actually asserted
- **[Changelog](CHANGELOG.md)**

For LLM agents working in this repo:

- `**[llms.txt](llms.txt)`** — compact LLM-oriented index
- `**[llms-full.txt](llms-full.txt)`** — single-file documentation bundle for ingestion
- `**[.claude/skills/](.claude/skills/README.md)**` — repo-local agent skills for common tasks
- **[Agent skills index](docs/agent-skills.md)** — what each skill does

## Repository layout

```
src/main/java/com/intelligenta/socialgraph/
  SocialGraphApplication.java    Spring Boot entrypoint
  controller/                    REST controllers (Auth, User, Action, Status, Timeline, Storage)
  service/                       Business logic (User, Session, Share, Timeline, Action, Device)
  service/storage/               ObjectStorageService + Azure/GCP/OCI impls
  model/                         DTOs and records
  config/                        AppProperties, StorageProperties, SecurityConfig, RedisConfig
  security/                      TokenAuthenticationFilter, AuthenticatedUser
  exception/                     Typed exceptions + GlobalExceptionHandler
  util/                          Util, ImagePayloads
  Verbs.java                     Action verb enum (like, love, fav, share, hug)
  LiquidRescaler.java            Seam carving (liquid rescale)
  PasswordHash.java              Argon2 / PBKDF2 helpers
src/main/resources/application.yml
src/test/java/com/intelligenta/socialgraph/
  ApiSurfaceRegressionTest.java  Fixes the public HTTP surface (51 endpoints)
  controller/                    Controller slice tests
  service/                       Service tests
  support/                       Custom @AuthenticationPrincipal resolver for tests
```

