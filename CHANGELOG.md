# Changelog

Append-only history of meaningful changes to SocialGraph. Entries are grouped by
release. Unreleased work sits at the top.

## [Unreleased]

### Changed

- **Initial deep documentation pass.** Added the `docs/` tree (getting started,
  configuration, architecture, authentication, per-controller API reference, Redis
  internals, storage providers, image pipeline, testing, agent skills), a new
  top-level README that matches the current Spring Boot product, `llms.txt` and
  `llms-full.txt` for LLM consumers, this changelog, and repo-local agent skills
  under `.claude/skills/`. No behavior changes.

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
