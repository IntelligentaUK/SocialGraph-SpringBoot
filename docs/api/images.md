# Image generation API

Generate images with whichever Spring AI image provider is active. See
[docs/ai.md](../ai.md) for provider selection. When `ai.image.provider=none`
(the default) every call returns `503 service_unavailable`.

## `POST /api/images/generate`

Authenticated. JSON body.

```http
POST /api/images/generate
Authorization: Bearer <token>
Content-Type: application/json

{
  "prompt": "a neon cyberpunk cat on a rainy pier",
  "size": "1024x1024",
  "style": "vivid",
  "n": 1,
  "responseFormat": "url"
}
```

- `prompt` (string, **required**, `@NotBlank`) — the text prompt.
- `size` (string, optional) — provider-specific. OpenAI DALL-E 3: `1024x1024`, `1792x1024`, `1024x1792`.
- `style` (string, optional) — e.g. `vivid` or `natural` for DALL-E 3.
- `n` (int, optional, `1..4`) — number of images to generate. Not all providers support `n > 1`.
- `responseFormat` (string, optional) — `url` (default for DALL-E) or `b64_json` (Stability AI).

### Response

```json
{
  "images": [
    {
      "url": "https://oai.com/blob/.../image.png",
      "mimeType": null
    }
  ],
  "durationMs": 4523,
  "provider": "openai",
  "model": "dall-e-3"
}
```

Either `url` or `base64` is populated per image depending on the provider.

### Errors

| Status | `error` code | Meaning |
|---|---|---|
| `400` | `incomplete_request` | Missing or blank `prompt`, `n` out of range |
| `401` | `auth_error` | Missing or bad token |
| `502` | `ai_provider_failed` | Active image provider returned an error |
| `503` | *(no body)* | `ai.image.provider=none` — feature disabled |

### Example: Stability AI

```bash
export AI_IMAGE_PROVIDER=stability-ai
export STABILITY_API_KEY=sk-...
./mvnw spring-boot:run &

curl -X POST http://localhost:4567/api/images/generate \
    -H "Authorization: Bearer $TOKEN" \
    -H 'content-type: application/json' \
    -d '{"prompt":"a neon cyberpunk cat","responseFormat":"b64_json"}' | jq '.images[0].base64' -r | base64 -d > cat.png
```
