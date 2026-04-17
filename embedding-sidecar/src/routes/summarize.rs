use std::sync::Arc;

use axum::extract::State;
use axum::Json;
use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct SummarizeRequest {
    pub status_text: String,
    #[serde(default)]
    pub image_urls: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct SummarizeResponse {
    pub summary: String,
}

pub async fn summarize(
    State(state): State<Arc<AppState>>,
    Json(req): Json<SummarizeRequest>,
) -> AppResult<Json<SummarizeResponse>> {
    if req.image_urls.len() > state.config.max_images_per_request {
        return Err(AppError::InvalidRequest(format!(
            "max {} images per request, got {}",
            state.config.max_images_per_request,
            req.image_urls.len()
        )));
    }

    let gemma = state
        .gemma
        .load_full()
        .ok_or(AppError::ModelNotLoaded)?;

    let status_text = req.status_text.clone();
    let image_urls = req.image_urls.clone();
    let summary = tokio::task::spawn_blocking(move || gemma.summarize(&status_text, &image_urls))
        .await
        .map_err(|e| AppError::Inference(format!("join error: {e}")))?
        .map_err(|e| AppError::Inference(e.to_string()))?;

    Ok(Json(SummarizeResponse { summary }))
}
