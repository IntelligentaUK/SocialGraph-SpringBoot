# Getting started

This page gets a developer from zero to a running SocialGraph instance with the
tests passing locally.

## Prerequisites

| Dependency | Minimum | Notes |
|------------|---------|-------|
| JDK        | Java 25 | `java -version` should report 25. Earlier JDKs will not compile the project. |
| Maven      | Bundled | Use the `./mvnw` wrapper that ships with the repo. |
| Redis      | 5.0+    | The app uses `StringRedisTemplate`; any recent Redis works. |
| Storage    | one of  | Azure Blob Storage, Google Cloud Storage, or OCI Object Storage. Default is Azure. |

Optional:

- GraalVM with the `native` profile if you want to try the native-image build
  (still work-in-progress).

## Clone and build

```bash
git clone <your-fork-or-this-repo> SocialGraph
cd SocialGraph
./mvnw -q package -DskipTests
```

## Start Redis

```bash
docker run -d --name socialgraph-redis -p 6379:6379 redis:7
```

Verify:

```bash
redis-cli ping   # -> PONG
```

If your Redis is on a different host / port / uses a password, export
`REDIS_HOST`, `REDIS_PORT`, and/or `REDIS_PASSWORD` before starting the app — see
[Configuration](configuration.md).

## Pick a storage provider

SocialGraph uploads media to exactly one object store. You pick the active provider
via `STORAGE_PROVIDER`:

### Azure Blob (default)

```bash
export STORAGE_PROVIDER=azure
export AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;AccountName=...;AccountKey=..."
export AZURE_STORAGE_CONTAINER=photos
```

### Google Cloud Storage

```bash
export STORAGE_PROVIDER=gcp
export GCP_PROJECT_ID=my-project
export GCP_STORAGE_BUCKET=my-bucket
# Ensure GOOGLE_APPLICATION_CREDENTIALS points at a service-account key file.
```

### Oracle Cloud Infrastructure Object Storage

```bash
export STORAGE_PROVIDER=oci
export OCI_OBJECT_STORAGE_NAMESPACE=my-namespace
export OCI_OBJECT_STORAGE_BUCKET=my-bucket
export OCI_REGION=us-ashburn-1
# Optionally: OCI_CONFIG_FILE, OCI_PROFILE, OCI_OBJECT_STORAGE_ENDPOINT
```

All provider-specific fields are listed in [Configuration](configuration.md).

## Run the app

```bash
./mvnw spring-boot:run
```

By default the server listens on `http://localhost:4567`.

## Smoke test

```bash
# Health check (public endpoint)
curl http://localhost:4567/api/ping
# -> hello

# Register a user (public endpoint)
curl -X POST "http://localhost:4567/api/register?username=alice&password=hunter2&email=alice@example.com"
# -> {"username":"alice","token":"...","uid":"...","expires_in":86400,"followers":"0","following":"0","activation_token":"..."}

# Login (public endpoint) — returns a fresh token
TOKEN=$(curl -s -X POST "http://localhost:4567/api/login?username=alice&password=hunter2" | jq -r .token)

# Use the token for an authenticated request
curl -H "Authorization: Bearer $TOKEN" http://localhost:4567/api/me
```

If all four calls return `2xx` responses, the server is wired up correctly.

## Run the tests

The canonical verification gate is:

```bash
./mvnw test
```

What runs:

- `ApiSurfaceRegressionTest` — fixes the full public HTTP surface (51 endpoints).
  Any endpoint added to the product must appear here.
- Controller slice tests (`AuthControllerTest`, `UserControllerTest`, etc.) — one
  class per controller, using `MockMvc` with a `TestAuthenticatedUserResolver` to
  inject `@AuthenticationPrincipal`.
- Service tests (`UserServiceTest`, `ShareServiceTest`, etc.).
- `CoreUtilitiesTest`, `ImagePayloadsTest` — utility contracts.
- `SecurityConfigTest`, `TokenAuthenticationFilterTest` — auth wiring.

See [Testing](testing.md) for how tests are structured and how to run individual
classes.

## What the server gives you

After startup you have 51 HTTP endpoints under `/api`. The public set is
`/api/ping`, `/api/session`, `/api/login`, `/api/register`, and `/api/activate`.
Everything else requires a Bearer token.

Full list: [API reference](api/README.md).

## Troubleshooting

- **`./mvnw` fails with `Unsupported class file major version`** — you are on a JDK
  older than 25. Install Java 25 (Temurin, OpenJDK, or Zulu all work) and retry.
- **Startup error `Unable to connect to Redis`** — Redis is not reachable at the
  configured host / port. Start Redis or adjust `REDIS_HOST`.
- **Startup warning `Failed to initialize Azure Blob Storage client`** — the Azure
  provider loads lazily; the server will still start, but any upload endpoint will
  return `storage_unavailable` until the connection string is correct.
- **`401` on authenticated requests** — confirm the token came from `/api/login`
  or `/api/register` and that you are sending `Authorization: Bearer <token>` (not
  in a query string). Token TTL defaults to 86400 seconds.
