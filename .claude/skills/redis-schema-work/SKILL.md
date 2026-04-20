---
name: redis-schema-work
description: Use when adding or changing any Redis key — new hash field, new list, new zset, new counter, new key prefix — in SocialGraph. For changes that also have an Infinispan side, pair with `persistence-work`.
when-to-use: Any change to the Redis-adapter keyspace (Redis default provider OR Infinispan RESP-compat mode, which shares the same keys). If you're writing a new store interface or Infinispan cache, use `persistence-work` as well.
---

# Redis schema work

Redis is one of two persistence backends SocialGraph supports; the other is
Infinispan. Under `persistence.provider=redis` (default) and under
`persistence.provider=infinispan` / `client-mode=resp`, **the same Redis
keyspace is in use** — the Lettuce client's writes land on Redis or on
Infinispan's RESP endpoint depending on the provider selection, but the key
shapes are identical. This page and this skill cover the Redis keyspace;
the Infinispan-native cache layout lives in
[`infinispan-schema.md`](../../../docs/internals/infinispan-schema.md).

The Redis schema is documented exhaustively in
[`docs/internals/redis-schema.md`](../../../docs/internals/redis-schema.md);
that page is the single source of truth.

Drifting code from the schema doc is the second most common way to ship a
silent breakage in this repo.

Use this skill **before** you start editing. If your change introduces a new
store interface or touches the Redis/Infinispan dual implementation, pair
this skill with [`persistence-work`](../persistence-work/SKILL.md).

## Triggers

Invoke this skill if your change touches any of:

- A new `redisTemplate.opsForHash().put / putAll / increment / delete`
- A new `redisTemplate.opsForSet().add / remove / members / isMember`
- A new `redisTemplate.opsForList().leftPush / rightPush / range / remove`
- A new `redisTemplate.opsForZSet().add / score / reverseRange`
- A new `redisTemplate.opsForValue().set / get` with a new key
- A new key **prefix** (`user:...`, `post:...`, `session:...`, etc.)
- An expansion of `Verbs.Action` (which changes `key()` and therefore the
  reaction key shape)

## Before you write code

1. Read the relevant section of
   [`docs/internals/redis-schema.md`](../../../docs/internals/redis-schema.md).
2. Find the existing key your change relates to. Existing keys follow strong
   conventions:
   - **User keys are split between `user:<username>` (hash, profile fields and
     counters) and `user:<uid>:<setname>` (set / hash / list / zset).** The
     reverse lookup `user:uid` (hash, field=UID → value=username) bridges them.
   - **Post keys are `post:<postId>` (hash, body) and `post:<postId>:replies`
     (list).**
   - **Reactions use `post:<postId><plural>:` (no colon between postId and
     plural) for the actor list, and `post:<postId>:<actorUid>:` (hash) for the
     reverse lookup. Do not "fix" the missing colon.**
   - **Counters live as hash fields on the user hash** (`followers`, `following`,
     `polyCount`) — not as standalone keys.
   - **Timelines are three views per user**: list (FIFO), zset (personal),
     zset (everyone). All three are written together in
     `ShareService.addPostToTimeline`.
3. Confirm that the new key fits one of the existing patterns. If it doesn't,
   stop and reconsider — adding a new top-level prefix is a bigger architectural
   change than it looks.

## Decision tree

1. **Is your new key a per-user attribute that other users will need to read?**
   → Use a hash on `user:<uid>:<name>`. Don't store it on `user:<username>`
   unless it is genuinely keyed by username (devices are the only example).

2. **Is your new key a counter incremented per request?**
   → Use a hash field on the user hash via `HINCRBY user:<username> <field>`.
   Don't create a new key for it.

3. **Is your new key a relationship between two users?**
   → Use a set on `user:<a>:<relation>` containing UIDs of the related users,
   with a mirror set on `user:<b>:<relation>s` for the reverse direction.
   Follow the existing pattern of `followers / following`,
   `blocked / blockers`, `muted / muters`.

4. **Is your new key a per-user filter (block list, allow list, keyword list)?**
   → Use a hash where field == value (the "hash-as-set" pattern). Lets you
   `HSETNX` for idempotent adds.

5. **Is your new key a fan-out destination written by a service writer?**
   → Make sure the writer goes through a single helper (the way
   `addPostToTimeline` is the only writer for the three timeline keys). Don't
   spread the writes across multiple call sites.

## Checklist for any schema change

- [ ] Implement the read and write paths. Use a service method, not direct
      controller-to-Redis access (the only existing exception is
      `StatusController` for negative-keyword and image-block writes — don't
      add new exceptions).
- [ ] Add a service-level test that asserts the exact Redis ops (mock
      `StringRedisTemplate` and verify `put` / `add` / `leftPush` calls with
      the correct arguments).
- [ ] **Update `docs/internals/redis-schema.md`** — add a row to the relevant
      section table with the key, type, contents, and owner. If the change is
      large enough to need its own section, add one.
- [ ] If the key participates in timeline delivery, update
      `docs/internals/timeline-delivery.md` (especially the consistency notes
      and known gaps).
- [ ] If the change affects an HTTP response or request, follow the
      `api-endpoint-work` skill in addition.
- [ ] Add a `CHANGELOG.md` entry.

## What not to do

- **Do not introduce a new top-level prefix without an entry in the schema doc.**
  Future maintainers and operators rely on the prefix list to know what to
  back up, what to TTL, what to migrate.
- **Do not split state between Redis and a DTO field that gets persisted
  separately.** Either it lives in Redis, or it doesn't exist between requests.
- **Do not add a TTL to a "permanent" key** without flagging it in the schema
  doc and the changelog. The only TTLs today are `tokens:<token>` and
  `session:<uuid>`.
- **Do not store JSON blobs in string keys when a hash will do.** It costs you
  the ability to `HGET` individual fields and breaks all of the existing
  patterns.
- **Do not store devices, timelines, or any other UID-keyed structure under
  `user:<username>:...`.** The `user:<username>:devices` key is a legacy
  exception — do not add to that pattern.

## Canonical references

- [`docs/internals/redis-schema.md`](../../../docs/internals/redis-schema.md)
- [`docs/internals/timeline-delivery.md`](../../../docs/internals/timeline-delivery.md)
- [`docs/internals/security-filter.md`](../../../docs/internals/security-filter.md)
  (token + user:uid lookups)
- [`docs/architecture.md`](../../../docs/architecture.md)
