# Persistence abstraction

SocialGraph's services do not talk directly to Redis or Infinispan. They inject
one or more **store interfaces** under
[`persistence/`](../../src/main/java/com/intelligenta/socialgraph/persistence/).
Each interface has two implementations — one per provider — selected at
startup via `@ConditionalOnProperty`.

This page documents the 12 interfaces, their implementations, and the wiring.

## Dual-implementation wiring

```mermaid
flowchart LR
    Service[Service<br/>e.g. UserService]
    I[Store interface<br/>persistence.TokenStore]
    R[RedisTokenStore<br/>@ConditionalOnProperty<br/>client-mode=resp OR provider=redis]
    IF[InfinispanTokenStore<br/>@ConditionalOnProperty<br/>client-mode=native]

    Service -->|depends on| I
    I -.implemented by.-> R
    I -.implemented by.-> IF
    R -->|Lettuce| RS[(Redis / Infinispan RESP)]
    IF -->|cache| ISP[(Embedded manager<br/>+ HotRod manager)]
```

Exactly one impl is active per run:

- `persistence.provider=redis` or
  (`provider=infinispan` AND `client-mode=resp`) → the `Redis*Store` bean wins
  (`matchIfMissing=true` on the resp check).
- `provider=infinispan` AND `client-mode=native` → the `Infinispan*Store` bean
  wins; the Redis impl is absent.

## The 12 store interfaces

| Interface | Responsibility | Redis impl | Infinispan impl |
|---|---|---|---|
| [`TokenStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/TokenStore.java) | Bearer-token issue / resolve / revoke with TTL | [`RedisTokenStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/redis/RedisTokenStore.java) | [`InfinispanTokenStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/infinispan/InfinispanTokenStore.java) |
| [`SessionStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/SessionStore.java) | RSA key-pair session storage with TTL | `RedisSessionStore` | `InfinispanSessionStore` |
| [`UserStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/UserStore.java) | User profile records; atomic registration across user hash + uid index + activation + token + counter | `RedisUserStore` | `InfinispanUserStore` |
| [`RelationStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/RelationStore.java) | Six relation sets (`FOLLOWERS`, `FOLLOWING`, `BLOCKED`, `BLOCKERS`, `MUTED`, `MUTERS`) | `RedisRelationStore` | `InfinispanRelationStore` |
| [`PostStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/PostStore.java) | Post records, replies list, images list; atomic create across post + images + per-user counters | `RedisPostStore` | `InfinispanPostStore` |
| [`ReactionStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/ReactionStore.java) | Like / love / fav / share ordered actor lists + reverse lookups | `RedisReactionStore` | `InfinispanReactionStore` |
| [`TimelineStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/TimelineStore.java) | Per-user FIFO list + personal-importance zset + everyone-importance zset | `RedisTimelineStore` | `InfinispanTimelineStore` |
| [`DeviceStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/DeviceStore.java) | Per-username device registration set | `RedisDeviceStore` | `InfinispanDeviceStore` |
| [`ContentFilterStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/ContentFilterStore.java) | Per-user negative-keyword and blocked-image-md5 sets | `RedisContentFilterStore` | `InfinispanContentFilterStore` |
| [`CounterStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/CounterStore.java) | Per-user `photos` / `videos` / `posts` counts | `RedisCounterStore` | `InfinispanCounterStore` |
| [`EmbeddingIndexStore`](../../src/main/java/com/intelligenta/socialgraph/persistence/EmbeddingIndexStore.java) | Vector index write + k-NN query over a time window | **Redis: not yet extracted** — `VectorSearchService` remains directly on `StatefulRedisConnection<byte[], byte[]>` | **Infinispan: deferred** — planned as Protobuf `@Indexed` + Ickle k-NN |
| [`EmbeddingQueue`](../../src/main/java/com/intelligenta/socialgraph/persistence/EmbeddingQueue.java) | At-least-once post-creation → embedding pipeline | **Redis: not yet extracted** — `ShareService.XADD` + `EmbeddingWorker.XREADGROUP` remain direct | **Infinispan: deferred** — planned as `CounterManager` sequence + `@ClientListener` |

