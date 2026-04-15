---
name: api-endpoint-work
description: Use when adding, removing, or modifying any /api/... HTTP endpoint, controller method, request / response DTO, or error code in SocialGraph
when-to-use: Any change that touches src/main/java/com/intelligenta/socialgraph/controller/, exception/, or model/, or that adds or removes a row from the public surface
---

# API endpoint work

The SocialGraph public HTTP surface is locked down by `ApiSurfaceRegressionTest`
and documented endpoint-by-endpoint under `docs/api/`. Drifting any of these
without the others is the most common way to ship a silent breakage.

Use this skill **before** you start editing.

## Triggers

Invoke this skill if your change touches any of:

- `src/main/java/com/intelligenta/socialgraph/controller/*.java`
- `src/main/java/com/intelligenta/socialgraph/model/*.java` (request / response DTOs)
- `src/main/java/com/intelligenta/socialgraph/exception/*.java`
- `src/main/java/com/intelligenta/socialgraph/exception/GlobalExceptionHandler.java`
- The `SecurityConfig` public-endpoint allowlist
- Any `@RequestMapping` / `@GetMapping` / `@PostMapping` / `@PatchMapping` / `@DeleteMapping` annotation

## Decision tree

1. **Are you adding or removing an endpoint?**
   → Yes: full checklist below. **`ApiSurfaceRegressionTest` will fail until you
   update it.**
   → No: skip to step 2.

2. **Are you changing a response DTO field name, removing a field, or changing
   its type?**
   → Yes: this is a **breaking change**. It needs a changelog entry, an updated
   API doc page, and probably a controller test update.
   → No: continue.

3. **Are you adding a new error code or changing the HTTP status of an existing one?**
   → Yes: update `docs/api/errors.md` and the matching exception class. Touch
   `GlobalExceptionHandler` only if you added a new exception type.
   → No: continue.

4. **Are you only changing internal logic that doesn't affect the wire?**
   → Run the matching `<Name>ControllerTest` to confirm and you're done.

## Add an endpoint — checklist

- [ ] Add the method to the matching controller. Use the existing constructor
      injection style (no field injection).
- [ ] Use `@AuthenticationPrincipal AuthenticatedUser user` if the endpoint is
      authenticated. Do **not** read the security context manually.
- [ ] If the endpoint must be public, add it to **both**:
  - `SecurityConfig.filterChain` `requestMatchers(...).permitAll()`
  - `app.public-endpoints` in `application.yml` (documentation only, but kept in sync)
- [ ] Add or update the request / response DTO under `model/`. Prefer
      `record` for immutable types. Use `@JsonProperty` / `@JsonInclude`
      annotations only when the wire name needs to differ from the field name.
- [ ] If the input has constraints, annotate the record / DTO with
      `jakarta.validation` annotations and use `@Valid` on the controller param.
      Validation failures surface as `incomplete_request`.
- [ ] Add a happy-path test to `controller/<Name>ControllerTest`. Use
      `TestRequestPostProcessors.authenticatedUser(uid)` to inject a principal.
- [ ] **Add the new endpoint to `ApiSurfaceRegressionTest`** in the matching
      method group (legacy or new). The test fails build until this is done.
- [ ] Document the endpoint on the matching `docs/api/<controller>.md` page —
      method, path, auth, params, response shape, errors. Match the existing
      style on that page.
- [ ] Update `docs/api/README.md` if the new path doesn't fit an existing
      entry in the "Full endpoint list" section.
- [ ] If errors are new, add rows to `docs/api/errors.md`.
- [ ] Add an entry to `CHANGELOG.md` under `[Unreleased]`.

## Remove an endpoint — checklist

- [ ] Delete the controller method.
- [ ] Delete the matching `<Name>ControllerTest` test.
- [ ] **Remove the path from `ApiSurfaceRegressionTest`** (it will hold the test
      green only if the path is also absent from the route map). Add it to the
      "must NOT exist" list if removal is intentional and load-bearing.
- [ ] Delete or update the matching section in `docs/api/<controller>.md`.
- [ ] Update `docs/api/README.md` "Full endpoint list".
- [ ] If the endpoint was the only consumer of a DTO, delete the DTO too.
- [ ] Add a **breaking-change** entry to `CHANGELOG.md`.

## Modify a response shape — checklist

- [ ] Update the DTO.
- [ ] Update the controller test assertion for the changed field.
- [ ] If the field is asserted by `ApiSurfaceRegressionTest` (likely true for
      `entities`, `setType`, `actorUid`, `actorUsername`, `actionType`, `count`,
      `duration`), update that test too.
- [ ] Update the response shape on the matching `docs/api/<controller>.md`.
- [ ] Add a **breaking-change** entry to `CHANGELOG.md`.

## What not to do

- **Do not bypass `@AuthenticationPrincipal`.** The
  `TestAuthenticatedUserResolver` will silently fail to inject the user and your
  test will pass against a `null` principal.
- **Do not return raw `Map<String, Object>` for new endpoints.** The legacy
  `Map`-returning endpoints are kept for compatibility; new endpoints should
  return a typed DTO so future readers can find the shape.
- **Do not add a new error response shape.** All errors must come out as
  `ErrorResponse{error, error_description}`. If you need extra fields, raise
  a new exception type and let `GlobalExceptionHandler` map it.
- **Do not add a query-string token parameter.** Auth is `Authorization: Bearer`
  only.

## Canonical references

- [`docs/api/README.md`](../../../docs/api/README.md)
- [`docs/api/errors.md`](../../../docs/api/errors.md)
- [`docs/authentication.md`](../../../docs/authentication.md)
- [`docs/testing.md`](../../../docs/testing.md)
- [`src/test/java/com/intelligenta/socialgraph/ApiSurfaceRegressionTest.java`](../../../src/test/java/com/intelligenta/socialgraph/ApiSurfaceRegressionTest.java)
