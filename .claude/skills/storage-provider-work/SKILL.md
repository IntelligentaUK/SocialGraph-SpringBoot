---
name: storage-provider-work
description: Use when adding a new object-storage backend, changing the ObjectStorageService contract, or adjusting upload / signed-URL behavior in SocialGraph
when-to-use: Any change that touches service/storage/, StorageProperties, or the StorageController upload paths
---

# Storage provider work

SocialGraph uses one `ObjectStorageService` at a time, picked at startup by
`storage.provider`. Three implementations exist today: Azure Blob, Google Cloud
Storage, and OCI Object Storage. The contract is small and the abstraction is
deliberate — additions should slot in without changing callers.

Authoritative reference:
[`docs/internals/storage-providers.md`](../../../docs/internals/storage-providers.md).

## Triggers

Invoke this skill if your change touches any of:

- `src/main/java/com/intelligenta/socialgraph/service/storage/*.java`
- `src/main/java/com/intelligenta/socialgraph/config/StorageProperties.java`
- `src/main/java/com/intelligenta/socialgraph/controller/StorageController.java`
- The `storage.*` block in `src/main/resources/application.yml`
- `LiquidRescaler` / `ImagePayloads` if your change affects what bytes reach
  the storage layer

## Before you write code

1. Read
   [`docs/internals/storage-providers.md`](../../../docs/internals/storage-providers.md)
   end to end.
2. Read
   [`docs/internals/image-pipeline.md`](../../../docs/internals/image-pipeline.md).
3. Confirm the change really needs a new provider or contract change. Often
   what looks like a provider concern is actually an `ImagePayloads` /
   `LiquidRescaler` change.

## Decision tree

1. **Adding a new provider (e.g. AWS S3)?** → "Add a new provider" checklist below.
2. **Changing how an existing provider creates signed URLs?** → "Modify a
   provider" checklist.
3. **Changing the `ObjectStorageService` contract** (new method, new
   parameter)? → "Change the contract" checklist.
4. **Changing what's uploaded** (rescale parameters, image format support)?
   → This is `ImagePayloads` / `LiquidRescaler` work. Stay in the image
   pipeline page; the storage provider should not need changes.

## Add a new provider — checklist

- [ ] Create
      `src/main/java/com/intelligenta/socialgraph/service/storage/<Name>ObjectStorageService.java`
      extending `AbstractObjectStorageService`.
- [ ] Annotate with:
      ```java
      @Service
      @ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "<name>")
      ```
      Only one provider should ever set `matchIfMissing = true` — that is Azure
      today. **Do not** set it on a new provider.
- [ ] Implement `provider()`, `createSignedUploadTarget`, `upload`, and
      `download`. Reuse `nextObjectKey` and `encodeObjectKey` from
      `AbstractObjectStorageService` for object keys.
- [ ] Use `@PostConstruct` for client init. Wrap it in try/catch and log a
      warning on failure — the existing providers leave the bean in the
      context with a null client and throw `storage_unavailable` lazily.
      Match this pattern unless you have a reason not to.
- [ ] Add a nested properties class to
      [`StorageProperties`](../../../src/main/java/com/intelligenta/socialgraph/config/StorageProperties.java),
      e.g. `S3` with `bucketName`, `region`, etc. Add the standard
      getters/setters (the class uses Spring Boot
      `@ConfigurationProperties`).
- [ ] Add a `storage.<name>.*` block to
      [`application.yml`](../../../src/main/resources/application.yml) with
      `${ENV_VAR:default}` placeholders. Match the existing `storage.azure`,
      `storage.gcp`, `storage.oci` style.
- [ ] Add the SDK dependency to `pom.xml`. Pin a version. Avoid pulling in a
      whole BOM if a single artifact will do.
- [ ] Add a unit test under
      `src/test/java/com/intelligenta/socialgraph/service/storage/` with a
      mocked SDK client, covering `nextObjectKey` formatting, signed-URL
      headers, and the `storage_unavailable` path.
