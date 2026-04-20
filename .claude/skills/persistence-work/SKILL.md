---
name: persistence-work
description: Use when adding or changing anything that reads or writes persistent state in SocialGraph — new store methods, new caches, provider-mode tweaks, Infinispan native impls, or changes to the Redis/Infinispan dual-implementation split.
when-to-use: Any change that adds a persistence.* interface method, a Redis*Store or Infinispan*Store implementation, a new cache definition in InfinispanConfig, or a new @ConditionalOnProperty gate on the persistence provider.
---

# Persistence work

SocialGraph has two persistence backends selectable at startup:
`persistence.provider=redis` (default) and `persistence.provider=infinispan`
(with `client-mode=resp` or `client-mode=native`). Services depend on store
**interfaces** under `com.intelligenta.socialgraph.persistence`; each
interface has a Redis implementation under `persistence.redis.*` and an
Infinispan implementation under `persistence.infinispan.*`, selected via
`@ConditionalOnProperty`.

The canonical references are:

- [`docs/persistence.md`](../../../docs/persistence.md) — provider selection,
  topology diagrams, env vars.
- [`docs/internals/persistence-abstraction.md`](../../../docs/internals/persistence-abstraction.md)
  — the 12 interfaces and their wiring.
- [`docs/internals/redis-schema.md`](../../../docs/internals/redis-schema.md)
  — Redis adapter keys (also applies to RESP-compat mode).
- [`docs/internals/infinispan-schema.md`](../../../docs/internals/infinispan-schema.md)
  — Infinispan native-mode cache layout.

Use this skill **before** you change any persistence-layer code. If the
change is purely about Redis key shape under the existing Redis adapter, use
[`redis-schema-work`](../redis-schema-work/SKILL.md) instead.

## Triggers

Invoke this skill when the change involves any of:

- A new method on a `persistence/*Store` interface.
- A new `persistence/redis/Redis*Store` or `persistence/infinispan/Infinispan*Store`.
- A new cache definition in `InfinispanConfig.Native#embeddedCacheManager`.
- A new `@ConditionalOnProperty(prefix="persistence.infinispan", name="client-mode", …)` gate.
- A change to `PersistenceProperties`, `PersistenceEnvironmentPostProcessor`,
  `InfinispanConfig`, or the gates on `VectorSearchService` / `SearchController` /
  `EmbeddingWorker` / `RedisSearchIndexInitializer`.
- A change to the Lettuce → Infinispan RESP redirect logic.

## Before you write code

1. Read the interface for the store you're touching. Keep the surface narrow
   and bounded-context — do **not** merge responsibilities across interfaces.
2. Identify the matching Redis / Infinispan impls. Both must be updated in
   lockstep; never leave one side behind.
3. Read the relevant rows in
   [`redis-schema.md`](../../../docs/internals/redis-schema.md) and
   [`infinispan-schema.md`](../../../docs/internals/infinispan-schema.md) so
   your changes line up with the documented contract.
4. If your change requires a new cache, plan:
   - the **key type** (`String`, composite)
   - the **value type** (prefer explicit Map / List structures over raw
     JSON; Infinispan serialises transparently within a JVM)
   - whether it belongs in the ephemeral tier (`expiration.lifespan` set) or
     cluster tier (no TTL)
   - the name — mirror the Redis key prefix where possible

## Decision tree

1. **Am I adding a new persistence responsibility entirely?**
   → Introduce a new interface under `persistence/`. Write both impls.
   Define a new Infinispan cache in `InfinispanConfig.Native` if needed.
   Add rows to both schema docs.

2. **Am I adding a method to an existing store?**
   → Implement it in both the Redis impl and the Infinispan impl. Unit-test
   with a mocked interface; do not mock `StringRedisTemplate` or
   `EmbeddedCacheManager` directly.

3. **Am I changing an existing Redis key shape?**
   → Preserve the current key shape in the Redis impl (the
   `ApiSurfaceRegressionTest` and several services depend on it). Add the
   new shape alongside, then migrate readers and update
   [`redis-schema.md`](../../../docs/internals/redis-schema.md). The Redis
   "unusual" reaction-key form (`post:<id>likes:`) is locked — do not fix.

