# Configuration

Every setting SocialGraph honors, documented from
`[src/main/resources/application.yml](../src/main/resources/application.yml)`,
`[AppProperties](../src/main/java/com/intelligenta/socialgraph/config/AppProperties.java)`,
and
`[StorageProperties](../src/main/java/com/intelligenta/socialgraph/config/StorageProperties.java)`.

Environment variables override `application.yml` defaults. Everything below is the
name used at runtime (not the YAML path) unless otherwise noted.

## Server


| Variable      | Default | Purpose                                                                                            |
| ------------- | ------- | -------------------------------------------------------------------------------------------------- |
| `SERVER_PORT` | `4567`  | Spring Boot standard env var. The YAML sets `server.port: 4567` to preserve the legacy Spark port. |


## Persistence provider

SocialGraph supports two backends. `PERSISTENCE_PROVIDER` picks between them;
see [persistence.md](persistence.md) for the full topology / trade-off guide
and [infinispan-schema.md](internals/infinispan-schema.md) for the
Infinispan-native cache layout.

| Variable | Default | Purpose |
| --- | --- | --- |
| `PERSISTENCE_PROVIDER` | `redis` | `redis` (default, Redis Stack) or `infinispan` |
| `INFINISPAN_CLIENT_MODE` | `resp` | `resp` (Lettuce → Infinispan RESP endpoint, no service refactor) or `native` (HotRod + embedded cache manager) |
| `INFINISPAN_CLUSTER_NAME` | `socialgraph` | JGroups cluster name (reserved for the cluster-join follow-up) |
| `INFINISPAN_HOTROD_SERVERS` | `localhost:11222` | Comma-separated `host:port` list; used by `RemoteCacheManager` in native mode |
| `INFINISPAN_HOTROD_USERNAME` | *(empty)* | HotRod SASL username (native mode) |
| `INFINISPAN_HOTROD_PASSWORD` | *(empty)* | HotRod SASL password (native mode) |
| `INFINISPAN_HOTROD_SASL_MECHANISM` | `DIGEST-MD5` | HotRod SASL mechanism (`DIGEST-MD5`, `PLAIN`, `SCRAM-SHA-512`, …) |
| `INFINISPAN_HOTROD_SASL_REALM` | `default` | HotRod server realm |
| `INFINISPAN_RESP_HOST` | `localhost` | RESP endpoint host (RESP mode) |
| `INFINISPAN_RESP_PORT` | `11222` | RESP endpoint port |
| `INFINISPAN_RESP_USERNAME` | *(empty)* | Forwarded to `spring.data.redis.username` in RESP mode |
| `INFINISPAN_RESP_PASSWORD` | *(empty)* | Forwarded to `spring.data.redis.password` in RESP mode |
| `INFINISPAN_EMBEDDED_CONFIG` | `infinispan-embedded.xml` | Reserved for XML-driven configuration; programmatic setup is used today |
| `INFINISPAN_EPHEMERAL_TTL` | `PT24H` | ISO-8601 `Duration`; TTL of `tokens` / `sessions` / `activations` caches |
| `INFINISPAN_TRANSACTIONAL_DEFAULT` | `false` | Reserved — switches native impls to transactional caches when the JTA upgrade lands |
| `INFINISPAN_JGROUPS_STACK` | `jgroups-tcp.xml` | Reserved — JGroups stack file |
| `INFINISPAN_JGROUPS_INITIAL_HOSTS` | *(empty)* | Reserved — TCPPING initial-hosts list |

`PERSISTENCE_PROVIDER=redis` with `INFINISPAN_CLIENT_MODE=resp` (the default)
is equivalent to omitting the Infinispan block entirely.

## Redis

Redis holds all social state (users, posts, tokens, timelines, reactions, blocks,
mutes, counters, etc.) under `persistence.provider=redis`. **Redis Stack 7.2+**
is required — the `search` module is used by the vector-search endpoints. The
project's `docker-compose.yml` brings up `redis/redis-stack-server:7.4.0-v0`;
the plain `redis` image does not work.

When `persistence.provider=infinispan` and `client-mode=resp`, the same
`spring.data.redis.*` properties still drive Lettuce, but an
`EnvironmentPostProcessor` (`PersistenceEnvironmentPostProcessor`) rewrites
the host / port / username / password from the `INFINISPAN_RESP_*` values at
boot time so the app talks to the Infinispan RESP endpoint instead.


| Variable         | Default     | Purpose                          |
| ---------------- | ----------- | -------------------------------- |
| `REDIS_HOST`     | `localhost` | Redis hostname                   |
| `REDIS_PORT`     | `6379`      | Redis port                       |
| `REDIS_PASSWORD` | *(empty)*   | Redis AUTH password, if required |


Pooling uses Lettuce with these fixed values (not currently env-overridable):

- `max-active: 8`
- `max-idle: 8`
- `min-idle: 0`

