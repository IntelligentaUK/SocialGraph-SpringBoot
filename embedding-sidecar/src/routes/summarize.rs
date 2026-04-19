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

#[derive(Debug, Deserialize)]
pub struct SummarizeAvRequest {
    pub status_text: String,
    pub media_url: String,
}

pub async fn summarize_audio(
    State(state): State<Arc<AppState>>,
    Json(req): Json<SummarizeAvRequest>,
) -> AppResult<Json<SummarizeResponse>> {
    if req.media_url.trim().is_empty() {
        return Err(AppError::InvalidRequest(
            "media_url must be non-empty".into(),
        ));
    }

    let gemma_ev = state
        .gemma_ev
        .load_full()
        .ok_or(AppError::ModelNotLoaded)?;

    let status_text = req.status_text.clone();
    let media_url = req.media_url.clone();
    let summary = tokio::task::spawn_blocking(move || {
        gemma_ev.summarize_audio(&status_text, &media_url)
    })
    .await
    .map_err(|e| AppError::Inference(format!("join error: {e}")))?
    .map_err(|e| AppError::Inference(e.to_string()))?;

    Ok(Json(SummarizeResponse { summary }))
}

pub async fn summarize_video(
    State(state): State<Arc<AppState>>,
    Json(req): Json<SummarizeAvRequest>,
) -> AppResult<Json<SummarizeResponse>> {
    if req.media_url.trim().is_empty() {
        return Err(AppError::InvalidRequest(
            "media_url must be non-empty".into(),
        ));
    }

    let gemma_ev = state
        .gemma_ev
        .load_full()
        .ok_or(AppError::ModelNotLoaded)?;

    let status_text = req.status_text.clone();
    let media_url = req.media_url.clone();
    let summary = tokio::task::spawn_blocking(move || {
        gemma_ev.summarize_video(&status_text, &media_url)
    })
    .await
    .map_err(|e| AppError::Inference(format!("join error: {e}")))?
    .map_err(|e| AppError::Inference(e.to_string()))?;

    Ok(Json(SummarizeResponse { summary }))
}
