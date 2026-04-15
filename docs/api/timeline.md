# API: timeline

[`TimelineController`](../../src/main/java/com/intelligenta/socialgraph/controller/TimelineController.java)
exposes three views of the authenticated user's timeline. All three are
authenticated.

## `GET /api/timeline`

Time-ordered (FIFO) timeline. Returns posts in the order they were delivered to
the user's timeline list.

- **Params (query):**
  - `index` — required. Zero-based offset into the list.
  - `count` — required. Page size.
- **Response:** `200 OK`, `TimelineResponse`:
  ```json
  {
    "entities": [
      {
        "uuid": "<postId>",
        "type": "photo",
        "content": "caption",
        "url": "https://...",
        "created": "1717171717",
        "actorUid": "...",
        "actorUsername": "alice",
        "actorFullname": "Alice Smith"
      }
    ],
    "count": 1,
    "duration": 4
  }
  ```
- **Backing store:** `LRANGE user:<uid>:timeline index index+count-1`.

## `GET /api/timeline/personal`

Timeline re-sorted by per-recipient edge score. The score is computed at post
delivery time from
`user:<authorUid>:connection:edgescore:<recipientUid>` and stored in the zset
`user:<uid>:timeline:personal:importance`.

- **Params:** `index`, `count` (required, same semantics as FIFO).
- **Response:** `TimelineResponse` sorted by score descending.
- **Backing store:** `ZREVRANGE user:<uid>:timeline:personal:importance ...`.

## `GET /api/timeline/everyone`

Timeline sorted by global social importance of the author. The score comes from
the `user:social:importance` zset at post delivery time.

- **Params:** `index`, `count` (required).
- **Response:** `TimelineResponse` sorted by score descending.
- **Backing store:** `ZREVRANGE user:<uid>:timeline:everyone:importance ...`.

## View-time filtering

All three endpoints hydrate post bodies through
[`TimelineService.generatePost`](../../src/main/java/com/intelligenta/socialgraph/service/TimelineService.java).
That method re-applies four filters **per request**, so new block / mute /
keyword / image-block settings take effect on existing timelines immediately:

1. `canViewContent(viewer, author)` — hides posts if either party has blocked
   the other.
2. `hasNegativeKeyword(viewer, words(content))` — hides posts containing any
   blocked keyword.
3. `isImageBlocked(viewer, imageHash)` — hides posts whose image MD5 is on the
   viewer's block list.
4. Missing posts (`post:<postId>` has been deleted) are silently skipped.

Filtered or missing posts do **not** count against the requested `count` — the
response just comes back shorter.

## Related

- [Internals: timeline delivery](../internals/timeline-delivery.md) for the
  `pushGraph` write path that populates these lists and zsets.
- [Content filters](status.md#content-filters) — how to add negative keywords
  and image blocks.
- [Redis schema](../internals/redis-schema.md).