Override by editing `application.yml` directly if you need different pool sizes.

## Mail

Spring Boot's `spring-boot-starter-mail` is on the classpath but the current code
does not call it — activation delivery is a TODO. The mail block is wired up so
activation emails can be added without reshaping config.


| Variable        | Default     | Purpose       |
| --------------- | ----------- | ------------- |
| `MAIL_HOST`     | `localhost` | SMTP host     |
| `MAIL_PORT`     | `587`       | SMTP port     |
| `MAIL_USERNAME` | *(empty)*   | SMTP user     |
| `MAIL_PASSWORD` | *(empty)*   | SMTP password |


`spring.mail.properties.mail.smtp.auth` and `starttls.enable` default to `false`;
adjust in `application.yml` for production SMTP.

## Object storage (shared)

Selects the active `ObjectStorageService` bean and governs signed-URL TTL and
object naming.


| Variable                         | Default   | Purpose                                                                                            |
| -------------------------------- | --------- | -------------------------------------------------------------------------------------------------- |
| `STORAGE_PROVIDER`               | `azure`   | One of `azure`, `gcp`, `oci`. The matching service bean is activated via `@ConditionalOnProperty`. |
| `STORAGE_SIGNED_URL_TTL_SECONDS` | `300`     | TTL for signed upload URLs / SAS tokens / OCI pre-authenticated requests.                          |
| `STORAGE_OBJECT_KEY_PREFIX`      | *(empty)* | Optional prefix prepended to every generated object key. A trailing `/` is inferred if missing.    |


Object keys are always `<prefix>/<uuid><extension>` — see
`[AbstractObjectStorageService.nextObjectKey](../src/main/java/com/intelligenta/socialgraph/service/storage/AbstractObjectStorageService.java)`.

## Azure Blob Storage

Active when `STORAGE_PROVIDER=azure` (default).


| Variable                          | Default         | Purpose                                                                          |
| --------------------------------- | --------------- | -------------------------------------------------------------------------------- |
| `AZURE_STORAGE_CONNECTION_STRING` | *(placeholder)* | Full Azure connection string. Used by the SDK to create the `BlobServiceClient`. |
| `AZURE_STORAGE_ACCOUNT_NAME`      | `photoblobs1`   | Preserved from the legacy deployment; consumed by operators, not the SDK.        |
| `AZURE_STORAGE_ACCOUNT_KEY`       | *(placeholder)* | Same — present for operational parity.                                           |
| `AZURE_STORAGE_CONTAINER`         | `photos`        | Container the service creates on startup if absent.                              |


> Only `AZURE_STORAGE_CONNECTION_STRING` and `AZURE_STORAGE_CONTAINER` are required
> for the client to function. The connection string contains the account name and
> key; the two standalone fields are kept for operators who already wire them into
> their deployment pipeline.

## Google Cloud Storage

Active when `STORAGE_PROVIDER=gcp`.


| Variable             | Default   | Purpose                                                    |
| -------------------- | --------- | ---------------------------------------------------------- |
| `GCP_PROJECT_ID`     | *(empty)* | Optional; passes through to `StorageOptions.setProjectId`. |
| `GCP_STORAGE_BUCKET` | *(empty)* | **Required.** Target bucket for uploads and signed URLs.   |


Credentials come from Application Default Credentials — set
`GOOGLE_APPLICATION_CREDENTIALS` to a service-account JSON file or run inside an
environment with workload identity. Signed URLs are V4.

## Oracle Cloud Infrastructure Object Storage

Active when `STORAGE_PROVIDER=oci`.


| Variable                       | Default   | Purpose                                                                                    |
| ------------------------------ | --------- | ------------------------------------------------------------------------------------------ |
| `OCI_OBJECT_STORAGE_NAMESPACE` | *(empty)* | **Required.** Object Storage namespace.                                                    |
| `OCI_OBJECT_STORAGE_BUCKET`    | *(empty)* | **Required.** Bucket name.                                                                 |
| `OCI_REGION`                   | *(empty)* | OCI region ID (e.g. `us-ashburn-1`). Used if `OCI_OBJECT_STORAGE_ENDPOINT` is not set.     |
| `OCI_CONFIG_FILE`              | *(empty)* | Path to an OCI config file. If empty, the SDK uses its default location (`~/.oci/config`). |
| `OCI_PROFILE`                  | `DEFAULT` | Profile within the config file.                                                            |
| `OCI_OBJECT_STORAGE_ENDPOINT`  | *(empty)* | Explicit endpoint URL; overrides `OCI_REGION` if both are set.                             |


Upload targets are created via Pre-Authenticated Request (PAR) with
`ObjectWrite` access; see `OciObjectStorageService.createSignedUploadTarget`.

## Application

These live under the `app.`* YAML prefix and are bound to
`[AppProperties](../src/main/java/com/intelligenta/socialgraph/config/AppProperties.java)`.