4. **Am I wiring a new @ConditionalOnProperty?**
   → Use the existing pair of conditions:
   - Redis side: `@ConditionalOnProperty(prefix="persistence.infinispan",
     name="client-mode", havingValue="resp", matchIfMissing=true)`
   - Infinispan side: `@ConditionalOnProperty(prefix="persistence.infinispan",
     name="client-mode", havingValue="native")`
   Any other combination probably breaks bean wiring in one of the three
   modes.

5. **Am I touching RediSearch / Streams / edge-score / social-importance
   paths?**
   → Those paths remain gated on `persistence.provider=redis` and the
   `EmbeddingIndexStore` / `EmbeddingQueue` interfaces exist but are not
   wired. Native-mode replacements are deferred follow-ups — do not
   accidentally activate them without completing the Infinispan impl.

## Checklist

- [ ] Interface method added / modified with Javadoc on the contract.
- [ ] `Redis*Store` implementation updated; key shapes preserved.
- [ ] `Infinispan*Store` implementation updated; cache name matches
      `infinispan-schema.md`.
- [ ] `InfinispanConfig.Native#embeddedCacheManager` has a
      `manager.defineConfiguration("<name>", …)` entry for every new cache.
- [ ] Unit test updated to mock the store interface (not the template /
      cache manager).
- [ ] [`redis-schema.md`](../../../docs/internals/redis-schema.md) row
      updated.
- [ ] [`infinispan-schema.md`](../../../docs/internals/infinispan-schema.md)
      row updated; gap matrix refreshed if the change narrows the gap.
- [ ] [`persistence-abstraction.md`](../../../docs/internals/persistence-abstraction.md)
      table updated if the interface or wiring changed.
- [ ] `CHANGELOG.md` entry.
- [ ] `./mvnw test` is green across Redis-default, RESP, and native modes
      (the integration-test bases
      [`InfinispanRespIntegrationTest`](../../../src/test/java/com/intelligenta/socialgraph/support/InfinispanRespIntegrationTest.java)
      and
      [`InfinispanHotRodIntegrationTest`](../../../src/test/java/com/intelligenta/socialgraph/support/InfinispanHotRodIntegrationTest.java)
      give you Testcontainers for both Infinispan modes).

## What not to do

- **Do not add a new `StringRedisTemplate.opsFor*` call inside a service.**
  Services are persistence-adapter-free now (bar three documented exceptions
  in `ShareService` that are gated on Redis). Route the call through a
  store interface instead.
- **Do not leave a Redis impl or Infinispan impl behind.** If a method exists
  on the interface, both impls must implement it. Spring will fail to start
  otherwise in the missing mode.
- **Do not reuse the same cache for unrelated concerns.** Each store gets
  its own cache(s); keep names bounded-context. The ephemeral tier is
  reserved for short-lived data.
- **Do not change the Redis key schema without updating
  `redis-schema.md`.** The page is load-bearing.
- **Do not introduce a new top-level `persistence.*` property without
  adding it to `PersistenceProperties`, `application.yml`, and
  `docs/persistence.md` (env-var table) + `docs/configuration.md`.**

## Canonical references

- [`docs/persistence.md`](../../../docs/persistence.md)
- [`docs/internals/persistence-abstraction.md`](../../../docs/internals/persistence-abstraction.md)
- [`docs/internals/redis-schema.md`](../../../docs/internals/redis-schema.md)
- [`docs/internals/infinispan-schema.md`](../../../docs/internals/infinispan-schema.md)
- [`docs/configuration.md`](../../../docs/configuration.md)
- [`PersistenceProperties`](../../../src/main/java/com/intelligenta/socialgraph/config/PersistenceProperties.java)
- [`InfinispanConfig`](../../../src/main/java/com/intelligenta/socialgraph/config/InfinispanConfig.java)
- [`PersistenceEnvironmentPostProcessor`](../../../src/main/java/com/intelligenta/socialgraph/config/PersistenceEnvironmentPostProcessor.java)
