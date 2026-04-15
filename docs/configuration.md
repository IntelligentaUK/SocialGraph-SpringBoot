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


## Redis

Redis holds all social state (users, posts, tokens, timelines, reactions, blocks,
mutes, counters, etc.). A Redis instance is required.


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