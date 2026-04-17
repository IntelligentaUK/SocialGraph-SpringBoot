use std::sync::Arc;

use axum::extract::State;
use axum::Json;
use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct EmbedTextRequest {
    pub text: String,
}

#[derive(Debug, Deserialize)]
pub struct EmbedImageTextRequest {
    pub image_url: String,
    pub text_description: String,
}

#[derive(Debug, Serialize)]
pub struct EmbedResponse {
    pub vector: Vec<f32>,
}

pub async fn embed_text(
    State(state): State<Arc<AppState>>,
    Json(req): Json<EmbedTextRequest>,
) -> AppResult<Json<EmbedResponse>> {
    if req.text.is_empty() {
        return Err(AppError::InvalidRequest("text must be non-empty".into()));
    }
    let siglip = state
        .siglip
        .load_full()
        .ok_or(AppError::ModelNotLoaded)?;

    let text = req.text.clone();
    let model = siglip.clone();
    let vec = tokio::task::spawn_blocking(move || model.embed_text(&text))
        .await
        .map_err(|e| AppError::Inference(format!("join error: {e}")))?
        .map_err(|e| AppError::Inference(e.to_string()))?;

    Ok(Json(EmbedResponse { vector: vec }))
}

pub async fn embed_image_text(
    State(state): State<Arc<AppState>>,
    Json(req): Json<EmbedImageTextRequest>,
) -> AppResult<Json<EmbedResponse>> {
    if req.image_url.is_empty() {
        return Err(AppError::InvalidRequest("image_url must be non-empty".into()));
    }
    let siglip = state
        .siglip
        .load_full()
        .ok_or(AppError::ModelNotLoaded)?;

    let model = siglip.clone();
    let image_url = req.image_url.clone();
    let desc = req.text_description.clone();
    let vec = tokio::task::spawn_blocking(move || model.embed_image_and_text(&image_url, &desc))
        .await
        .map_err(|e| AppError::Inference(format!("join error: {e}")))?
        .map_err(|e| AppError::Inference(e.to_string()))?;

    Ok(Json(EmbedResponse { vector: vec }))
}
