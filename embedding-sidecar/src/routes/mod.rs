pub mod embed;
pub mod health;
pub mod summarize;

use std::sync::Arc;

use axum::routing::{get, post};
use axum::Router;

use crate::state::AppState;

pub fn router(state: Arc<AppState>) -> Router {
    Router::new()
        .route("/healthz", get(health::healthz))
        .route("/embed/text", post(embed::embed_text))
        .route("/embed/image-text", post(embed::embed_image_text))
        .route("/summarize", post(summarize::summarize))
        .with_state(state)
}
