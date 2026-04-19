# AI provider configuration

SocialGraph exposes six AI capabilities that each route to a provider you pick
per install:

| Capability | Property | Used by |
|---|---|---|
| Embeddings | `ai.embedding.provider` | `POST /api/search/question`, `POST /api/search/ai`, embedding pipeline for every new post |
| Chat (visual summary) | `ai.chat.provider` | Multi-image post summarisation before SigLIP / text embedding |
| Image generation | `ai.image.provider` | `POST /api/images/generate` (returns 503 when set to `none`) |
| Moderation | `ai.moderation.provider` | Inline check in `ShareService.createStatusUpdate`; flagged content returns 400 `content_blocked` |
| Audio summary | `ai.audio.provider` | `type=audio` post summarisation via the sidecar's E4B Gemma slot (concatenated into `text_vec` for retrieval) |
| Video summary | `ai.video.provider` | `type=video` post summarisation via the sidecar's E4B Gemma slot (concatenated into `text_vec` for retrieval) |

Each capability independently picks its provider. You can, for example, run
OpenAI embeddings, Anthropic chat summaries, Stability image generation and
OpenAI moderation at the same time.

## Provider keys and recommended defaults

`ai.<capability>.model` / `dimensions` / `temperature` / `max-tokens` override
per-capability. Leave them blank to get the defaults below (from
[`DefaultModelCatalog`](../src/main/java/com/intelligenta/socialgraph/ai/DefaultModelCatalog.java)).

| Provider key | Embedding default | Chat default (✓ vision) | Image default | Moderation default |
|:---|:---|:---|:---|:---|
| `sidecar` (default) | siglip2-giant (1152d) | gemma-4-31B-it ✓ | — | — |
| `openai` | `text-embedding-3-small` (1536d) | `gpt-5.4-mini-2026-03-17` ✓ | `dall-e-3` | `omni-moderation-latest` |
| `azure-openai` | `text-embedding-3-small` (1536d) | `gpt-5.4-mini-2026-03-17` ✓ | `dall-e-3` | — |
| `anthropic` | — | `claude-opus-4-7` ✓ | — | — |
| `google-genai` | — | `gemini-3.1-flash-lite-preview` ✓ | — | — |
| `google-genai-embedding` | `text-embedding-005` (768d) | — | — | — |
| `vertex-ai-gemini` | — | `gemini-3.1-flash-lite-preview` ✓ | — | — |
| `vertex-ai-embedding` | `text-embedding-005` (768d) | — | — | — |
| `bedrock-converse` | — | `anthropic.claude-opus-4-7` ✓ | — | — |
| `bedrock-titan` | `amazon.titan-embed-text-v2:0` (1024d) | — | — | — |
| `bedrock-cohere` | `cohere.embed-english-v3.0` (1024d) | — | — | — |
| `ollama` | `nomic-embed-text` (768d) | `gemma4:e2b` ✓ | — | — |
| `mistral-ai` | `mistral-embed` (1024d) | `mistral-large-latest` | — | `mistral-moderation-latest` |
| `stability-ai` | — | — | `stable-diffusion-xl-1024-v1-0` | — |
| `zhipuai` | `embedding-3` (2048d) | `glm-4-plus` | `cogview-3` | — |
| `minimax` | `embo-01` (1536d) | `abab6.5-chat` | — | — |
| `oci-genai` | `cohere.embed-multilingual-v3.0` (1024d) | `cohere.command-r-plus` | — | — |
| `deepseek` | — | `deepseek-chat` | — | — |
| `postgresml` | `intfloat/e5-small` (384d) | — | — | — |
| `transformers` (local ONNX) | `all-MiniLM-L6-v2` (384d) | — | — | — |
| `none` | disabled (throws) | no-op (caption passthrough) | 503 | allowed (no check) |

`—` means the provider doesn't expose that capability in Spring AI 2.0.
Selecting an unsupported pair (e.g. `ai.image.provider=anthropic`) fails
startup with a clear error rather than silently falling back.

### Audio / video defaults

The sidecar hosts a second Gemma slot (`ENABLE_AUDIO_VIDEO_SUMMARY=true`)
loaded with `google/gemma-4-E4B-it`, which handles both capabilities.
Cloud alternatives resolve through `DefaultModelCatalog` but their Spring-AI
bean wiring is pending a follow-up refactor of `VisualSummarizerConfig` to
use provider-specific `ChatModel` types (the current config's generic
`ChatModel` autowire becomes ambiguous once two provider auto-configs are
active simultaneously). Until that lands, only `sidecar` and `none` are
concretely wired for `ai.audio.provider` / `ai.video.provider`.

| Provider key | Audio default | Video default |
|:---|:---|:---|
| `sidecar` (default) | `google/gemma-4-E4B-it` | `google/gemma-4-E4B-it` |
| `openai` | `whisper-1` *(wiring pending)* | — |
| `azure-openai` | `whisper-1` *(wiring pending)* | — |
| `google-genai` | `gemini-3.1-flash-lite-preview` *(wiring pending)* | `gemini-3.1-flash-lite-preview` *(wiring pending)* |
| `vertex-ai-gemini` | `gemini-3.1-flash-lite-preview` *(wiring pending)* | `gemini-3.1-flash-lite-preview` *(wiring pending)* |
| `none` | disabled (caption passthrough) | disabled (caption passthrough) |

