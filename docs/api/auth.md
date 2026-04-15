# API: auth

[`AuthController`](../../src/main/java/com/intelligenta/socialgraph/controller/AuthController.java)
owns account creation, credential exchange, account activation, session key
bootstrap, and the health-check ping. All five routes are public — they do not
require an `Authorization` header.

## `GET /api/ping`

Liveness check.

- **Auth:** public.
- **Params:** none.
- **Response:** `200 OK`, text body `hello`.

```bash
curl http://localhost:4567/api/ping
# hello
```

## `GET /api/session`

Fetches (or creates) a server RSA public key for a client-provided session UUID.
This is the legacy key-exchange bootstrap; it is the only advanced-auth helper
that survived the Spring Boot migration.

- **Auth:** public.
- **Params (query):**
  - `uuid` — optional. If omitted, the server generates one.
- **Response:** `200 OK`
  ```json
  {
    "uuid": "b8c4...-...",
    "response": { "pubKey": "MIIBIjANBgkqhki..." }
  }
  ```
  The `pubKey` is Base64-encoded X.509-format RSA public key (2048-bit). When the
  session is re-fetched with an existing UUID the stored keys are not re-issued;
  `response` comes back empty.
- **TTL:** the server stores the keypair at `session:<uuid>` with a 1-day expiry.

```bash
curl "http://localhost:4567/api/session?uuid=$(uuidgen)"
```

## `POST /api/login`

Authenticates an existing account and returns a fresh Bearer token.

- **Auth:** public.
- **Params (query or form):**
  - `username` — required
  - `password` — required
- **Response:** `200 OK`, `AuthResponse`:
  ```json
  {
    "username": "alice",
    "token": "2bf4e3d0-...-...",
    "uid":   "89b19e58-...-...",
    "expires_in": 86400,
    "followers": "0",
    "following": "0"
  }
  ```
- **Errors:**
  - `401 Unauthorized` — `invalid_grant` — username does not exist or password
    does not match.

```bash
curl -X POST "http://localhost:4567/api/login?username=alice&password=hunter2"
```

## `POST /api/register`

Creates a new account and returns both a Bearer token and an activation token.

- **Auth:** public.
- **Params (query or form):**
  - `username` — required, must be unique.
  - `password` — required, hashed with Argon2 (`iterations=3, memory=65536, parallelism=4`) after salting.
  - `email` — required.
- **Response:** `200 OK`, `AuthResponse` plus activation token:
  ```json
  {
    "username": "alice",
    "token": "2bf4e3d0-...-...",
    "uid":   "89b19e58-...-...",
    "expires_in": 86400,
    "followers": "0",
    "following": "0",
    "activation_token": "f0a2b1d4-...-..."
  }
  ```
  The `activation_token` is the value passed to `GET /api/activate`. The current
  code does not send an email — email delivery is a TODO — so the client
  receives the token inline for now.
- **Errors:**
  - `400 Bad Request` — `cannot_register` — the username already exists.
  - `400 Bad Request` — `incomplete_request` — a required parameter is missing.

```bash
curl -X POST \
  "http://localhost:4567/api/register?username=alice&password=hunter2&email=alice@example.com"
```

## `GET /api/activate`

Activates an account previously created by `/api/register`.

- **Auth:** public.
- **Params (query):**
  - `token` — required. The `activation_token` value returned by `/api/register`.
- **Response:** `200 OK`
  ```json
  { "username": "alice", "uid": "89b19e58-...", "activated": "true" }
  ```
- **Errors:**
  - `400 Bad Request` with body `{ "activated": "false" }` — the token did not
    map to a known account. Note: this particular endpoint returns a
    `Map<String, String>` body, not the usual `ErrorResponse`.

```bash
curl "http://localhost:4567/api/activate?token=$ACTIVATION_TOKEN"
```

## Related

- Token storage and filter chain: [Authentication](../authentication.md).
- Error code / status mapping: [errors.md](errors.md).
- Regression surface: `ApiSurfaceRegressionTest` requires all five of these paths
  to exist.
