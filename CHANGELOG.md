# Changelog

Append-only history of meaningful changes to SocialGraph. Entries are grouped by
release. Unreleased work sits at the top.

## [Unreleased]

### Added

- **Multimodal vector search** — SigLIP-2 + Gemma VLM powered retrieval over the last 7 days of posts.
  - Two endpoints: `POST /api/search/question` (multimodal, ranks image + Gemma summary fused vectors) and `POST /api/search/ai` (text-only, ranks SigLIP-2 caption embeddings). Both accept `{query, limit?}`, return `{results, count, durationMs}` with typed `SearchResult` records.
  - Rust sidecar (`embedding-sidecar/`, axum + candle) exposes `/summarize`, `/embed/text`, `/embed/image-text`, `/healthz`. Ships with deterministic stub models (default build) and a `models` feature flag for the real candle-backed loaders — lets the rest of the stack be exercised without downloading ~20 GB of weights.
  - Async embedding pipeline via Redis Streams: `ShareService.createStatusUpdate` XADDs each new post (except videos and empty posts) to `embedding:queue`; `EmbeddingWorker` consumes via `XREADGROUP` and HSETs `embedding:post:<id>` hashes with an 8-day TTL. Failures retry up to 3× before being redirected to `embedding:queue:dlq`.
  - RediSearch HNSW index (`idx:post:embedding`) with COSINE distance, 1152-dim FLOAT32, author_uid TAG, created NUMERIC SORTABLE. Created idempotently at startup by `RedisSearchIndexInitializer`.
- **Multi-image posts.** `POST /api/status` now accepts `multipart/form-data` with a `files[]` field; up to `embedding.max-images-per-post` (default 10) images are uploaded per post. Stored as an ordered list at `post:<id>:images` with a new `imageCount` field on the post hash. `TimelineEntry.imageUrls` carries the full ordered list; legacy single-image posts (no `imageCount`) are backfilled on read to `[url]`.

### Changed

- **Redis Stack is required.** The plain `redis` image no longer works — RediSearch is mandatory for vector search. A `docker-compose.yml` at repo root brings up `redis/redis-stack-server:7.4.0-v0` (and, under the `sidecar` profile, the embedding sidecar).
- **Dev docker command.** `docker run ... redis:trixie` → `docker compose up -d redis`. `docs/getting-started.md` and `README.md` updated.
- **Initial deep documentation pass.** Added the `docs/` tree (getting started,
  configuration, architecture, authentication, per-controller API reference, Redis
  internals, storage providers, image pipeline, testing, agent skills), a new
  top-level README that matches the current Spring Boot product, `llms.txt` and
  `llms-full.txt` for LLM consumers, this changelog, and repo-local agent skills
  under `.claude/skills/`. No behavior changes.

### Breaking changes

- **Dev Redis image.** `redis:trixie` → `redis/redis-stack-server:7.4.0-v0`. Without the `search` module loaded, app startup fails fast with a clear message.
- **`ShareService` constructor** now requires an `EmbeddingProperties` dependency. Downstream code that constructs this service by hand needs updating.

### Environment variables (new)

`EMBEDDING_SIDECAR_URL` (default `http://localhost:8000`), plus every `embedding.*` key in `application.yml` is env-overridable (uppercased, hyphens → underscores). Sidecar-side: `SIDECAR_PORT`, `MODEL_CACHE_DIR`, `HF_TOKEN`, `MAX_IMAGES_PER_REQUEST`, `IMAGE_FETCH_TIMEOUT_S`, `MAX_IMAGE_BYTES`, `SIGLIP_MODEL_ID`, `GEMMA_MODEL_ID`, `VECTOR_DIM`, `RUST_LOG`.

### Verification

- `./mvnw test` — 117 JUnit tests including Testcontainers-backed RediSearch integration (`RedisSearchIndexInitializerTest`, `VectorSearchServiceTest`) and MockRestServiceServer-backed `EmbeddingClientTest`.
- `cargo test` (in `embedding-sidecar/`) — 13 tests across unit (stub vectors unit-norm / deterministic / differs-by-input; Gemma template) and axum integration smoke.

## 1.0-SNAPSHOT — Java 25 / Spring Boot 4 migration

