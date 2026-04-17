use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use moka::future::Cache;

use crate::config::Config;
use crate::models::{GemmaModel, SiglipModel};

/// Shared runtime state handed to every route. `siglip` and `gemma` are
/// lazily populated: initialization may take minutes (weight download +
/// model build), so `/healthz` returns 503 until both are ready.
pub struct AppState {
    pub siglip: arc_swap::ArcSwapOption<Box<dyn SiglipModel>>,
    pub gemma: arc_swap::ArcSwapOption<Box<dyn GemmaModel>>,
    pub siglip_ready: AtomicBool,
    pub gemma_ready: AtomicBool,
    #[allow(dead_code)] // used by image_fetch when the `models` feature is on
    pub http: reqwest::Client,
    #[allow(dead_code)]
    pub image_cache: Cache<String, Arc<image::DynamicImage>>,
    pub config: Config,
}

impl AppState {
    pub fn new(cfg: Config, http: reqwest::Client) -> Arc<Self> {
        Arc::new(Self {
            siglip: arc_swap::ArcSwapOption::empty(),
            gemma: arc_swap::ArcSwapOption::empty(),
            siglip_ready: AtomicBool::new(false),
            gemma_ready: AtomicBool::new(false),
            http,
            image_cache: Cache::builder()
                .max_capacity(200)
                .time_to_live(std::time::Duration::from_secs(600))
                .build(),
            config: cfg,
        })
    }

    pub fn set_siglip(&self, m: Box<dyn SiglipModel>) {
        self.siglip.store(Some(Arc::new(m)));
        self.siglip_ready.store(true, Ordering::Release);
    }

    pub fn set_gemma(&self, m: Box<dyn GemmaModel>) {
        self.gemma.store(Some(Arc::new(m)));
        self.gemma_ready.store(true, Ordering::Release);
    }

    pub fn is_ready(&self) -> bool {
        self.siglip_ready.load(Ordering::Acquire) && self.gemma_ready.load(Ordering::Acquire)
    }
}
