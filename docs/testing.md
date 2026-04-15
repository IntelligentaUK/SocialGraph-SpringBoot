# Testing

The verification gate for the project is:

```bash
./mvnw test
```

Everything on the test tree lives under
[`src/test/java/com/intelligenta/socialgraph/`](../src/test/java/com/intelligenta/socialgraph/).

## Test layout

```
src/test/java/com/intelligenta/socialgraph/
  ApiSurfaceRegressionTest.java     ← fixes the full public HTTP surface
  CoreUtilitiesTest.java            ← utility + config contracts
  config/
    SecurityConfigTest.java         ← public vs protected route matrix
  security/
    TokenAuthenticationFilterTest.java
  controller/
    AuthControllerTest.java
    UserControllerTest.java
    ActionControllerTest.java
    StatusControllerTest.java
    TimelineControllerTest.java
    StorageControllerTest.java
  service/
    UserServiceTest.java
    SessionServiceTest.java
    ShareServiceTest.java
    TimelineServiceTest.java
    ActionServiceTest.java
    DeviceServiceTest.java
    storage/
      ObjectStorageServicesTest.java
  util/
    ImagePayloadsTest.java
  support/
    TestAuthenticatedUserResolver.java
    TestRequestPostProcessors.java
```

## The regression gate: `ApiSurfaceRegressionTest`

This is the single most important test in the project. It asserts that:

- **All 51 endpoints** from the API reference exist (same method and path).
- **The legacy routes** `/api/aes/key` and `/api/get/image` do **not** exist —
  they were intentionally not migrated.
- **Response shapes** use the new field names:
  - `ActionResponse` → `actionType`, `actors` (not legacy `likes`).
  - `MembersResponse` → `setType`, `members` (not legacy `followers`).
  - `TimelineResponse` → `entities`; each entry uses `actorUid` / `actorUsername`
    (not legacy `actor` / `activity`).

If you add an endpoint, add it here. If you remove one, remove it here. If the
test starts failing, you have made an accidental breaking change to the public
surface.

See
[`ApiSurfaceRegressionTest.java`](../src/test/java/com/intelligenta/socialgraph/ApiSurfaceRegressionTest.java).

## Controller slice tests

Each controller has a dedicated test class (`<Name>ControllerTest`) that stands
up a `MockMvc` instance with **only that controller** and the custom argument
resolver that injects `@AuthenticationPrincipal AuthenticatedUser`. These tests
cover:

- Happy path: one test per endpoint asserts `200 OK` and the key response
  fields the DTO serializes.
- Error path: a few key failure modes per controller (e.g. missing required
  params, duplicate follow, unknown post).
- Principal injection: every authenticated route test uses
  `TestRequestPostProcessors.authenticatedUser(uid)` to attach a pre-resolved
  principal to the request.

**Important:** controller tests use `standaloneSetup`, which **does not run the
Spring Security filter chain.** This means:

- `TokenAuthenticationFilter` is **not** exercised by these tests — use
  `TokenAuthenticationFilterTest` for that.
- Public vs protected routing is **not** enforced at this layer — use
  `SecurityConfigTest` for that.
- The `@AuthenticationPrincipal` argument is resolved by
  `TestAuthenticatedUserResolver`, not by the filter.

## Security and auth tests

[`SecurityConfigTest`](../src/test/java/com/intelligenta/socialgraph/config/SecurityConfigTest.java)
stands up a real `SecurityFilterChain` and asserts:

- `/api/ping`, `/api/session` are public (accessible without a Bearer token).
- Protected endpoints return `401` without a token.
- A valid Bearer token (via mocked Redis) lets protected endpoints through.
- Legacy routes `/api/aes/key` return `403` without auth and `404` with auth —
  i.e. they are protected by the filter chain but have no controller handler.

[`TokenAuthenticationFilterTest`](../src/test/java/com/intelligenta/socialgraph/security/TokenAuthenticationFilterTest.java)
unit-tests the filter in isolation:

- Missing `Authorization` header → `SecurityContext` stays empty.
- Malformed (non-`Bearer`) header → context stays empty.
- Valid token → principal installed; `polyCount` incremented.
- Unknown token → context stays empty, no exception.
- Redis throwing an exception → logged, context empty, request proceeds.

## Service tests

One class per service. These are plain JUnit tests against mocked
`StringRedisTemplate` and collaborators. They cover the shape of each method's
Redis interactions (which keys are read, which are written, with what arguments).

For `UserService` the covered behaviors include:
- register → creates the correct Redis layout and returns the expected
  `AuthResponse`.
- login → salt + argon2 validation.
- follow / unfollow / block / mute → correct set writes and counter bumps.
- `canViewContent`, `hasBlocked`, `hasMuted`, `isImageBlocked`, `hasNegativeKeyword`.

For `ShareService` / `TimelineService`: `pushGraph` fan-out, filter evaluation,
and timeline read-time filtering.

For `ActionService`: perform / reverse / list per verb, plus the "already done"
and "not done" branches.

For `ObjectStorageServicesTest`: object-key prefix handling, `StoredObject` /
`StorageUploadTarget` construction via the abstract base class (provider
clients are mocked).

## Utility tests

[`CoreUtilitiesTest`](../src/test/java/com/intelligenta/socialgraph/CoreUtilitiesTest.java)
covers the small utilities: UUID generation, MD5, unix time, Argon2 /
PBKDF2 hashing, DTO JSON serialization shape (especially the snake_case
`expires_in`, `activation_token`, `error_description` fields and the
`@type: "person"` field on `ActionActor`), `AppProperties` defaults, and
`RedisConfig` bean wiring.

[`ImagePayloadsTest`](../src/test/java/com/intelligenta/socialgraph/util/ImagePayloadsTest.java)
covers MIME detection (magic bytes win over content-type hints), data URL
parsing, and alias normalization.

## Test support

`TestAuthenticatedUserResolver` is a
`HandlerMethodArgumentResolver` that resolves any parameter of type
`AuthenticatedUser` by reading `UID_ATTRIBUTE` / `USERNAME_ATTRIBUTE` off the
request and constructing a new `AuthenticatedUser(uid, username)`.

`TestRequestPostProcessors.authenticatedUser(uid)` /
`authenticatedUser(uid, username)` returns a Spring `RequestPostProcessor` that
sets those attributes on the incoming request before the controller method
runs. Controller tests register the resolver on their `MockMvc` builder with
`.setCustomArgumentResolvers(new TestAuthenticatedUserResolver())`.

This two-piece pattern lets controller tests inject an authenticated principal
without pulling in the full Spring Security filter chain.

## Running a single test

```bash
./mvnw test -Dtest=UserControllerTest
./mvnw test -Dtest=ApiSurfaceRegressionTest
./mvnw test -Dtest='UserServiceTest#registerCreatesRedisLayout'
```

Maven's failsafe / surefire runners honor the standard `-Dtest=...` selector.

## Adding tests

Rules of thumb:

1. **Public surface changes** — always update `ApiSurfaceRegressionTest` in the
   same commit.
2. **Controller behavior changes** — add a test to the matching
   `<Name>ControllerTest`.
3. **Redis key changes** — add a service-level test to assert the new key shape.
   Then update [Redis schema](internals/redis-schema.md).
4. **Error codes** — if you add a new exception + code, add a handler test case
   in `GlobalExceptionHandlerTest` (add the file if it doesn't exist) and a row
   to [API errors](api/errors.md).

## Related

- [API reference](api/README.md) — what the controller tests verify at the HTTP
  layer.
- [Authentication](authentication.md) — what the security tests verify.
- [Redis schema](internals/redis-schema.md) — ground truth that service tests
  should match.
