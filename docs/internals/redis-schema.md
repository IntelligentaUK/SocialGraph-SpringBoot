# Redis schema

SocialGraph stores every piece of social state in Redis — users, tokens, posts,
timelines, reactions, follow graph, block / mute sets, session keypairs,
counters. This page enumerates every key the application touches and the service
that owns it.

There are no namespaces or prefixes beyond the per-type keys below. Everything
lives on one logical Redis database.

## Users and auth

| Key | Type | Fields / contents | Owner |
|-----|------|-------------------|-------|
| `user:<username>` | hash | `passwordHash`, `salt`, `poly`, `uuid`, `username`, `email`, `created`, `followers` (counter), `following` (counter), `activated`, `fullname`, `bio`, `profilePicture`, `polyCount` | `UserService`, `TokenAuthenticationFilter` |
| `user:uid` | hash | field = UID, value = username (reverse lookup) | `UserService`, `TokenAuthenticationFilter` |
| `user:activations:<activationToken>:uid` | string | UID to activate | `UserService` |
| `tokens:<token>` | string | UID. TTL = `app.security.token-expiration-seconds` (default 86400) | `UserService`, `TokenAuthenticationFilter` |
| `user:<uid>:crypto` | hash | `publicKey` (Base64 RSA public key) | `UserService` (read) |

Notes:

- `user:<username>` is keyed by **username**, not UID. The reverse lookup
  `user:uid` is the cross-index, and is the only way the rest of the service
  can turn a UID into a profile. Counters are kept on the username hash, so
  `UserService.incrementCounterForUid` internally resolves UID → username before
  `HINCRBY`.
- `polyCount` is bumped on every authenticated request by
  `TokenAuthenticationFilter` as a coarse per-user request counter.
- `followers` and `following` fields on the user hash are plain integer counters
  maintained alongside the follower / following **sets** below. They can diverge
  if writes are interleaved with restarts; the sets are authoritative.

## Follow graph

| Key | Type | Members | Owner |
|-----|------|---------|-------|
| `user:<uid>:followers` | set | UIDs following this user | `UserService.follow/unfollow` |
| `user:<uid>:following` | set | UIDs this user follows | `UserService.follow/unfollow` |
| `user:<uid>:blocked` | set | UIDs blocked by this user | `UserService.block/unblock` |
| `user:<uid>:blockers` | set | UIDs that have blocked this user | `UserService.block/unblock` |
| `user:<uid>:muted` | set | UIDs muted by this user | `UserService.mute/unmute` |
| `user:<uid>:muters` | set | UIDs that have muted this user | `UserService.mute/unmute` |

**Friends** are computed on the fly as `followers ∩ following`
(`UserService.getFriends`) and are not stored.

**Block semantics.** `UserService.block` writes both sides (`A:blocked` += B,
`B:blockers` += A) and **removes any existing follow in either direction**,
decrementing both users' counters. `unblock` reverses only the block set membership.

## Posts

| Key | Type | Fields / contents | Owner |
|-----|------|-------------------|-------|
| `post:<postId>` | hash | `id`, `type`, `uid` (author UID), `created`, `content`, `url`, `imageHash`, `imageCount`, `parentId` (reply), `sharedPostId` (reshare), `updated` (edit), `md5` (legacy alias for `imageHash`) | `ShareService` |
| `post:<postId>:replies` | list | reply post IDs (newest-first via `LPUSH`) | `ShareService.createStatusUpdate` |
| `post:<postId>:images` | list | image URLs in author-posted order (`RPUSH`ed, read via `LRANGE 0 -1`) | `ShareService.sharePhotos` |

Post types in `type`: `text`, `photo`, `video`, `reply`, `reshare`.

`imageHash` is MD5 of the original uploaded image bytes, used for
image-block filtering. Legacy posts may carry it under `md5`; both
`ShareService.readImageHash` and `TimelineService.generatePost` check both field
names. For multi-image posts `imageHash` stores the MD5 of the **first** image.

`imageCount` is the decimal count of URLs in `post:<postId>:images`. Legacy
single-image posts created before multi-image support do not carry this field;
read-side code (`TimelineService.generatePost`) treats its absence as "1 if
`url` is set, else 0" and falls back to `[url]` as the `imageUrls` response.

## Reactions

Each reaction is stored **twice** — once as an ordered list for pagination, once
as a reverse-lookup hash for O(1) `containsAction` checks.

| Key | Type | Contents | Owner |
|-----|------|----------|-------|
| `post:<postId>likes:` | list | actor UIDs | `ActionService` |
| `post:<postId>loves:` | list | actor UIDs | `ActionService` |
| `post:<postId>favs:` | list | actor UIDs | `ActionService` |
| `post:<postId>shares:` | list | actor UIDs | `ActionService` |
| `post:<postId>:<actorUid>:` | hash | `like`, `love`, `fav`, `share` → `"1"` | `ActionService` |

