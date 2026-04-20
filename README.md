# SocialGraph

A social graph REST API written in Java 25 on Spring Boot 4. The persistence
layer is pluggable — **Redis Stack** (default) or **Infinispan** (RESP-compat
or native HotRod, embedded ephemeral tier + cluster tier) — with pluggable
object storage (Azure Blob, Google Cloud Storage, or Oracle Cloud
Infrastructure) for media.

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
store, plus a provider-neutral signed upload target endpoint. Multi-image posts
support up to N images per status update.
- **Vector search** — SigLIP-2 + Gemma-powered multimodal retrieval over the last
7 days of posts. Two endpoints: `POST /api/search/question` ranks posts by image
+ Gemma-summary fused embedding; `POST /api/search/ai` ranks by caption-text
embedding. Backed by Redis Stack (RediSearch KNN) and a Rust sidecar that hosts
both models.
- **Any Spring AI provider** — routes embeddings, chat (visual summary), image
generation, and moderation to any of 18 Spring AI 2.0 model providers (OpenAI,
Anthropic, Vertex AI, Bedrock, Azure OpenAI, Ollama, Mistral, Stability AI,
Zhipu, MiniMax, OCI GenAI, DeepSeek, PostgresML, local ONNX, …) or the bundled
Rust sidecar. One active provider per capability; swap models + dims via
`ai.<capability>.*` without touching code. See [docs/ai.md](docs/ai.md).
- **Image generation** — `POST /api/images/generate` backed by the active
image provider (DALL-E, Stable Diffusion XL, cogview, …).
- **Inline content moderation** — OpenAI omni-mod or Mistral moderation;
flagged posts return `400 content_blocked` before anything hits Redis.

The legacy advanced-auth helpers from the old codebase (`/api/aes/key`,
`/api/get/image`, etc.) were intentionally **not** migrated. `/api/session` is
preserved as the public RSA bootstrap endpoint.

## Quickstart

```bash
# 1. Start Redis Stack (RediSearch is required for vector search)
docker compose up -d redis

# 2. Point storage at a bucket you control, e.g. Azure Blob:
export STORAGE_PROVIDER=azure
export AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;AccountName=...;AccountKey=..."
export AZURE_STORAGE_CONTAINER=photos

# 3. Build and run
./mvnw spring-boot:run
# Server listens on http://localhost:4567
```

### Alternative: Infinispan

```bash
# RESP-compat mode (drop-in; existing Lettuce client talks to Infinispan RESP endpoint)
docker compose --profile infinispan-resp up -d
PERSISTENCE_PROVIDER=infinispan INFINISPAN_CLIENT_MODE=resp \
  INFINISPAN_RESP_USERNAME=admin INFINISPAN_RESP_PASSWORD=admin \
  ./mvnw spring-boot:run

# Native mode (HotRod + embedded ephemeral tier)
docker compose --profile infinispan up -d
PERSISTENCE_PROVIDER=infinispan INFINISPAN_CLIENT_MODE=native \
  INFINISPAN_HOTROD_USERNAME=admin INFINISPAN_HOTROD_PASSWORD=admin \
  ./mvnw spring-boot:run
```

Vector search (`/api/search/*`) and the embedding pipeline are only active
under the Redis provider today; see [docs/persistence.md](docs/persistence.md)
for the provider matrix and trade-offs.

Smoke test:

```bash
curl http://localhost:4567/api/ping            # -> "hello"
curl -X POST "http://localhost:4567/api/register?username=alice&password=hunter2&email=alice@example.com"
```

Full install, configuration, and run instructions: [docs/getting-started.md](docs/getting-started.md).

## Verifying vector search

```bash
# 1. Bring up Redis Stack + the embedding sidecar.
docker compose --profile sidecar up -d
curl -fsS http://localhost:8000/healthz   # -> {"status":"ok"} once both models are loaded
                                          # (default build uses stub models; instant)

# 2. Register and grab a token.
TOKEN=$(curl -s -X POST "http://localhost:4567/api/register?username=alice&password=hunter2&email=alice@example.com" | jq -r .token)

# 3. Post a multi-image photo status.
curl -X POST http://localhost:4567/api/status \
  -H "Authorization: Bearer $TOKEN" \
  -F "type=photo" -F "content=sunset at the pier" \
  -F "files=@./fixtures/pier1.jpg" -F "files=@./fixtures/pier2.jpg"

# 4. Wait a second for EmbeddingWorker to finish, then verify the embedding hash exists.
sleep 2
POST_ID=<id-from-step-3>
redis-cli EXISTS embedding:post:$POST_ID    # -> 1
redis-cli FT.INFO idx:post:embedding        # num_docs should increment

# 5. Query.
curl -X POST http://localhost:4567/api/search/question \
    -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' \
    -d '{"query":"waterfront at dusk","limit":10}' | jq
curl -X POST http://localhost:4567/api/search/ai \
    -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' \
    -d '{"query":"pier","limit":10}' | jq
```

The default sidecar build uses **stub** models (deterministic unit-norm vectors
seeded off the input hash) so the full pipeline is exercisable locally without
any model download. Rebuild the sidecar with the `models` feature to swap in
real candle-backed SigLIP-2 + Gemma loaders.

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
- `**[.claude/skills/](.claude/skills/README.md)`** — repo-local agent skills for common tasks
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