## Minimal configurations

### Default (no external API keys)

```
# Nothing to set — the Rust sidecar serves embedding + chat.
docker compose --profile sidecar up -d
./mvnw spring-boot:run
```

### OpenAI for everything

```bash
export OPENAI_API_KEY=sk-...
export AI_EMBEDDING_PROVIDER=openai
export AI_CHAT_PROVIDER=openai
export AI_IMAGE_PROVIDER=openai
export AI_MODERATION_PROVIDER=openai
```

### Mix providers

```bash
# Anthropic chat + Vertex embeddings + Stability image gen + OpenAI moderation
export ANTHROPIC_API_KEY=sk-ant-...
export AI_CHAT_PROVIDER=anthropic

export GCP_PROJECT_ID=my-project
export AI_EMBEDDING_PROVIDER=vertex-ai-embedding

export STABILITY_API_KEY=sk-stab-...
export AI_IMAGE_PROVIDER=stability-ai

export OPENAI_API_KEY=sk-...
export AI_MODERATION_PROVIDER=openai
```

### Swap the default model within a provider

```bash
export AI_EMBEDDING_PROVIDER=openai
export AI_EMBEDDING_MODEL=text-embedding-3-large     # overrides the curated default
export AI_EMBEDDING_DIMENSIONS=3072                  # OpenAI-specific truncation
```

### Point OpenAI starter at a compatible endpoint (Groq, Together, vLLM, …)

```bash
export AI_CHAT_PROVIDER=openai
export AI_CHAT_MODEL=llama-3.3-70b-versatile
export OPENAI_API_KEY=gsk-...
export SPRING_AI_OPENAI_BASE_URL=https://api.groq.com/openai
```

## Per-provider configuration keys

Beyond the `ai.*` block above, everything under `spring.ai.<provider>.*` flows
through unchanged from Spring AI's own docs. Common keys:

| Provider | Key | Purpose |
|---|---|---|
| OpenAI | `spring.ai.openai.api-key` | API key |
| OpenAI | `spring.ai.openai.base-url` | Point at Azure / Groq / Together / vLLM |
| Azure OpenAI | `spring.ai.azure.openai.endpoint` | Azure endpoint |
| Azure OpenAI | `spring.ai.azure.openai.api-key` | API key |
| Azure OpenAI | `spring.ai.azure.openai.chat.options.deployment-name` | Azure deployment name |
| Anthropic | `spring.ai.anthropic.api-key` | Anthropic API key |
| Vertex AI | `spring.ai.vertex.ai.gemini.project-id` | GCP project |
| Vertex AI | `spring.ai.vertex.ai.gemini.location` | Region (e.g. `us-central1`) |
| Ollama | `spring.ai.ollama.base-url` | Ollama host (default `http://localhost:11434`) |
| Bedrock | `spring.ai.bedrock.aws.region` + `access-key` + `secret-key` | AWS creds / region |
| OCI GenAI | `spring.ai.oci.genai.compartment` + `serving-mode` | OCI compartment OCID |
| Watsonx AI | *(not shipped in Spring AI 2.0.0-M4; use Bedrock Converse as a workaround)* |  |

Full list: <https://docs.spring.io/spring-ai/reference/api/chatmodel.html>.

## Vector index and the 7-day window

The RediSearch index name encodes the active provider + dimension:

```
idx:post:embedding:<provider>:<dim>
```

So `idx:post:embedding:sidecar:1152` today becomes
`idx:post:embedding:openai:1536` if you flip to OpenAI with default dim.
Switching providers creates a second index; old data stays in the old one
until the 8-day TTL on its hash keys ticks past. No manual index drop needed.

## Troubleshooting

- **`ai_provider_failed` 502 from `/api/search/*`** — the active embedding
  provider's API call failed. Check the provider's credentials; run
  `curl -fsS "https://api.openai.com/v1/models" -H "Authorization: Bearer $OPENAI_API_KEY"`
  (or equivalent) to confirm reachability.
- **`content_blocked` 400 from `POST /api/status`** — the active moderation
  provider flagged the post content. The error description lists the
  triggered category (`hate`, `self_harm`, ...).
- **`sidecar_unavailable` 502** — the Rust sidecar isn't running; bring it up
  with `docker compose --profile sidecar up -d embedding-sidecar` or set
  `AI_EMBEDDING_PROVIDER` and `AI_CHAT_PROVIDER` to a Spring AI provider to
  bypass it.
- **Startup fails with `Endpoint must not be empty`** — an un-excluded Spring
  AI autoconfig tried to build a client without required properties. The
  `spring.autoconfigure.exclude` block in `application.yml` should prevent
  this for every shipped provider; if you add a new provider starter, add its
  autoconfig class name to that list.