> **The list keys have no colon between the post ID and the suffix.**
> The suffix comes from `Verbs.Action.key()` which returns `"<plural>:"`. Concatenated
> against `"post:<postId>"` the result is `"post:<id>likes:"`. This is unusual
> but is the shape the `ApiSurfaceRegressionTest` + `ActionControllerTest` suite
> relies on — **do not** "fix" it to `post:<id>:likes` without a migration plan.

## Timelines

Each follower gets three redundant views written at delivery time:

| Key | Type | Contents | Order |
|-----|------|----------|-------|
| `user:<uid>:timeline` | list | post IDs | newest-first (`LPUSH`) |
| `user:<uid>:timeline:personal:importance` | zset | post ID → personal edge score | `ZREVRANGE` — descending |
| `user:<uid>:timeline:everyone:importance` | zset | post ID → author's global social importance | `ZREVRANGE` — descending |

Personal edge score comes from
`user:<authorUid>:connection:edgescore:<recipientUid>` (a string-encoded double),
read during delivery by `ShareService.getConnectionZScore`. The zset score is
the author-to-recipient direction, so "personal" means "how much this author
matters to *me*".

The author's global social importance comes from `user:social:importance`
(a zset keyed by UID). Neither the edgescore string keys nor the social-importance
zset have writer code in this repo — they are expected to be populated out of
band.

Reply lists are separate:

- `post:<parentId>:replies` — list of reply post IDs, newest-first.

## Sessions (RSA key exchange)

| Key | Type | Fields | TTL |
|-----|------|--------|-----|
| `session:<uuid>` | hash | `publicKey`, `privateKey` (both Base64-encoded 2048-bit RSA) | 1 day |

Created lazily by `SessionService.getSession` the first time a UUID is
requested. Re-requesting an existing UUID returns the stored public key (the
caller has to remember it themselves — the response is empty the second time).

## Counters and aggregates

| Key | Type | Fields / scores |
|-----|------|-----------------|
| `photos` | hash | UID → number of photos posted by that user |
| `videos` | hash | UID → number of videos posted by that user |
| `posts` | hash | UID → number of text / reply / reshare posts by that user |
| `user:social:importance` | zset | UID → global social importance score (read only by this app) |

The photo / video / text counters are bumped inside
`ShareService.createStatusUpdate`'s `MULTI`/`EXEC` block so they stay in sync
with post creation.

## Devices

| Key | Type | Members |
|-----|------|---------|
| `user:<username>:devices` | set | registered device IDs |

Managed by `DeviceService.register / deregister / list`. `StatusController`
reads this set for `GET /api/devices/registered`. There is currently no HTTP
endpoint to register or deregister devices — call the service directly from
code, or adjust the set with `redis-cli` for now.

> Device keys use **username**, not UID. This is a legacy inconsistency that
> survived the migration. Post / follow / timeline keys all use UID. When
> calling `DeviceService` you must pass the username, not the UID.

## Content filters

| Key | Type | Contents |
|-----|------|----------|
| `user:<uid>:negative:keywords` | hash | field = keyword, value = keyword (used as a set) |
| `user:<uid>:images:blocked:md5` | hash | field = md5 hex, value = md5 hex |

Both are queried at delivery time (`ShareService.shouldDeliver`) and at read
time (`TimelineService.generatePost`). The hash-as-set pattern is intentional —
`HSETNX` is O(1) and lets `POST /api/add/keyword/negative` return whether the
keyword was new without an extra `EXISTS` round trip.

## Vector search

SocialGraph hosts two RediSearch vector indexes used by `/api/search/question`
and `/api/search/ai`. This is the only feature in the repo that requires
**Redis Stack** (the base `redis` image does not ship the `search` module and
`RedisSearchIndexInitializer` will fail startup with a clear error).

### Async pipeline

| Key | Type | Fields / contents | Owner | TTL |
|-----|------|-------------------|-------|-----|
| `embedding:queue` | stream | `postId`, `authorUid` | `ShareService.createStatusUpdate` (produce), `EmbeddingWorker` (consume) | — |
| `embedding:queue:dlq` | stream | `postId`, `authorUid`, `failure`, `message`, `attempts` | `EmbeddingWorker.onFailure` | — |

