# API: status and posts

[`StatusController`](../../src/main/java/com/intelligenta/socialgraph/controller/StatusController.java)
owns post creation (text / photo / video), replies, reshares, post edits,
deletes, device list, and per-user content filters (negative keywords and
image-hash blocks). Every endpoint on this page is authenticated.

## Posting

### `POST /api/status`

Creates a new post. The payload shape depends on `type`.

- **Params (query):**
  - `type` — required. One of `text`, `photo`, `video`.
  - `content` — required for `text`, optional for `photo` and `video`.
  - `url` — for `video` (required) or `photo` (optional — if present, reused
    directly; if absent the request body is uploaded to object storage).
- **Body (photo only):**
  - When `url` is absent, the raw image bytes go in the HTTP body and are
    uploaded via the active `ObjectStorageService`. Use the `Content-Type`
    header to hint MIME; otherwise `ImagePayloads` detects JPEG / PNG / WebP
    from magic bytes.
- **Response:** `200 OK`, a post map:
  ```json
  {
    "id": "<postId>",
    "type": "photo",
    "uid": "<authorUid>",
    "created": "1717171717",
    "content": "caption text",
    "url": "https://.../photos/abc.jpg",
    "duration": "12"
  }
  ```
  The response intentionally omits `imageHash` — that value is only stored in
  Redis and checked against other users' block lists during delivery.
- **Errors:**
  - `400 Bad Request` — unknown `type`, blank `content` for text, missing `url`
    for video, missing both `url` and `body` for photo.
  - `400 invalid_image_payload` / `unsupported_image_type` — photo upload with
    bytes the MIME detector rejects.

### `POST /api/status` — behavior matrix

| `type`  | Body                      | Notes |
|---------|---------------------------|-------|
| `text`  | ignored                   | `content` is required, must not be blank. |
| `photo` | `url` present             | URL stored as-is; no upload. |
| `photo` | raw image bytes           | Uploaded via `ObjectStorageService.upload`; MIME detected. |
| `photo` | multipart `files[]`       | Up to `embedding.max-images-per-post` images (default 10); each uploaded in order, first one stored in the legacy `url` field, and the full list available on `TimelineEntry.imageUrls`. |
| `photo` | neither                   | 400. |
| `video` | `url` required            | URL stored as-is; no upload. |

See [timeline delivery](../internals/timeline-delivery.md) for what happens
after the post is stored.

### Multi-image photo posts — multipart/form-data

To attach multiple images in a single post, use `multipart/form-data`:

```bash
curl -X POST http://localhost:4567/api/status \
  -H "Authorization: Bearer $TOKEN" \
  -F "type=photo" \
  -F "content=sunset at the pier" \
  -F "files=@pier1.jpg" \
  -F "files=@pier2.jpg" \
  -F "files=@pier3.jpg"
```