This release is the landing point of the `migrate-complete` branch. The repository
was converted from the legacy Spark / Couchbase / Jetty codebase to a single
Spring Boot 4 application on Java 25.

### Added

- **Spring Boot 4 application shell.** `SocialGraphApplication` with classic
  `@SpringBootApplication` wiring; server port `4567` (preserved from the legacy
  Spark layout).
- **Stateless Bearer-token auth** via `TokenAuthenticationFilter` and
  `SecurityConfig` — Redis-backed token store at key `tokens:<token>` with
  configurable TTL (`app.security.token-expiration-seconds`, default 86400s).
- **Pluggable object storage** behind `ObjectStorageService`:
  - `AzureObjectStorageService` — Azure Blob Storage with SAS upload URLs
  - `GcpObjectStorageService` — Google Cloud Storage with V4 signed URLs
  - `OciObjectStorageService` — OCI Object Storage with pre-authenticated requests
  - Active provider selected by `storage.provider` (`azure` | `gcp` | `oci`).
- **Controllers.** `AuthController`, `UserController`, `ActionController`,
  `StatusController`, `TimelineController`, `StorageController` — 51 total
  endpoints, all under `/api`.
- **Services.** `UserService`, `SessionService`, `ShareService`, `TimelineService`,
  `ActionService`, `DeviceService`, plus storage services. Everything persists to
  Redis via `StringRedisTemplate`.
- **Timeline delivery** — `ShareService.pushGraph` fan-out writes posts to each
  follower's FIFO list, personal-importance zset, and everyone-importance zset,
  honoring block / mute / negative-keyword / blocked-image filters.
- **Image pipeline.** `ImagePayloads` detects JPEG / PNG / WebP from magic bytes and
  parses base64 data URLs. `LiquidRescaler` performs seam-carving width reduction;
  `POST /api/lq/upload` runs rescale then upload in one call.
- **Typed exception hierarchy** + `GlobalExceptionHandler` with stable error codes
  (`cannot_register`, `invalid_grant`, `user_not_found`, `cannot_follow`,
  `cannot_unfollow`, `cannot_perform_action`, `auth_error`, `access_denied`,
  `incomplete_request`, `invalid_request_body`, `unsupported_image_type`,
  `media_upload_failed`, `storage_unavailable`, `internal_server_error`).
- **Argon2 password hashing** via `PasswordHash.createArgon2Hash` /
  `validateArgon2Hash` (legacy PBKDF2 helpers retained for read-side compatibility).

### Preserved

- `/api/session` — the public RSA key-exchange bootstrap endpoint, reimplemented
  on top of Redis-stored session keypairs (`session:<uuid>`, TTL 1 day).
- `/api/ping` — public health check, returns the string `"hello"`.

### Removed

- **Spark framework.** All Spark route handlers and the Spark runtime dependency.
- **Couchbase storage.** Post, user, and session state now live in Redis.
- **Legacy advanced-auth helpers.** `/api/aes/key`, `/api/get/image`, and related
  crypto-helper routes were **intentionally not migrated** — `ApiSurfaceRegressionTest`
  enforces that these routes do not exist. Consumers depending on them must be
  updated.
- **`.idea/` IntelliJ library XMLs** pinned to legacy Maven dependencies.

### Breaking changes

- **HTTP clients must now send `Authorization: Bearer <token>`.** The legacy query-string
  token auth scheme is gone; `TokenAuthenticationFilter` reads the `Authorization`
  header only.
- **Response shapes are standardized.**
  - Timeline responses use `entities` (not legacy `activity`); each entry carries
    `actorUid`, `actorUsername`, `actorFullname` (not a nested `actor` block).
  - Member-list responses use `setType` and `members` (not legacy `followers`).
  - Action responses use `actionType` (plural form: `likes`, `loves`, `favs`,
    `shares`) and `actors`.
  - Errors are always `{ "error": "<code>", "error_description": "<message>" }`.
- **Java 25 / Spring Boot 4 required.** Earlier JDKs will not compile the project.

### Verification

- `./mvnw test` — `ApiSurfaceRegressionTest` asserts the presence of all migrated
  endpoints and the absence of the legacy routes.