`ShareService` XADDs each new post (except videos and fully-empty posts) to
`embedding:queue` after the post's creation transaction commits.
`EmbeddingWorker` is a `@Component` with a daemon thread that joins the
`embed-workers` consumer group and blocks on `XREADGROUP ... BLOCK 5000 COUNT 10`.
For each message it calls the Rust sidecar (`/summarize` + `/embed/image-text`
+ `/embed/text`), writes the result to `embedding:post:<postId>`, and `XACK`s.
Failed messages are retried (in-memory counter); on the Nth failure
(`embedding.dlq-max-retries`, default 3) they're redirected to
`embedding:queue:dlq` and `XACK`ed on the main stream.

### Embedding records and index

| Key | Type | Fields / contents | Owner | TTL |
|-----|------|-------------------|-------|-----|
| `embedding:post:<provider>:<dim>:<postId>` | hash | `author_uid` (UTF-8), `created` (UTF-8 unix seconds), `combined_vec` (binary: N × float32 LE; omitted for text-only posts), `text_vec` (binary: N × float32 LE), `gemma_summary` (UTF-8) | `EmbeddingWorker.process` | **691 200 s (8 days)** |
| `idx:post:embedding:<provider>:<dim>` | RediSearch index over prefix `embedding:post:<provider>:<dim>:` | see schema below | `RedisSearchIndexInitializer` | — |

The index name and key prefix both encode the active embedding provider
(`ai.embedding.provider`) and its vector dimension. Default install:
`idx:post:embedding:sidecar:1152` over `embedding:post:sidecar:1152:`.
Switching to e.g. OpenAI at 1536d creates a parallel
`idx:post:embedding:openai:1536`; old entries in the sidecar-scoped index
continue to serve queries from the old provider config until their 8-day TTL
expires — no manual `FT.DROPINDEX` needed during a provider cutover.

```
FT.CREATE idx:post:embedding ON HASH PREFIX 1 embedding:post: SCHEMA
  author_uid TAG
  created NUMERIC SORTABLE
  combined_vec VECTOR HNSW 6 TYPE FLOAT32 DIM 1152 DISTANCE_METRIC COSINE
  text_vec     VECTOR HNSW 6 TYPE FLOAT32 DIM 1152 DISTANCE_METRIC COSINE
```

The 8-day TTL on `embedding:post:<postId>` gives a 1-day buffer past the
7-day query window; RediSearch auto-removes expired hash keys from the index,
so no cron job is required. Inserting a vector with dimension ≠ 1152 fails at
write time, not query time — `VectorSearchService` always passes a 1152-dim
query vector from SigLIP-2.

> Note the TTL. Every other post-adjacent key in this schema is permanent
> (posts, replies, timelines, actions, etc.). `embedding:post:*` is the only
> key family in SocialGraph with a wall-clock expiry. Consumers that need to
> resurface old posts must re-create the embedding by XADDing a fresh event
> to `embedding:queue` (e.g. a backfill runner) — it is NOT sufficient to
> reset the TTL on an existing key.

## Read / write ownership

| Service | Writes | Reads |
|---------|--------|-------|
| `UserService` | `user:<username>`, `user:uid`, `user:activations:*`, `tokens:*`, `user:<uid>:(followers\|following\|blocked\|blockers\|muted\|muters)` | all of the above + `user:<uid>:crypto`, `user:<uid>:(negative:keywords\|images:blocked:md5)` |
| `SessionService` | `session:<uuid>` | `session:<uuid>` |
| `ShareService` | `post:<postId>`, `post:<parentId>:replies`, `user:<uid>:timeline*` (fan-out), `photos`, `videos`, `posts` | `post:<postId>`, `user:<authorUid>:followers`, `user:<authorUid>:connection:edgescore:*`, `user:social:importance` |
| `TimelineService` | — | `user:<uid>:timeline*`, `post:<postId>`, `post:<postId>:replies` |
| `ActionService` | `post:<postId>likes:` (and the other three verbs), `post:<postId>:<actorUid>:` | same |
| `DeviceService` | `user:<username>:devices` | same |
| `TokenAuthenticationFilter` | `user:<username>.polyCount` (HINCRBY) | `tokens:*`, `user:uid` |

## Key lifecycle summary

- **TTLs**: only `tokens:<token>` and `session:<uuid>` expire. Everything else is
  persistent until explicitly deleted.
- **Deletion**: `DELETE /api/posts/{postId}` only removes `post:<postId>`. It
  does **not** remove the post from follower timelines or reaction lists —
  those are pruned lazily at read time by skipping entries with no hash body.
- **Migration**: adding a new keyspace means adding both the writer (usually in
  a service) and the reader (usually in a controller or another service), then
  updating this page.

## Related

- [Architecture](../architecture.md) — high-level component diagram.
- [Timeline delivery](timeline-delivery.md) — `pushGraph` fan-out in detail.
- [Security filter](security-filter.md) — token lookup path.
