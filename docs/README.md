# SocialGraph documentation

This is the index for the full documentation set. Each section below describes what
the page covers so you can jump straight to the part you need.

## Getting started

- [**Getting started**](getting-started.md) — install prerequisites (Java 25, Maven
  wrapper, Redis, a storage provider), run the app, run the tests, smoke-test the
  API end to end.
- [**Configuration**](configuration.md) — every `application.yml` field and every
  environment variable, grouped by subsystem (server, Redis, mail, storage, app).

## How the system works

- [**Architecture**](architecture.md) — layered view of the application (controllers
  → services → persistence + object storage), with Mermaid diagrams for the runtime
  shape and the request lifecycle.
- [**Persistence**](persistence.md) — choosing between Redis (default) and
  Infinispan (RESP-compat or native HotRod), topology diagrams, env vars, and
  operational trade-offs.
- [**Authentication**](authentication.md) — stateless Bearer-token flow, public vs.
  authenticated endpoints, `TokenAuthenticationFilter` behavior, how
  `@AuthenticationPrincipal` resolves to `AuthenticatedUser`.

## API reference

See [**API reference index**](api/README.md). Each page documents exact method,
path, auth requirement, request parameters, response shape, and error cases for one
controller:

- [Auth endpoints](api/auth.md) — `/api/login`, `/api/register`, `/api/activate`,
  `/api/session`, `/api/ping`
- [User & graph endpoints](api/users.md) — follow, unfollow, block, mute, profile,
  search, RSA public keys, member lists
- [Action endpoints](api/actions.md) — like, love, fav, share (plus reverse
  operations and actor listings)
- [Status / post endpoints](api/status.md) — post, read, reply, reshare, edit,
  delete, devices, content filters
- [Timeline endpoints](api/timeline.md) — FIFO, personal, everyone
- [Storage endpoints](api/storage.md) — signed upload target, liquid-rescale upload
- [Error codes](api/errors.md) — the complete mapping from typed exception to HTTP
  status and JSON `error` code

## Internals

Documentation for maintainers and contributors:

- [**Persistence abstraction**](internals/persistence-abstraction.md) — the 12
  store interfaces, the Redis / Infinispan dual implementations, and how
  `@ConditionalOnProperty` wiring picks one per run.
- [**Redis schema**](internals/redis-schema.md) — every key and hash field the
  Redis adapter (including RESP-compat mode) touches, who reads and who
  writes it.
- [**Infinispan schema**](internals/infinispan-schema.md) — cache-by-cache
  layout under `persistence.provider=infinispan` / `client-mode=native`,
  plus the feature-gap matrix vs. the Redis adapter.
- [**Timeline delivery**](internals/timeline-delivery.md) — the `pushGraph` fan-out
  algorithm, the filters it applies, and the three timeline representations
  (FIFO list, personal zset, everyone zset), with a Mermaid sequence diagram.
- [**Storage providers**](internals/storage-providers.md) — the
  `ObjectStorageService` contract, how the active provider is selected at startup,
  and the Azure / GCP / OCI implementations.
- [**Image pipeline**](internals/image-pipeline.md) — `ImagePayloads` MIME detection,
  `LiquidRescaler` seam carving, how `POST /api/lq/upload` wires them together.
- [**Security filter**](internals/security-filter.md) — `TokenAuthenticationFilter`
  extraction, Redis lookups, `polyCount` side effect, and why the filter ordering
  matters.

## Testing

- [**Testing**](testing.md) — the layout of `src/test/`, how
  `ApiSurfaceRegressionTest` fixes the public surface, how controller tests inject
  authenticated users via `TestAuthenticatedUserResolver`, and how to run the full
  suite or a single class.

## Agent-oriented documentation

- [**Agent skills index**](agent-skills.md) — what each repo-local skill under
  `.claude/skills/` does and when to invoke it.
- [`../llms.txt`](../llms.txt) — compact LLM index pointing at this documentation.
- [`../llms-full.txt`](../llms-full.txt) — single-file ingestion bundle.

## History

- [**Changelog**](../CHANGELOG.md) — append-only history, including the Java 25 /
  Spring Boot 4 migration and its breaking changes.
