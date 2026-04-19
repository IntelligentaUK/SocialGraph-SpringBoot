# Changelog

Append-only history of meaningful changes to SocialGraph. Entries are grouped by
release. Unreleased work sits at the top.

## [Unreleased]

### Added

- **Infinispan persistence foundation (phase I-A)** — dormant-by-default
  Infinispan support alongside Redis. `persistence.provider` selects the
  backend (`redis` default, `infinispan` alternative); when Infinispan is
  chosen, `persistence.infinispan.client-mode` picks between `resp` (existing
  Lettuce client against Infinispan's RESP endpoint, no service refactor) and
  `native` (HotRod + embedded cache manager for transactional caches,
  `CounterManager`, clustered listeners, and Ickle queries). This phase adds
  the Infinispan BOM, optional dependencies (`infinispan-core`,
  `infinispan-client-hotrod`, `infinispan-query`, `infinispan-commons`),
  `PersistenceProperties`, and the full `persistence.*` YAML block. No
  runtime behaviour changes — Redis remains the default and the build is
  byte-identical when `PERSISTENCE_PROVIDER` is unset. `awaitility:4.2.2`
  lands in test scope for the eventual-consistency assertions in later
  phases.
- **Infinispan RESP-compat mode (phase I-B)** — setting
  `PERSISTENCE_PROVIDER=infinispan INFINISPAN_CLIENT_MODE=resp` redirects the
  existing Lettuce client to Infinispan Server's RESP endpoint with no
  service refactor. A new `PersistenceEnvironmentPostProcessor` rewrites
  `spring.data.redis.host/port/username/password` from
  `persistence.infinispan.resp-*` before bean creation, so every service that
  injects `StringRedisTemplate` works unchanged. The RediSearch path
  (`redisSearchClient`, `redisSearchBinaryConnection`,
  `RedisSearchIndexInitializer`, `VectorSearchService`, `SearchController`)
  and the Streams path (`EmbeddingWorker`, `ShareService`'s XADD to
  `embedding:queue`) are gated on `persistence.provider=redis`; in Infinispan
  RESP mode those beans are absent and `/api/search/*` return 404 until I-I.
  Ships a `docker-compose` profile `infinispan-resp`, the server config at
  `docker/infinispan/infinispan-resp.xml` (RESP endpoint with
  `application/octet-stream` encoding), `InfinispanRespIntegrationTest` base
  class, and `SessionServiceInfinispanRespTest` proving a stock Redis-backed
  service round-trips through the Infinispan RESP endpoint unchanged.
- **Infinispan native-mode infrastructure (phase I-C)** — setting
  `INFINISPAN_CLIENT_MODE=native` activates `InfinispanConfig.Native`, which
  wires three beans: a HotRod `RemoteCacheManager` connected to
  `persistence.infinispan.hotrod-servers` (SASL auth via
  `hotrod-username/password` + `DIGEST-MD5` by default), a `CounterManager`
  derived from it, and an in-process `EmbeddedCacheManager` with pre-defined
  `tokens` / `sessions` / `activations` ephemeral caches plus cluster-tier
  caches (`users`, `relations`, `posts`, `reactions`, `timelines-*`, etc.)
  in LOCAL mode. JGroups replication between the embedded tier and the
  cluster tier is deferred to the next phase-refresh. Ships the `infinispan`
  docker-compose profile, `InfinispanHotRodIntegrationTest` base class, and
  `InfinispanNativeSmokeTest` proving the beans wire and a HotRod put/get
  round-trips.
- **Persistence abstraction layer (phase I-D)** — twelve per-bounded-context
  store interfaces under `com.intelligenta.socialgraph.persistence`:
  `TokenStore`, `SessionStore`, `UserStore`, `RelationStore`, `PostStore`,
  `ReactionStore`, `TimelineStore`, `DeviceStore`, `ContentFilterStore`,
  `CounterStore`, `EmbeddingIndexStore`, `EmbeddingQueue`. Each (except
  the two embedding stores) ships both a Redis implementation under
  `persistence.redis.*` (activated when `client-mode=resp` / provider=redis,
  preserving the current Redis key schema bit-for-bit — including the
  "unusual" `post:<id>likes:` reaction key shape that
  `ApiSurfaceRegressionTest` depends on) and an Infinispan implementation
  under `persistence.infinispan.*` (activated when `client-mode=native`,
  backed by the `EmbeddedCacheManager`'s pre-defined caches). `UserService`,
  `ShareService`, `TimelineService`, `ActionService`, `SessionService`,
  `DeviceService`, and `TokenAuthenticationFilter` were refactored to
  inject store interfaces instead of `StringRedisTemplate`. A `grep
  StringRedisTemplate` inside `service/` now returns only the few
  out-of-band lookups that migrate in subsequent phases (edge scores,
  social-importance sorted set, embedding-queue XADD).
- **Infinispan simple-store implementations (phase I-E)** —
  `InfinispanTokenStore` (embedded cache with entry TTL),
  `InfinispanSessionStore` (embedded cache with RSA key-pair values),
  `InfinispanDeviceStore` (cluster cache of per-user `HashSet<String>`), and
  `InfinispanContentFilterStore` (nested per-user map of keyword / image
  sets). All activated under
  `@ConditionalOnProperty(persistence.infinispan.client-mode=native)`.
- **Infinispan user + relations implementations (phase I-F)** —
  `InfinispanUserStore` stores user profile records in a `users` cache plus
  a `user-uid-index` reverse-lookup cache, issues activation + token
  atomically across the ephemeral and cluster tiers, and exposes
  increment-field semantics via local read-modify-write (pessimistic
  transaction promotion is the follow-up). `InfinispanRelationStore`
  mirrors the six-relation set semantics as a nested map cache.
- **Infinispan posts + reactions implementations (phase I-G)** —
  `InfinispanPostStore` coordinates the post hash + image list + per-user
  counter mutations via the `CounterStore` abstraction (atomic transactions
  via HotRod JTA are the follow-up refinement).
  `InfinispanReactionStore` maintains the list-of-actors + reverse-lookup
  under cleaner key shapes than the Redis "unusual" format.
- **Infinispan timelines + counters implementations (phase I-H)** —
  `InfinispanTimelineStore` stores a FIFO list plus two score maps per
  user, with ranked reads emulated via client-side sort (Ickle
  `ORDER BY score DESC LIMIT` on an indexed cache is the follow-up).
  `InfinispanCounterStore` maintains a bucket-per-kind map of user →
  count; strong-counter promotion for ACID increments is the follow-up.
- **Vector search + embedding queue abstractions (phases I-I / I-J)** —
  `EmbeddingIndexStore` and `EmbeddingQueue` interfaces added to the
  persistence package. Current RediSearch / Redis Streams paths in
  `VectorSearchService` and `EmbeddingWorker` remain the only shipped
  implementations; both continue to be gated on `persistence.provider=redis`
  (set in phase I-B). Infinispan-native implementations — Protobuf-indexed
  `PostEmbedding` entities with Ickle k-NN for search, and a
  `CounterManager`-sequenced cache + clustered listener for the queue —
  are deferred to a follow-up plan. When Infinispan is the selected
  provider, `/api/search/*` return 404 and the embedding pipeline is
  inactive (documented trade-off).
- **Test matrix (phase I-K)** — existing unit tests were rewritten to mock
  the store interfaces instead of `StringRedisTemplate`; integration tests
  gained `SessionServiceInfinispanRespTest` (RESP-compat end-to-end) and
  `InfinispanNativeSmokeTest` (native beans round-trip). All 139 tests
  pass across the Redis default, RESP-compat, and native modes.
- **Documentation (phase I-L)** — this rollup. A full topology /
  trade-off / provider-comparison document under `docs/persistence.md`
  is the next doc task; for now the canonical source is
  `PersistenceProperties` + `InfinispanConfig` Javadoc.
- **Audio and video post summarization** — `type=audio` and `type=video` posts
  now produce retrieval-oriented summaries alongside the existing image path.
  The Rust sidecar gains a second Gemma slot (`gemma_ev`, default
  `google/gemma-4-E4B-it`) behind `ENABLE_AUDIO_VIDEO_SUMMARY=true` exposing
  `POST /summarize/audio` and `POST /summarize/video`. Two new capabilities
  `ai.audio.provider` and `ai.video.provider` route to `sidecar` by default;
  cloud alternatives (Whisper, Gemini) resolve in `DefaultModelCatalog` with
  Spring-AI wiring pending a follow-up. Summaries are stored as
  `audio_summary` / `video_summary` Redis hash fields and concatenated into
  `text_vec` so audio/video posts are searchable via `/api/search/ai` without
  any new RediSearch index. `ShareService.shouldEmitEmbedding` no longer
  skips videos; `StatusController` accepts `type=audio` alongside `video`.
- **Multi-provider AI routing via Spring AI 2.0** — every shipped model starter
  in Spring AI 2.0.0-M4 is bundled (optional) and selectable per-capability via
  `ai.embedding.provider` / `ai.chat.provider` / `ai.image.provider` /
  `ai.moderation.provider`. Providers covered: OpenAI, Azure OpenAI, Anthropic,
  Google GenAI (Gemini), Vertex AI (Gemini + embeddings), Bedrock (Converse,
  Titan, Cohere), Ollama, Mistral AI, Stability AI, Zhipu AI, MiniMax, OCI
  GenAI, DeepSeek, PostgresML, local ONNX transformers — 18 total plus the
  existing Rust `sidecar` and a `none` no-op. The sidecar remains the default
  so no existing install changes behaviour.
- **Default model catalog** — `DefaultModelCatalog` hardcodes a recommended
  model + dimension for every (capability, provider) pair. Admins override any
  field via `ai.<capability>.model` / `dimensions` / `temperature` /
  `max-tokens`, and everything under `spring.ai.<provider>.*` continues to flow
  through unchanged.
- **Per-provider/dim RediSearch index** — `idx:post:embedding:<provider>:<dim>`
  replaces the single `idx:post:embedding`. Switching provider creates a new
  index; old entries expire via TTL (no data loss, no `FT.DROPINDEX` needed).
- **`POST /api/images/generate`** — generate images with the active provider
  (OpenAI DALL-E, Azure OpenAI DALL-E, Stability AI, Zhipu cogview). Returns
  503 when `ai.image.provider=none`.
- **Inline moderation hook** — `ShareService.createStatusUpdate` calls the
  active `ContentModerator` (OpenAI omni-mod or Mistral moderation) before the
  Redis transaction. Flagged content returns `400 content_blocked` with the
  triggered categories.
- **New docs** — [`docs/ai.md`](docs/ai.md) (provider table + config recipes)
  and [`docs/api/images.md`](docs/api/images.md).
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
