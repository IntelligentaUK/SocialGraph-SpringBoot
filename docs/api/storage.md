# API: storage

[`StorageController`](../../src/main/java/com/intelligenta/socialgraph/controller/StorageController.java)
exposes the two media-upload entry points. Both routes are authenticated (they
fall under the `anyRequest().authenticated()` rule in
[`SecurityConfig`](../../src/main/java/com/intelligenta/socialgraph/config/SecurityConfig.java)).
The body of `POST /api/lq/upload` is JSON; everything else in this app is form
or query parameters.

## `POST /api/request/storage/key`

Returns a provider-neutral signed upload target. The client can then `PUT` the
bytes directly to the object store without routing them through the app.

- **Params:** none. The controller calls
  `objectStorageService.createSignedUploadTarget(null, null)`, so it does not
  know the extension or content type in advance — the resulting URL is a
  generic upload URL. If you need to pin content type you can ignore this route
  and upload via `POST /api/status` with `type=photo` and raw bytes instead.
- **Response:** `200 OK`, `StorageUploadTarget`:
  ```json
  {
    "provider": "azure",
    "objectKey": "9f4a...-....",
    "objectUrl": "https://photoblobs.blob.core.windows.net/photos/9f4a...-....",
    "uploadUrl": "https://photoblobs.blob.core.windows.net/photos/9f4a...-....?sv=...&sig=...",
    "method": "PUT",
    "headers": {
      "x-ms-blob-type": "BlockBlob"
    },
    "expiresIn": 300
  }
  ```
- **Provider-specific shape:**
  - **Azure** — `uploadUrl` is the blob URL with a SAS query string granting
    create / write / read for `storage.signed-url-ttl-seconds` seconds. Caller
    must include `x-ms-blob-type: BlockBlob`.
  - **GCP** — `uploadUrl` is a V4 signed URL scoped to `PUT`. If the caller
    passed a content type, it is also included in `headers`.
  - **OCI** — `uploadUrl` resolves to a pre-authenticated request (PAR) with
    `ObjectWrite` access on the target object.
- **Errors:** `400 storage_unavailable` if the provider client was not
  initialized on startup (e.g. missing connection string).

> The `expiresIn` value in the response matches
> `storage.signed-url-ttl-seconds` from
> [configuration](../configuration.md#object-storage-shared) (default 300).

## `POST /api/lq/upload`

Uploads a base64-encoded image, seam-carves it to a narrower width (liquid
rescale), and stores the result in the active object store in one round trip.

- **Body (JSON):**
  ```json
  {
    "imageBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
    "cut": 100,
    "mimeType": "image/png"
  }
  ```
  - `imageBase64` — required. Either raw base64 or a `data:image/...;base64,...`
    URL. `ImagePayloads` parses both.
  - `cut` — required, positive integer. Number of pixels to shave from the
    image width. Must be strictly less than the image width.
  - `mimeType` — optional hint. The detector may override it from the magic
    bytes; if the detector finds no match and the hint is unsupported, the
    request fails with `unsupported_image_type`.
- **Supported MIME types:** `image/jpeg`, `image/png`, `image/webp`. Aliases
  (`image/jpg`, `image/x-png`, `image/x-webp`) are normalized.
- **Response:** `200 OK`, `LqUploadResponse`:
  ```json
  {
    "provider": "azure",
    "objectKey": "9f4a...-....png",
    "objectUrl": "https://photoblobs.blob.core.windows.net/photos/9f4a...-....png",
    "mimeType": "image/png"
  }
  ```
- **Errors:**
  - `400 incomplete_request` — `imageBase64` blank or `cut` missing / non-positive
    (bean validation from `@NotBlank`, `@NotNull`, `@Positive` on
    [`LqUploadRequest`](../../src/main/java/com/intelligenta/socialgraph/model/LqUploadRequest.java)).
  - `400 invalid_image_payload` — base64 is malformed, bytes are empty,
    `ImageIO` cannot decode, or `cut` is not smaller than the image width.
  - `400 unsupported_image_type` — MIME type is not JPEG / PNG / WebP.
  - `400 media_upload_failed` — the object store rejected the upload.
  - `400 storage_unavailable` — the storage provider was not initialized.

## Storage provider details

The active provider is picked at startup by
`storage.provider` in `application.yml` (or the `STORAGE_PROVIDER` env var).
Only one `ObjectStorageService` bean is active at a time — the other two classes
are skipped by `@ConditionalOnProperty`.

See [Internals: storage providers](../internals/storage-providers.md) for the
Azure / GCP / OCI implementation notes, and [configuration](../configuration.md)
for required environment variables per provider.

## Related

- [Image pipeline](../internals/image-pipeline.md) — how `ImagePayloads` and
  `LiquidRescaler` work together.
- [errors.md](errors.md) — full error-code table.
