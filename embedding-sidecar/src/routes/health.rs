use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;
use axum::Json;
use serde_json::json;

use crate::state::AppState;

pub async fn healthz(
    State(state): State<Arc<AppState>>,
) -> (StatusCode, Json<serde_json::Value>) {
    if state.is_ready() {
        (StatusCode::OK, Json(json!({"status": "ok"})))
    } else {
        (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(json!({
                "status": "loading",
                "siglip_ready": state.siglip_ready.load(std::sync::atomic::Ordering::Acquire),
                "gemma_ready": state.gemma_ready.load(std::sync::atomic::Ordering::Acquire),
            })),
        )
    }
}
