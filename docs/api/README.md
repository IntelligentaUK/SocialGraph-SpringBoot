# API reference

SocialGraph exposes 51 HTTP endpoints, all mounted under `/api`. This index lists
every endpoint by controller; each per-controller page documents exact
request / response shapes, auth requirements, and error cases.

Base URL in development: `http://localhost:4567`.

## By controller

| Controller | Page | Endpoints |
|------------|------|-----------|
| Auth | [auth.md](auth.md) | `/api/login`, `/api/register`, `/api/activate`, `/api/session`, `/api/ping` |
| User & graph | [users.md](users.md) | follow / unfollow / block / mute / profile / search / member lists / RSA public key |
| Actions | [actions.md](actions.md) | like, love, fav, share (get / do / undo) |
| Status | [status.md](status.md) | post / read / reply / reshare / edit / delete, content filters, device list |
| Timeline | [timeline.md](timeline.md) | FIFO, personal, everyone |
| Storage | [storage.md](storage.md) | signed upload target, liquid-rescale upload |

## Full endpoint list

Public (no auth):

- `GET  /api/ping`
- `GET  /api/session`
- `POST /api/login`
- `POST /api/register`
- `GET  /api/activate`

Authenticated (`Authorization: Bearer <token>` required):

- `GET  /api/me`
- `PATCH /api/me`
- `GET  /api/users/{uid}`
- `GET  /api/users/search`
- `GET  /api/me/rsa/public/key`
- `GET  /api/rsa/public/key`
- `POST /api/follow`
- `POST /api/unfollow`
- `GET  /api/followers`
- `GET  /api/following`
- `GET  /api/friends`
- `POST /api/block`
- `POST /api/unblock`
- `GET  /api/blocked`
- `GET  /api/blockers`
- `POST /api/mute`
- `POST /api/unmute`
- `GET  /api/muted`
- `GET  /api/muters`
- `GET  /api/likes`
- `POST /api/like`
- `POST /api/unlike`
- `GET  /api/loves`
- `POST /api/love`
- `POST /api/unlove`
- `GET  /api/faves`
- `POST /api/fav`
- `POST /api/unfav`
- `GET  /api/shares`
- `POST /api/share`
- `POST /api/unshare`
- `POST /api/status`
- `GET  /api/posts/{postId}`
- `PATCH /api/posts/{postId}`
- `DELETE /api/posts/{postId}`
- `GET  /api/posts/{postId}/replies`
- `POST /api/posts/{postId}/reply`
- `POST /api/posts/{postId}/reshare`
- `GET  /api/devices/registered`
- `POST /api/add/keyword/negative`
- `POST /api/add/image/block`
- `POST /api/request/storage/key`
- `POST /api/lq/upload`
- `GET  /api/timeline`
- `GET  /api/timeline/personal`
- `GET  /api/timeline/everyone`

## Conventions

- **Method and path.** Each endpoint is documented as `METHOD /api/path`.
- **Parameters.** Most request data comes via query string (`@RequestParam`). The
  few endpoints with a JSON body are called out explicitly.
- **Response shape.** JSON. Field names match the DTO exactly. When a DTO is a
  `Map<String, ...>` assembled inside the controller, the exact keys are listed
  per endpoint.
- **Auth.** Every non-public endpoint requires `Authorization: Bearer <token>`.
- **Errors.** All errors are normalized to
  `{ "error": "<code>", "error_description": "<message>" }` by
  [`GlobalExceptionHandler`](../../src/main/java/com/intelligenta/socialgraph/exception/GlobalExceptionHandler.java).
  See [errors.md](errors.md) for the full mapping.
- **Timings.** Most list responses include a `duration` field with the server-side
  processing time in milliseconds. Do not use this for SLA reporting — it only
  covers the service method, not network, parsing, or filter time.

## Data types used throughout

- `AuthResponse` — `{username, token, uid, expires_in, followers, following, activation_token?}`
- `MemberInfo` — `{uid, username, fullname}`
- `MembersResponse` — `{setType, members: MemberInfo[], count, duration}`
- `TimelineEntry` — `{uuid, type, content?, url?, created, updated?, parentUuid?, sharedPostUuid?, actorUid, actorUsername, actorFullname}`
- `TimelineResponse` — `{entities: TimelineEntry[], count, duration}`
- `ActionActor` — `{"@type": "person", uuid, username, displayName}`
- `ActionResponse` — `{actionType, object, actors: ActionActor[], count, duration}`
- `StorageUploadTarget` — `{provider, objectKey, objectUrl, uploadUrl, method: "PUT", headers, expiresIn}`
- `StoredObject` — `{provider, objectKey, objectUrl, contentType}`
- `ErrorResponse` — `{error, error_description}`

Full source definitions live in
[`src/main/java/com/intelligenta/socialgraph/model/`](../../src/main/java/com/intelligenta/socialgraph/model/).