The last two interfaces are defined but not yet consumed; the current
RediSearch / Streams path in `VectorSearchService` and `EmbeddingWorker`
remains gated on `persistence.provider=redis`, and the `/api/search/*`
endpoints + embedding pipeline are off in RESP and native modes until the
follow-up wiring lands.

## Service → store wiring

| Service | Stores injected |
|---|---|
| `UserService` | `UserStore`, `RelationStore`, `ContentFilterStore`, `TokenStore`, `AppProperties` |
| `SessionService` | `SessionStore` |
| `ShareService` | `PostStore`, `TimelineStore`, `CounterStore`, `UserService`, `ObjectStorageService`, `EmbeddingProperties`, `ContentModerator`, `PersistenceProperties`, `StringRedisTemplate` (for the remaining edge-score / social-importance / embedding-queue XADD calls under Redis mode only) |
| `TimelineService` | `TimelineStore`, `PostStore`, `UserService` |
| `ActionService` | `ReactionStore`, `PostStore`, `UserService` |
| `DeviceService` | `DeviceStore` |
| `TokenAuthenticationFilter` | `TokenStore`, `UserStore` |

## Gates

Beans in the persistence package carry one of two conditions:

| Condition | Effect |
|---|---|
| `@ConditionalOnProperty(prefix="persistence.infinispan", name="client-mode", havingValue="resp", matchIfMissing=true)` | Redis implementations. Active when `client-mode=resp` (Infinispan RESP) **or** `client-mode` is unset (Redis default). |
| `@ConditionalOnProperty(prefix="persistence.infinispan", name="client-mode", havingValue="native")` | Infinispan implementations. Only active when the property is explicitly `native`. |

The two sets are mutually exclusive, and Spring refuses to start if neither
bean exists for a required interface.

## Not-yet-abstracted access

The refactor intentionally left a small number of direct `StringRedisTemplate`
calls in `ShareService` to avoid ballooning the scope:

1. `user:<authorUid>:connection:edgescore:<recipientUid>` — per-edge score
   string, read in `addPostToTimeline`. The edge-score is out of scope for
   this repo (populated externally) and has no Infinispan equivalent defined.
2. `user:social:importance` — a global sorted set of UID → importance,
   populated externally, read in `addPostToTimeline`.
3. `embedding:queue` XADD — the producer half of the Redis Streams pipeline.
   Extracted to the `EmbeddingQueue` interface but not yet switched over;
   `ShareService` still calls `redisTemplate.opsForStream().add(...)` behind
   the `embeddingQueueEnabled` flag which is set from `PersistenceProperties`.

All three are gated on `persistence.provider=redis`; they no-op when
Infinispan is selected.

## Adding a new store

1. Create the interface in `persistence/` with a narrow, bounded-context
   surface. Avoid a god `PersistenceService` — each interface corresponds to
   one service or workflow.
2. Create `persistence/redis/Redis<Name>Store` with
   `@ConditionalOnProperty(prefix="persistence.infinispan", name="client-mode",
   havingValue="resp", matchIfMissing=true)`. Reproduce the exact Redis
   command sequence the current code uses.
3. Create `persistence/infinispan/Infinispan<Name>Store` with
   `@ConditionalOnProperty(...,"native")`. Inject `EmbeddedCacheManager`,
   `RemoteCacheManager`, and / or `CounterManager` as needed.
4. Add a cache definition to
   [`InfinispanConfig.Native#embeddedCacheManager`](../../src/main/java/com/intelligenta/socialgraph/config/InfinispanConfig.java)
   if a new cache is needed.
5. Update [`infinispan-schema.md`](infinispan-schema.md) and
   [`redis-schema.md`](redis-schema.md) tables with the new surface.
6. Refactor the consuming service to depend on the new interface.
7. Update the service unit test to mock the interface.
8. Add a changelog entry under `[Unreleased]`.

See [`.claude/skills/persistence-work`](../../.claude/skills/persistence-work/SKILL.md)
for the procedural checklist.

## Related

- [Persistence](../persistence.md) — user-facing provider selection guide.
- [Redis schema](redis-schema.md) — the Redis / RESP keyspace the Redis impls
  preserve.
- [Infinispan schema](infinispan-schema.md) — the Infinispan cache layout the
  native impls use.
- [Architecture](../architecture.md) — where the persistence layer sits.