- [ ] If you can stand up a real client in tests (e.g. via Testcontainers),
      add an integration-flavored test under the same package.
- [ ] **Update [`docs/internals/storage-providers.md`](../../../docs/internals/storage-providers.md)**:
      add a new section under "## <Name>" mirroring the structure of the
      existing Azure / GCP / OCI sections.
- [ ] **Update [`docs/configuration.md`](../../../docs/configuration.md)**: add
      a row group under "## <Name>" with every env var and its purpose.
- [ ] **Update [`docs/api/storage.md`](../../../docs/api/storage.md)**: add the
      provider to the "Provider-specific shape" list under
      `POST /api/request/storage/key`.
- [ ] Add a row to the `pom.xml` dependency list note in `README.md` if you
      maintain one (currently not required).
- [ ] Add a `CHANGELOG.md` entry.

## Modify a provider — checklist

- [ ] Make the change. If you altered signed-URL parameters, update the
      `Headers` map returned by `createSignedUploadTarget` so callers know
      what to send.
- [ ] Update the matching section of
      [`docs/internals/storage-providers.md`](../../../docs/internals/storage-providers.md).
- [ ] Update the unit test — assert the new headers / parameters.
- [ ] If `expiresIn` semantics change (e.g. you stop honoring
      `signedUrlTtlSeconds`), document that explicitly in
      [`docs/configuration.md`](../../../docs/configuration.md) and the
      changelog.

## Change the contract — checklist

- [ ] Update
      [`ObjectStorageService.java`](../../../src/main/java/com/intelligenta/socialgraph/service/storage/ObjectStorageService.java).
- [ ] Update **all three** existing implementations
      (`AzureObjectStorageService`, `GcpObjectStorageService`,
      `OciObjectStorageService`) plus
      [`AbstractObjectStorageService`](../../../src/main/java/com/intelligenta/socialgraph/service/storage/AbstractObjectStorageService.java)
      if the helper signatures change.
- [ ] Update every caller. Today the callers are:
  - `StorageController.requestStorageKey` — `createSignedUploadTarget`
  - `StorageController.uploadWithRescale` — `upload`
  - `ShareService.sharePhoto(uid, content, bytes, contentType)` — `upload`
  No code currently calls `download`; if you're removing it, do so.
- [ ] Update
      [`docs/internals/storage-providers.md`](../../../docs/internals/storage-providers.md)
      "Interface" section.
- [ ] Run `./mvnw test`. The provider tests will catch most call-site issues.
- [ ] **Breaking-change** entry in `CHANGELOG.md`.

## What not to do

- **Do not bypass `AbstractObjectStorageService.nextObjectKey`.** It enforces
  the prefix and extension conventions; subverting it breaks the
  download / read paths in subtle ways.
- **Do not return a provider-specific `StorageUploadTarget`.** The DTO is
  intentionally generic. If a provider needs extra metadata, add it to the
  generic DTO and document that it is provider-specific.
- **Do not call the SDK client without a null check.** Every provider's
  `init()` swallows failures and leaves the client null on purpose so the app
  comes up. Reach the client only via an `ensureXxx()` guard that throws
  `storage_unavailable`.
- **Do not pull SDK transitive dependencies that pin Jackson, Netty, or Lettuce
  versions.** Spring Boot 4 + Java 25 + Lettuce + the existing SDK trio is
  fragile; check `./mvnw dependency:tree` after adding a new SDK.

## Canonical references

- [`docs/internals/storage-providers.md`](../../../docs/internals/storage-providers.md)
- [`docs/internals/image-pipeline.md`](../../../docs/internals/image-pipeline.md)
- [`docs/api/storage.md`](../../../docs/api/storage.md)
- [`docs/configuration.md`](../../../docs/configuration.md)
- [`src/main/java/com/intelligenta/socialgraph/service/storage/`](../../../src/main/java/com/intelligenta/socialgraph/service/storage/)
