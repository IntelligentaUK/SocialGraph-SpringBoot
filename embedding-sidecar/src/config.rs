use std::path::PathBuf;

/// Runtime configuration, populated from environment variables.
///
/// All defaults match the values documented in the SocialGraph plan so that a
/// bare `cargo run` with no envs still produces a working sidecar against a
/// standard local setup.
#[allow(dead_code)] // several fields are consumed only by the `models` feature's real loaders
#[derive(Clone, Debug)]
pub struct Config {
    pub port: u16,
    pub model_cache_dir: PathBuf,
    pub hf_token: Option<String>,
    pub max_images_per_request: usize,
    pub image_fetch_timeout_s: u64,
    pub max_image_bytes: u64,
    pub siglip_model_id: String,
    pub gemma_model_id: String,
    pub gemma_ev_model_id: String,
    pub enable_audio_video_summary: bool,
    pub max_audio_seconds: u64,
    pub max_video_seconds: u64,
    pub vector_dim: usize,
}

impl Config {
    #[allow(dead_code)] // smoke tests construct `Config` directly
    pub fn from_env() -> anyhow::Result<Self> {
        let env = |k: &str, d: &str| std::env::var(k).unwrap_or_else(|_| d.to_string());
        Ok(Self {
            port: env("SIDECAR_PORT", "8000")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid SIDECAR_PORT: {e}"))?,
            model_cache_dir: PathBuf::from(env("MODEL_CACHE_DIR", "/app/models")),
            hf_token: std::env::var("HF_TOKEN").ok().filter(|s| !s.is_empty()),
            max_images_per_request: env("MAX_IMAGES_PER_REQUEST", "5")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid MAX_IMAGES_PER_REQUEST: {e}"))?,
            image_fetch_timeout_s: env("IMAGE_FETCH_TIMEOUT_S", "10")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid IMAGE_FETCH_TIMEOUT_S: {e}"))?,
            max_image_bytes: env("MAX_IMAGE_BYTES", "10485760")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid MAX_IMAGE_BYTES: {e}"))?,
            siglip_model_id: env("SIGLIP_MODEL_ID", "google/siglip2-giant-opt-patch16-384"),
            gemma_model_id: env("GEMMA_MODEL_ID", "google/gemma-4-31B-it"),
            gemma_ev_model_id: env("GEMMA_EV_MODEL_ID", "google/gemma-4-E4B-it"),
            enable_audio_video_summary: env("ENABLE_AUDIO_VIDEO_SUMMARY", "false")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid ENABLE_AUDIO_VIDEO_SUMMARY: {e}"))?,
            max_audio_seconds: env("MAX_AUDIO_SECONDS", "600")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid MAX_AUDIO_SECONDS: {e}"))?,
            max_video_seconds: env("MAX_VIDEO_SECONDS", "300")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid MAX_VIDEO_SECONDS: {e}"))?,
            vector_dim: env("VECTOR_DIM", "1152")
                .parse()
                .map_err(|e| anyhow::anyhow!("invalid VECTOR_DIM: {e}"))?,
        })
    }
}
