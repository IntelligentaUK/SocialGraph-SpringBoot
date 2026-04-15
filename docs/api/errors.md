# API errors

All errors are normalized to the same JSON shape by
[`GlobalExceptionHandler`](../../src/main/java/com/intelligenta/socialgraph/exception/GlobalExceptionHandler.java):

```json
{
  "error": "<stable_code>",
  "error_description": "<human message>"
}
```

`error` is a stable machine-readable code. `error_description` is a human
readable message that can vary per occurrence.

## Error code → HTTP status

| Code | HTTP | Typed exception | When |
|------|------|-----------------|------|
| `cannot_register` | 400 | `AlreadyRegisteredException` | Registration with a username that already exists. |
| `invalid_grant` | 401 | `InvalidCredentialsException` | Login with an unknown username or wrong password. |
| `user_not_found` | 400 | `UserNotFoundException` | Target UID / username does not resolve to a known account. |
| `cannot_follow` | 400 | `AlreadyFollowingException`, `CannotFollowSelfException` | Follow a user already followed, or any graph action on yourself. |
| `cannot_unfollow` | 400 | `NotFollowingException` | Unfollow a user not currently followed. |
| `cannot_perform_action` | 400 | `PostNotFoundException` | Post ID does not exist. |
| `auth_error` | 401 | `InvalidTokenException`, `AuthenticationException` | Missing or invalid Bearer token on an authenticated route. |
| `access_denied` | 403 | `AccessDeniedException` | Caller is authenticated but not authorized — e.g. editing someone else's post. |
| `incomplete_request` | 400 | `MissingServletRequestParameterException`, `MethodArgumentNotValidException` | A required query parameter is missing or bean validation failed. |
| `invalid_request_body` | 400 | `HttpMessageNotReadableException` | JSON body could not be parsed. |
| `invalid_image_payload` | 400 | `SocialGraphException` with this code | Image bytes are empty, base64 is malformed, `cut >= width`, or ImageIO cannot decode. |
| `unsupported_image_type` | 400 | `SocialGraphException` with this code | MIME type is not JPEG / PNG / WebP. |
| `media_upload_failed` | 400 | `SocialGraphException` with this code | Provider-specific upload error. |
| `storage_unavailable` | 400 | `SocialGraphException` with this code | Storage provider client is null (not initialized) or the operation could not reach the store. |
| `session_key_failure` | 400 | `SocialGraphException` with this code | `KeyPairGenerator("RSA")` failed during `/api/session` bootstrap. |
| `internal_server_error` | 500 | `Exception` (catch-all) | Any unhandled exception. |

Anything thrown as a plain `SocialGraphException` with a custom code falls
through to the generic `SocialGraphException` handler and comes out as `400`
with whatever `errorCode` the exception carries.

## Special cases

Two endpoints deliberately break the normalized error shape:

- **`GET /api/activate`** on a bad token returns `400` with body
  `{ "activated": "false" }` rather than the standard `ErrorResponse`. This is
  intentional — activation lives in a success-or-not contract independent of the
  error plumbing.
- **Spring validation (`@Valid` on `LqUploadRequest`)** produces messages
  constructed from the first field error:
  `"imageBase64 must not be blank"`, `"cut must be greater than 0"`, etc.
  `error` is always `incomplete_request`.

## Example error responses

```json
// POST /api/register with a taken username
{ "error": "cannot_register", "error_description": "Username already registered" }
```

```json
// POST /api/login with a bad password
{ "error": "invalid_grant", "error_description": "Invalid username or password" }
```

```json
// GET /api/me without a Bearer token
{ "error": "auth_error", "error_description": "Authentication required" }
```

```json
// PATCH /api/posts/{someoneElsePostId}
{ "error": "access_denied", "error_description": "Access denied" }
```

```json
// POST /api/lq/upload with a GIF
{ "error": "unsupported_image_type", "error_description": "Only JPEG, PNG, and WebP images are supported" }
```

## Related

- [Authentication](../authentication.md) for how 401s are produced.
- [API reference index](README.md) for endpoint-by-endpoint error columns.
- [`GlobalExceptionHandler`](../../src/main/java/com/intelligenta/socialgraph/exception/GlobalExceptionHandler.java)
  is the authoritative source for this mapping.