- **Limit.** At most `embedding.max-images-per-post` images (default 10). Over-cap requests fail with `400`.
- **Response.** Same map shape as the single-image response (`id`, `type`, `uid`, `content`, `url`, `created`, `duration`) plus `imageCount` indicating how many images were stored. The primary `url` field is populated with the first uploaded image URL for backwards compatibility; the full ordered list is available on `TimelineEntry.imageUrls` when the post is later read through timeline / `GET /api/posts/{postId}` endpoints.
- **Embedding pipeline.** The first 5 images and the caption are sent to the Rust embedding sidecar for a Gemma visual summary; the first image + that summary is fused into a SigLIP-2 combined vector and the caption alone becomes a SigLIP-2 text vector. Both vectors are KNN-indexed so the post is searchable via `/api/search/question` (multimodal) and `/api/search/ai` (text-only). See [search.md](search.md) and [redis-schema.md § Vector search](../internals/redis-schema.md#vector-search).

## Reading posts

### `GET /api/posts/{postId}`

- **Path:** `postId` — the post UUID.
- **Response:** `200 OK`, `TimelineEntry`:
  ```json
  {
    "uuid": "...",
    "type": "photo",
    "content": "...",
    "url": "https://...",
    "created": "1717171717",
    "actorUid": "...",
    "actorUsername": "alice",
    "actorFullname": "Alice Smith"
  }
  ```
- **Errors:**
  - `400 cannot_perform_action` — post does not exist.
  - Returns `null` / filtered if the viewer has blocked the author or vice
    versa, or if a negative-keyword / image-block rule matches.

### `GET /api/posts/{postId}/replies`

Paginated replies.

- **Path:** `postId`.
- **Params:** `index` (required), `count` (required).
- **Response:** `200 OK`, `TimelineResponse` with each reply rendered as a
  `TimelineEntry` and the usual `count` / `duration` fields.

## Mutating posts

### `POST /api/posts/{postId}/reply`

- **Path:** `postId`.
- **Params:** `content` (required, must not be blank).
- **Response:** `200 OK`, the new reply post map (same shape as
  `POST /api/status`, with `type: "reply"` and `parentId: <postId>`).
- **Side effect:** the new reply ID is pushed onto `post:<postId>:replies`.
- **Errors:** `400 cannot_perform_action` — parent post does not exist.

### `POST /api/posts/{postId}/reshare`

Creates a new post of `type: "reshare"` that references the original post.

- **Path:** `postId`.
- **Params:** `content` (optional — a user comment attached to the reshare).
- **Response:** `200 OK`, the new reshare post map, including
  `sharedPostId: <postId>`.
- **Errors:** `400 cannot_perform_action` — original post does not exist.

### `PATCH /api/posts/{postId}`

Edits the `content` of an existing post. Only the original author can edit.

- **Path:** `postId`.
- **Params:** `content` (required, must not be blank).
- **Response:** `200 OK`, the full post map with `updated` set to the new
  Unix timestamp.
- **Errors:**
  - `400 cannot_perform_action` — post does not exist.
  - `403 access_denied` — authenticated user is not the author.

### `DELETE /api/posts/{postId}`

Deletes the post. Only the author can delete.

- **Path:** `postId`.
- **Response:** `200 OK`
  ```json
  { "deleted": "true", "uuid": "<postId>" }
  ```
- **Side effect:** `DEL post:<postId>`. The post is not removed from timelines
  or reaction lists; `TimelineService.generatePost` silently drops any stale
  references at read time.

## Devices

### `GET /api/devices/registered`

Returns the device IDs registered against the authenticated user. Device
registration itself is managed by `DeviceService` but is not currently exposed
through an HTTP route.

- **Params:** `token` (optional, legacy parameter preserved for compatibility;
  ignored), `index` (required), `count` (required — included for pagination
  compatibility; the current implementation returns the whole set).
- **Response:** `200 OK`
  ```json
  { "devices": ["<deviceId>", "..."], "count": 2, "duration": 1 }
  ```

## Content filters

Each user keeps per-user blocklists that apply both at post delivery time
(`pushGraph`) and at read time (`generatePost`).

### `POST /api/add/keyword/negative`

Adds a negative keyword to the authenticated user's filter list. Any post whose
content contains that keyword will be filtered out of the user's timeline.

- **Params:** `keyword` (required).
- **Response:** `200 OK`
  ```json
  { "keyword": "spoilers", "added": "true" }
  ```
  `added` is `"false"` if the keyword was already present.
- **Backing store:** `user:<uid>:negative:keywords` hash.

### `POST /api/add/image/block`

Blocks an image by its MD5 hash. Posts carrying the same image hash will be
filtered out of the user's timeline.

- **Params (query, exactly one of):**
  - `md5` — the image hash directly.
  - `postId` — read the hash from an existing post (`post:<postId>.imageHash`,
    or legacy field `md5`).
- **Response:** `200 OK`
  ```json
  { "imageHash": "<md5>", "added": "true" }
  ```
- **Errors:** `400` if neither `md5` nor a resolvable `postId` is supplied.
- **Backing store:** `user:<uid>:images:blocked:md5` hash.

## Related

- [Timeline endpoints](timeline.md) — where these posts show up.
- [Internals: timeline delivery](../internals/timeline-delivery.md) — fan-out
  algorithm.
- [Internals: image pipeline](../internals/image-pipeline.md) — MIME detection
  and liquid rescale.
- [errors.md](errors.md) — error-code mapping.
