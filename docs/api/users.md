# API: users and graph

[`UserController`](../../src/main/java/com/intelligenta/socialgraph/controller/UserController.java)
covers follow / unfollow, block / mute, member-list retrieval, profile read /
update, search, and RSA public key lookup. Every endpoint on this page requires
`Authorization: Bearer <token>` unless otherwise noted.

## Profile

### `GET /api/me`

Returns the authenticated user's profile.

- **Params:** none.
- **Response:** `200 OK`
  ```json
  {
    "uid": "...",
    "username": "alice",
    "fullname": "Alice Smith",
    "bio": "the ceo",
    "profilePicture": "https://.../alice.jpg",
    "created": "1717171717",
    "followers": "12",
    "following": "34",
    "isBlocked": false,
    "isMuted": false,
    "blocksViewer": false
  }
  ```
  The `isBlocked`, `isMuted`, and `blocksViewer` flags are always the self-view
  values on this endpoint (viewer == target).

### `PATCH /api/me`

Partially updates the authenticated user's profile.

- **Params (query or form, all optional):**
  - `fullname`
  - `bio`
  - `profilePicture`
- **Response:** `200 OK`, the updated profile (same shape as `GET /api/me`).
- Only fields that are passed in are updated; others are left unchanged.

### `GET /api/users/{uid}`

Fetches another user's profile as seen by the authenticated user.

- **Path:**
  - `uid` — the target user UID.
- **Response:** `200 OK`, same shape as `GET /api/me`. `isBlocked`, `isMuted`, and
  `blocksViewer` are computed against the authenticated viewer.
- **Errors:** `400 user_not_found` — `uid` has no user record.

### `GET /api/users/search`

Paginated prefix / substring search across usernames and full names.

- **Params (query):**
  - `q` — required. Case-insensitive substring search.
  - `index` — required. Zero-based offset.
  - `count` — required. Page size.
- **Response:** `200 OK`, `MembersResponse`:
  ```json
  { "setType": "search", "members": [...], "count": 3, "duration": 4 }
  ```
  The search iterates the full `user:uid` hash and filters in-process. Fine at
  small scale; for larger user sets replace with a proper index.

## Follow graph

### `POST /api/follow`

Follows another user.

- **Params (query, at least one of):**
  - `uid` — target UID, preferred.
  - `username` — target username. If `uid` is absent, the service resolves
    username → UID via `user:<username>.uuid`.
- **Response:** `200 OK`
  ```json
  {
    "success": "true",
    "following.UID": "<target-uid>",
    "following.Username": "<target-username>"
  }
  ```
- **Errors:**
  - `400 user_not_found` — target does not exist.
  - `400 cannot_follow` (`CannotFollowSelfException`) — trying to follow yourself.
  - `400 cannot_follow` (`AlreadyFollowingException`) — already following the target.

### `POST /api/unfollow`

Unfollows a user.

- **Params (query):**
  - `uid` — required. Target UID.
- **Response:** `200 OK`
  ```json
  { "success": "<uid>", "unfollowed": "<uid>" }
  ```
- **Errors:**
  - `400 cannot_follow` — `uid` equals the authenticated UID.
  - `400 cannot_unfollow` — not currently following the target.

### `GET /api/followers`
### `GET /api/following`
### `GET /api/friends`

Return the authenticated user's followers, followees, or mutual friends.

- **Params:** none.
- **Response:** `200 OK`, `MembersResponse`:
  ```json
  {
    "setType": "followers",
    "members": [ { "uid": "...", "username": "...", "fullname": "..." } ],
    "count": 1,
    "duration": 3
  }
  ```
  `friends` is computed as `followers ∩ following` at request time.

## Block and mute

The block and mute endpoints share a response shape:

```json
{ "uid": "<target-uid>", "action": "blocked|unblocked|muted|unmuted", "changed": "true|false" }
```

`changed` is `"false"` when the state was already the requested value (idempotent
no-op). A block additionally removes any existing follow relationship in both
directions and decrements the corresponding counters.

### `POST /api/block` / `POST /api/unblock`

- **Params:** `uid` (required).
- **Response:** see shape above, with `action` set to `blocked` or `unblocked`.
- **Errors:** `user_not_found`, `cannot_follow` (if `uid` equals authenticated UID).

### `POST /api/mute` / `POST /api/unmute`

- **Params:** `uid` (required).
- **Response:** same shape, `action` is `muted` or `unmuted`.

### `GET /api/blocked`
### `GET /api/blockers`
### `GET /api/muted`
### `GET /api/muters`

Return the authenticated user's block list, the users who have blocked them, the
mute list, and the users who have muted them. Each is a `MembersResponse` with
`setType` matching the path suffix.

## RSA public keys

### `GET /api/me/rsa/public/key`

Returns the authenticated user's RSA public key (stored at
`user:<uid>:crypto.publicKey`).

- **Params:** none.
- **Response:** `200 OK`, plain-text body (the Base64 key string). Empty string
  if no key is registered.

### `GET /api/rsa/public/key`

Returns another user's RSA public key.

- **Params (query):**
  - `uid` — optional. If omitted, behaves like `/api/me/rsa/public/key`.
- **Response:** `200 OK`, plain-text body.

## Related

- [Authentication](../authentication.md) for `Authorization` header details.
- [Redis schema](../internals/redis-schema.md) for the underlying key layout
  (`user:<uid>:followers`, `user:<uid>:blocked`, `user:uid`, etc.).
- [errors.md](errors.md) for the full error-code table.
