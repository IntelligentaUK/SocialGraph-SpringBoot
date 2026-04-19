# embedding-sidecar

Rust HTTP sidecar that hosts the SigLIP-2 image+text encoder plus two Gemma
vision/audio-language models for the SocialGraph vector-search pipeline:
a larger `gemma-4-31B-it` slot for image+text summaries, and an optional
edge-variant `gemma-4-E4B-it` slot for audio and video summaries (gated behind
`ENABLE_AUDIO_VIDEO_SUMMARY=true`).

## Endpoints

| Method | Path                | Body                                    | Returns                       |
|-------:|:--------------------|:----------------------------------------|:------------------------------|
| `GET`  | `/healthz`          | —                                       | `200 {status:"ok"}` once all enabled slots have loaded, `503 {status:"loading",...}` before |
| `POST` | `/embed/text`       | `{ "text": "..." }`                     | `{ "vector": [1152 floats] }` |
| `POST` | `/embed/image-text` | `{ "image_url": "...", "text_description": "..." }` | `{ "vector": [1152 floats] }` |
| `POST` | `/summarize`        | `{ "status_text": "...", "image_urls": ["..."] }` (≤ `MAX_IMAGES_PER_REQUEST`) | `{ "summary": "..." }` |
| `POST` | `/summarize/audio`  | `{ "status_text": "...", "media_url": "..." }` | `{ "summary": "..." }` (503 if `ENABLE_AUDIO_VIDEO_SUMMARY=false` or E4B still loading) |
| `POST` | `/summarize/video`  | `{ "status_text": "...", "media_url": "..." }` | `{ "summary": "..." }` (503 if `ENABLE_AUDIO_VIDEO_SUMMARY=false` or E4B still loading) |

## Build and run

```bash
cargo build --release
cargo run
# listens on 0.0.0.0:${SIDECAR_PORT:-8000}
```

For a containerized run, the project's root `docker-compose.yml` exposes the
sidecar under the `sidecar` profile:

```bash
docker compose --profile sidecar up -d
curl -fsS http://localhost:8000/healthz
```

## Cargo features

- `(default)` — stub models. `/embed/*` returns deterministic unit-norm
  vectors seeded off the input text/URL; `/summarize` returns a fixed
  template. Lets the rest of the stack (Java worker, Redis Streams, FT.SEARCH)
  be exercised without downloading ~20 GB of weights.
- `models` — pulls in `candle-core`, `candle-nn`, `candle-transformers`. The
  real SigLIP-2 and Gemma loaders live behind this feature. **As of this
  revision both loaders still fall back to the stub with a loud warning** —
  wiring SigLIP-2's two-tower config against candle 0.8 and confirming a
  candle-supported Gemma VLM variant is a follow-up.
- `cuda` / `metal` / `accelerate` — imply `models` and activate the
  corresponding candle backend.

## Environment variables

| Variable                   | Default                                     | Purpose                                                   |
|:---------------------------|:--------------------------------------------|:----------------------------------------------------------|
| `SIDECAR_PORT`             | `8000`                                      | HTTP port                                                 |
| `MODEL_CACHE_DIR`          | `/app/models`                               | HuggingFace weight cache directory                        |
| `HF_TOKEN`                 | (unset)                                     | HF Hub token for gated models                             |
| `MAX_IMAGES_PER_REQUEST`   | `5`                                         | Hard cap on `/summarize` input images                     |
| `IMAGE_FETCH_TIMEOUT_S`    | `10`                                        | Per-request HTTP GET timeout for source images            |
| `MAX_IMAGE_BYTES`          | `10485760` (10 MB)                          | Per-image byte cap; streaming aborts above this           |
| `SIGLIP_MODEL_ID`          | `google/siglip2-giant-opt-patch16-384`      | HF model repo for the SigLIP-2 checkpoint                 |
| `GEMMA_MODEL_ID`           | `google/gemma-4-31B-it`                     | HF model repo for the Gemma VLM used for image+text summaries                                        |
| `GEMMA_EV_MODEL_ID`        | `google/gemma-4-E4B-it`                     | HF model repo for the edge-variant Gemma loaded into the audio+video slot when `ENABLE_AUDIO_VIDEO_SUMMARY=true` |
| `ENABLE_AUDIO_VIDEO_SUMMARY` | `false`                                   | Gate for loading the `gemma_ev` slot. When false, `/summarize/audio` and `/summarize/video` return 503 |
| `MAX_AUDIO_SECONDS`        | `600`                                       | Upper bound on audio clip duration (enforced by real loader; stub ignores)                         |
| `MAX_VIDEO_SECONDS`        | `300`                                       | Upper bound on video clip duration (enforced by real loader; stub ignores)                         |
| `VECTOR_DIM`               | `1152`                                      | SigLIP-2 giant projection dim                             |
| `RUST_LOG`                 | `info,embedding_sidecar=debug`              | tracing filter                                            |
| `CUDA_VISIBLE_DEVICES`     | (GPU default)                               | honoured by candle when `cuda` feature is enabled         |

## Fusion strategy (`/embed/image-text`)

SigLIP-2 places image and text in a **shared** 1152-dim embedding space. The
fused vector is the renormalized average of the two L2-normalized unit
vectors:

```text
img_u = normalize(siglip.image_encoder(image))
txt_u = normalize(siglip.text_encoder(tokenize(text_description)))
fused = normalize((img_u + txt_u) / 2)
```

This embeds the Gemma-derived "visual description" into the image-embedding
space, so a text-only query (embedded with the same SigLIP-2 text encoder) can
dot-product-rank both pure-text captions and image-fused summaries in a single
index.

## Smoke test

```
cargo test
```

- Unit tests for the stub SigLIP (deterministic, unit-norm, per-input variety).
- Integration tests bring up an in-process axum server on `127.0.0.1:0`,
  hit every route, and assert on JSON shape + status codes.
