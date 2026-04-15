# API: actions

[`ActionController`](../../src/main/java/com/intelligenta/socialgraph/controller/ActionController.java)
covers reactions on posts: **like**, **love**, **fav**, and **share**. The four
verbs share a common shape — one `GET /api/<plural>` to list actors, one
`POST /api/<verb>` to perform the action, and one `POST /api/un<verb>` to
reverse it.

The verbs are defined in
[`Verbs.Action`](../../src/main/java/com/intelligenta/socialgraph/Verbs.java).
`HUG` exists in the enum but is **not exposed** by this controller — adding it
would require new routes.

> **Note on the `share` verb.** `ActionController`'s `POST /api/share` records a
> *reaction* on a post (a share action) and is distinct from the reshare flow on
> `POST /api/posts/{postId}/reshare` documented in
> [status.md](status.md), which actually creates a new post of type `reshare`.
> They affect different Redis keys.

## Shared response shapes

**Action responses** (POST /api/like, POST /api/unlike, etc.):

```json
{ "result": "Liked Post: <postId>" }
```

Exact strings:

| Verb | Perform → | Reverse → |
|------|-----------|-----------|
| like | `Liked Post: <id>` or `Already liked Post: <id>` | `Unliked Post: <id>` or `Cannot unlike, not like: <id>` |
| love | `Loved Post: <id>` / `Already loved Post: <id>` | `Unloved Post: <id>` / `Cannot unlove, not love: <id>` |
| fav  | `Faved Post: <id>` / `Already faved Post: <id>` | `Unfaved Post: <id>` / `Cannot unfav, not fav: <id>` |
| share | `Shared Post: <id>` / `Already shared Post: <id>` | `Unshared Post: <id>` / `Cannot unshare, not share: <id>` |

**Action list responses** (GET /api/likes, GET /api/loves, etc.):

```json
{
  "actionType": "likes",
  "object": "<postId>",
  "actors": [
    { "@type": "person", "uuid": "...", "username": "alice", "displayName": "Alice Smith" }
  ],
  "count": 1,
  "duration": 2
}
```

`actionType` is the plural verb form — `likes`, `loves`, `favs`, `shares`.

## Likes

### `GET /api/likes`

- **Params (query):**
  - `uuid` — required. Post ID.
  - `index` — required. Zero-based offset.
  - `count` — required. Page size.
- **Response:** `200 OK`, `ActionResponse` with `actionType: "likes"`.
- **Errors:** `400 cannot_perform_action` — post does not exist.

### `POST /api/like`

- **Params:** `uuid` (required). Auth required.
- **Response:** `200 OK`, `{ "result": "Liked Post: <uuid>" }`.
- **Idempotent:** repeating the call returns `Already liked Post: <uuid>`.

### `POST /api/unlike`

- **Params:** `uuid` (required). Auth required.
- **Response:** `200 OK`, `{ "result": "Unliked Post: <uuid>" }` or `Cannot unlike, not like: <uuid>` when the user had not liked it.

## Loves

### `GET /api/loves`
### `POST /api/love`
### `POST /api/unlove`

Same shapes as likes, with `actionType: "loves"` and the verb swapped into the
`result` string.

## Favorites

### `GET /api/faves`
### `POST /api/fav`
### `POST /api/unfav`

Same shapes, `actionType: "favs"`, `favs` suffix on Redis keys.

## Shares (reaction)

### `GET /api/shares`
### `POST /api/share`
### `POST /api/unshare`

Same shapes, `actionType: "shares"`. Again: this is the *reaction*, not the
repost flow.

## Backing Redis keys

Each action is stored twice: once as an ordered list of actor UIDs on the post
(for pagination), and once as a reverse-lookup hash so `containsAction` is O(1).

- `post:<postId>likes:` — list of actor UIDs (note: no colon between `postId`
  and `likes:`; the key suffix comes from `Verbs.Action.key()` which returns
  `<plural>:`).
- `post:<postId>loves:`, `post:<postId>favs:`, `post:<postId>shares:` — same pattern.
- `post:<postId>:<actorUid>:` — hash with fields `like`, `love`, `fav`, `share`
  set to `"1"` when the actor has performed that action on the post.

Full schema: [Redis schema](../internals/redis-schema.md).

## Related

- [Status endpoints](status.md) — post creation, reply, reshare (note: reshare
  creates a new post, unlike the `share` reaction here).
- [errors.md](errors.md) for error-code mapping.
