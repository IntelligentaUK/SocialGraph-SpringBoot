# Search API

Vector-search endpoints over the last 7 days of posts. Both endpoints return
posts ranked by dot-product / cosine similarity against a query vector
produced by SigLIP-2's text encoder. The two endpoints differ only in which
field of the RediSearch index they rank against:

| Endpoint | Ranks against | Use for |
|---|---|---|
| `POST /api/search/question` | `combined_vec` (image + Gemma summary fused) | Natural-language questions that benefit from image content (e.g. "what did I post about cats at the beach?") |
| `POST /api/search/ai` | `text_vec` (caption-only SigLIP-2 embedding) | Text-biased retrieval that ignores image content |

Both require `Authorization: Bearer <token>` and accept an identical JSON body.

## Request

```http
POST /api/search/question
Authorization: Bearer <token>
Content-Type: application/json

{
  "query": "pier at sunset",
  "limit": 20
}
```

- `query` (string, **required**) — user's natural-language question or keywords. Must be non-blank.
- `limit` (int, optional) — max results. Default `20`, bounded `1..100`.

## Response

```json
{
  "results": [
    {
      "id": "9e9cbc44-...-c007f66efe42",
      "uid": "u-alice",
      "type": "photo",
      "content": "sunset at the pier",
      "url": "https://cdn.example/pier1.jpg",
      "imageUrls": [
        "https://cdn.example/pier1.jpg",
        "https://cdn.example/pier2.jpg"
      ],
      "created": "1776390013",
      "score": 0.18
    }
  ],
  "count": 1,
  "durationMs": 34
}
```

- `score` is the RediSearch KNN distance (cosine) — **lower is more similar**, 0 being an exact match.
- Fields match `TimelineEntry` for posts that have been turned into embeddings.
- Posts that no longer exist (deleted between embedding and query time) are silently skipped.

## Behaviour

- Results are filtered to the last `embedding.search-window-days` days (default 7). Posts older than that are never returned, even if their embedding hash still carries an 8-day TTL.
- Only posts that completed embedding are returned. A brand-new post typically becomes searchable within a few seconds — the `EmbeddingWorker` picks it up from `embedding:queue` and writes the vectors to `embedding:post:<id>` (see [redis-schema.md](../internals/redis-schema.md#vector-search)).
- A post with **no** images still returns for `/api/search/ai` (via `text_vec`) but will NOT appear in `/api/search/question` results (no `combined_vec` is written for text-only posts).
- If the embedding sidecar is unavailable the query returns `502 bad_gateway` (wrapped as `{ "error": "...", "error_description": "..." }` via `GlobalExceptionHandler`). Writes keep flowing into `embedding:queue`; the worker resumes when the sidecar comes back.

## Validation errors

| Condition | HTTP status | `error` code |
|---|---|---|
| Missing or blank `query` | `400` | `incomplete_request` |
| `limit < 1` or `limit > 100` | `400` | `incomplete_request` |
| Missing / bad token | `401` | `unauthorised` |

## Troubleshooting

- **Empty results despite having recent posts.** Check `redis-cli FT.INFO idx:post:embedding` for `num_docs`. If it's zero, confirm the sidecar is up (`curl -fsS http://localhost:8000/healthz`) and that `XLEN embedding:queue:dlq` is zero — DLQ growth means the worker has been failing.
- **`RedisSystemException: Redis Stack with RediSearch module required`.** The app is pointed at a stock `redis` image, not `redis-stack-server`. Bring up the project's `docker-compose.yml` (`docker compose up -d redis`).
- **Query that should match returns nothing.** RediSearch requires vectors to match the index dimensionality. If the sidecar was swapped for one producing a different dim, clear the index (`FT.DROPINDEX idx:post:embedding DD`) and restart — `RedisSearchIndexInitializer` will recreate it from `embedding.vector-dim`.
