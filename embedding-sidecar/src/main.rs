mod config;
mod error;
mod image_fetch;
mod models;
mod routes;
mod state;

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env()
            .unwrap_or_else(|_| EnvFilter::new("info,embedding_sidecar=debug")))
        .init();

    let cfg = config::Config::from_env()?;
    tracing::info!(?cfg.port, ?cfg.vector_dim, "starting embedding-sidecar");

    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(cfg.image_fetch_timeout_s))
        .user_agent(concat!(
            "socialgraph-embedding-sidecar/",
            env!("CARGO_PKG_VERSION")
        ))
        .build()?;

    let state = state::AppState::new(cfg.clone(), http);

    // Kick off model loading on a blocking thread. /healthz reports 503 until
    // both Once cells are populated. In the default (stub) build this is
    // instant; the `models` feature swaps in candle-backed loaders.
    load_models_async(state.clone(), cfg.clone());

    let addr: SocketAddr = ([0, 0, 0, 0], cfg.port).into();
    let listener = tokio::net::TcpListener::bind(addr).await?;
    tracing::info!(%addr, "listening");

    let app = routes::router(state);
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    Ok(())
}

fn load_models_async(state: Arc<state::AppState>, cfg: config::Config) {
    let s1 = state.clone();
    let cfg1 = cfg.clone();
    tokio::task::spawn_blocking(move || {
        let siglip: Box<dyn models::SiglipModel> = load_siglip(&cfg1);
        s1.set_siglip(siglip);
        tracing::info!("siglip loaded");
    });

    let s2 = state.clone();
    tokio::task::spawn_blocking(move || {
        let gemma: Box<dyn models::GemmaModel> = load_gemma();
        s2.set_gemma(gemma);
        tracing::info!("gemma loaded");
    });

    if cfg.enable_audio_video_summary {
        let s3 = state.clone();
        tokio::task::spawn_blocking(move || {
            let gemma_ev: Box<dyn models::GemmaEvModel> = load_gemma_ev();
            s3.set_gemma_ev(gemma_ev);
            tracing::info!("gemma_ev loaded");
        });
    } else {
        tracing::info!(
            "audio/video summarization disabled (set ENABLE_AUDIO_VIDEO_SUMMARY=true to enable)"
        );
    }
}

#[cfg(not(feature = "models"))]
fn load_siglip(cfg: &config::Config) -> Box<dyn models::SiglipModel> {
    tracing::warn!(
        "running without `models` feature — SigLIP-2 returning deterministic stub vectors (dim={})",
        cfg.vector_dim
    );
    Box::new(models::StubSiglip::new(cfg.vector_dim))
}

#[cfg(feature = "models")]
fn load_siglip(cfg: &config::Config) -> Box<dyn models::SiglipModel> {
    // Real candle-backed loader lives in `siglip::load` when the `models`
    // feature is enabled. For now we still fall back to the stub with a
    // loud warning — wiring in candle 0.8's SigLIP-2 config is a follow-up
    // (see docs/superpowers/plans for details).
    tracing::warn!(
        "`models` feature enabled but real SigLIP-2 loader is TODO; falling back to stub (dim={})",
        cfg.vector_dim
    );
    Box::new(models::StubSiglip::new(cfg.vector_dim))
}

#[cfg(not(feature = "models"))]
fn load_gemma() -> Box<dyn models::GemmaModel> {
    tracing::warn!("running without `models` feature — Gemma returning stub summaries");
    Box::new(models::StubGemma)
}

#[cfg(feature = "models")]
fn load_gemma() -> Box<dyn models::GemmaModel> {
    tracing::warn!("`models` feature enabled but real Gemma loader is TODO; falling back to stub");
    Box::new(models::StubGemma)
}

#[cfg(not(feature = "models"))]
fn load_gemma_ev() -> Box<dyn models::GemmaEvModel> {
    tracing::warn!(
        "running without `models` feature — Gemma-EV returning stub audio/video summaries"
    );
    Box::new(models::StubGemmaEv)
}

#[cfg(feature = "models")]
fn load_gemma_ev() -> Box<dyn models::GemmaEvModel> {
    tracing::warn!(
        "`models` feature enabled but real Gemma-EV loader is TODO; falling back to stub"
    );
    Box::new(models::StubGemmaEv)
}

async fn shutdown_signal() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let term = async {
        if let Ok(mut s) =
            tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        {
            s.recv().await;
        }
    };
    #[cfg(not(unix))]
    let term = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = term => {},
    }
    tracing::info!("shutdown requested");
}
