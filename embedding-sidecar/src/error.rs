use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;

/// Unified error type for every handler in the sidecar. Maps to a stable
/// JSON shape so the Java caller can pattern-match on the `code` field.
#[derive(thiserror::Error, Debug)]
#[allow(dead_code)]
pub enum AppError {
    #[error("invalid request: {0}")]
    InvalidRequest(String),

    #[error("image fetch failed: {0}")]
    ImageFetch(#[from] reqwest::Error),

    #[error("image decode: {0}")]
    ImageDecode(#[from] image::ImageError),

    #[error("model not loaded")]
    ModelNotLoaded,

    #[error("inference: {0}")]
    Inference(String),

    #[error("tokenizer: {0}")]
    Tokenizer(String),

    #[error("io: {0}")]
    Io(#[from] std::io::Error),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, code) = match &self {
            Self::InvalidRequest(_) => (StatusCode::BAD_REQUEST, "invalid_request"),
            Self::ImageFetch(_) | Self::ImageDecode(_) => {
                (StatusCode::BAD_GATEWAY, "image_fetch_failed")
            }
            Self::ModelNotLoaded => (StatusCode::SERVICE_UNAVAILABLE, "model_loading"),
            _ => (StatusCode::INTERNAL_SERVER_ERROR, "internal_error"),
        };
        tracing::error!(error = %self, "request failed");
        (
            status,
            Json(json!({"error": self.to_string(), "code": code})),
        )
            .into_response()
    }
}

pub type AppResult<T> = Result<T, AppError>;