| YAML path                               | Default                                                               | Purpose                                                                                                                                                                                                         |
| --------------------------------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `app.security.token-expiration-seconds` | `86400`                                                               | TTL (seconds) for every issued Bearer token. Applied to the `tokens:<token>` Redis key at write time.                                                                                                           |
| `app.public-endpoints`                  | `[/api/login, /api/register, /api/ping, /api/session, /api/activate]` | Reference list of public endpoints. `SecurityConfig` does **not** read this list at runtime — it hardcodes the same set in the filter chain. The YAML list is present for documentation and future refactoring. |


## AI providers (Spring AI)

Each AI capability selects a provider independently. Providers available in
Spring AI 2.0.0-M4 are bundled as optional deps; the one whose
`ai.<capability>.provider` is set + whose credentials are supplied
autoconfigures its beans.

| Variable | Default | Purpose |
|---|---|---|
| `AI_EMBEDDING_PROVIDER` | `sidecar` | `sidecar`, `none`, or a Spring AI provider key |
| `AI_EMBEDDING_MODEL` | (catalog default) | Override the model, e.g. `text-embedding-3-large` |
| `AI_EMBEDDING_DIMENSIONS` | (catalog default) | Override vector dim (OpenAI supports truncation) |
| `AI_CHAT_PROVIDER` | `sidecar` | Chat provider for the visual summary |
| `AI_CHAT_MODEL` | (catalog default) | Chat model override |
| `AI_CHAT_TEMPERATURE` | (provider default) | |
| `AI_CHAT_MAX_TOKENS` | (provider default) | |
| `AI_IMAGE_PROVIDER` | `none` | Image-generation provider for `POST /api/images/generate` |
| `AI_IMAGE_MODEL` | (catalog default) | Image model override |
| `AI_MODERATION_PROVIDER` | `none` | Moderation provider (OpenAI or Mistral) |
| `AI_MODERATION_MODEL` | (catalog default) | Moderation model override |

Provider-native configuration (API keys, endpoints, project IDs) flows through
unchanged from Spring AI's own namespace — `OPENAI_API_KEY`,
`SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_AZURE_OPENAI_ENDPOINT`,
`SPRING_AI_VERTEX_AI_GEMINI_PROJECT_ID`, etc.

Full provider table, defaults, and examples:
[docs/ai.md](ai.md).

## Vector embedding pipeline

The `embedding.*` block in `application.yml` configures the sidecar URL,
timeouts, dimensions, multi-image limits, and search-window. Every field is
environment-overridable (uppercase with hyphens turned into underscores, e.g.
`embedding.sidecar-url` -> `EMBEDDING_SIDECAR_URL`).

| Variable | Default | Purpose |
|---|---|---|
| `EMBEDDING_SIDECAR_URL` | `http://localhost:8000` | Base URL of the Rust sidecar |
| `EMBEDDING_SUMMARIZE_TIMEOUT_MS` | `30000` | Read timeout for `/summarize` (Gemma VLM can be slow) |
| `EMBEDDING_EMBED_TIMEOUT_MS` | `15000` | Read timeout for `/embed/*` |
| `EMBEDDING_VECTOR_DIM` | `1152` | SigLIP-2 giant projection dim. Changing this requires dropping and recreating `idx:post:embedding` |
| `EMBEDDING_MAX_IMAGES_PER_POST` | `10` | Hard cap enforced at `POST /api/status` for multipart uploads |
| `EMBEDDING_IMAGES_FOR_EMBEDDING` | `5` | First N images passed to Gemma for the visual summary |
| `EMBEDDING_SEARCH_WINDOW_DAYS` | `7` | Only posts created within this many days are returned by `/api/search/*` |
| `EMBEDDING_DLQ_MAX_RETRIES` | `3` | EmbeddingWorker retries a failing message this many times before DLQing |
| `EMBEDDING_EMBEDDING_TTL_SECONDS` | `691200` (8 d) | TTL on `embedding:post:<id>` — must be ≥ `search-window-days` |
| `EMBEDDING_SEARCH_LIMIT_DEFAULT` | `20` | Default `limit` when missing from the request body |
| `EMBEDDING_SEARCH_LIMIT_MAX` | `100` | Hard cap on requested `limit` |

The Rust sidecar reads its own env vars — see
[`embedding-sidecar/README.md`](../embedding-sidecar/README.md).

## Logging

```yaml
logging:
  level:
    root: INFO
    org.springframework.security: DEBUG
```

- `com.intelligenta.socialgraph` to get application debug logs.
- Spring Security debug logging is verbose and intentional — turn it off in
production.

## Modifying config

Three ways to set any value:

1. **Environment variables** — highest priority for the names listed above. Best
  for container deployments.
2. `**application.yml`** — defaults committed to the repo.
3. **Spring Boot property overrides** on the command line, e.g.
  `./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"`.

Any Spring Boot property (`server.port`, `logging.level.`*, etc.) works via the
standard Boot precedence rules.