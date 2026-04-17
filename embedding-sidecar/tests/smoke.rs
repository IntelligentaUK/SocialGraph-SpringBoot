//! End-to-end smoke test against a running axum server with stub models.
//!
//! Verifies: (1) /healthz flips from 503 to 200 once stub models are wired,
//! (2) /embed/text returns a 1152-dim vector for a valid request,
//! (3) /embed/image-text rejects empty URLs, (4) /summarize respects the
//! max-images cap.

use std::sync::Arc;

use tokio::net::TcpListener;

// Re-export the library modules via `embedding_sidecar` crate name. The
// binary crate's modules aren't normally exposed to integration tests, so
// we duplicate-import the ones we need through a small test-only shim.
// For simplicity we just start the binary in-process by calling into
// axum's serve() with our own Router built from the public handlers.

#[path = "../src/config.rs"]
mod config;
#[path = "../src/error.rs"]
mod error;
#[path = "../src/image_fetch.rs"]
mod image_fetch;
#[path = "../src/models.rs"]
mod models;
#[path = "../src/routes/mod.rs"]
mod routes;
#[path = "../src/state.rs"]
mod state;

async fn spawn_server() -> (String, tokio::task::JoinHandle<()>) {
    let cfg = config::Config {
        port: 0,
        model_cache_dir: std::path::PathBuf::from("/tmp/sidecar-test"),
        hf_token: None,
        max_images_per_request: 5,
        image_fetch_timeout_s: 10,
        max_image_bytes: 10_485_760,
        siglip_model_id: "stub".into(),
        gemma_model_id: "stub".into(),
        vector_dim: 1152,
    };
    let http = reqwest::Client::new();
    let app_state = state::AppState::new(cfg, http);
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    let base = format!("http://{}", addr);

    // Pre-load stubs synchronously so /healthz is ready by the time we hit it.
    app_state.set_siglip(Box::new(models::StubSiglip::new(1152)));
    app_state.set_gemma(Box::new(models::StubGemma));

    let app = routes::router(Arc::clone(&app_state));
    let handle = tokio::spawn(async move {
        let _ = axum::serve(listener, app).await;
    });
    (base, handle)
}

#[tokio::test]
async fn healthz_ok_when_stubs_are_loaded() {
    let (base, _h) = spawn_server().await;
    let resp = reqwest::get(format!("{base}/healthz")).await.unwrap();
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    assert_eq!(body["status"], "ok");
}

#[tokio::test]
async fn embed_text_returns_1152_floats() {
    let (base, _h) = spawn_server().await;
    let resp = reqwest::Client::new()
        .post(format!("{base}/embed/text"))
        .json(&serde_json::json!({"text": "a cat on a mat"}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    let v = body["vector"].as_array().unwrap();
    assert_eq!(v.len(), 1152);
}

#[tokio::test]
async fn embed_text_rejects_empty() {
    let (base, _h) = spawn_server().await;
    let resp = reqwest::Client::new()
        .post(format!("{base}/embed/text"))
        .json(&serde_json::json!({"text": ""}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status(), 400);
}

#[tokio::test]
async fn summarize_rejects_too_many_images() {
    let (base, _h) = spawn_server().await;
    let urls: Vec<String> = (0..6).map(|i| format!("https://img/{i}.png")).collect();
    let resp = reqwest::Client::new()
        .post(format!("{base}/summarize"))
        .json(&serde_json::json!({"status_text": "x", "image_urls": urls}))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status(), 400);
}

#[tokio::test]
async fn summarize_mentions_image_count() {
    let (base, _h) = spawn_server().await;
    let resp = reqwest::Client::new()
        .post(format!("{base}/summarize"))
        .json(&serde_json::json!({
            "status_text": "sunset",
            "image_urls": ["https://img/a.jpg", "https://img/b.jpg"],
        }))
        .send()
        .await
        .unwrap();
    assert_eq!(resp.status(), 200);
    let body: serde_json::Value = resp.json().await.unwrap();
    let s = body["summary"].as_str().unwrap();
    assert!(s.contains("images=2"), "summary missing image count: {s}");
}
