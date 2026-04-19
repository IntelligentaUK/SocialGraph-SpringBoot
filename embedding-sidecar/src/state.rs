use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use moka::future::Cache;

use crate::config::Config;
use crate::models::{GemmaEvModel, GemmaModel, SiglipModel};

/// Shared runtime state handed to every route. `siglip`, `gemma`, and
/// `gemma_ev` are lazily populated: initialization may take minutes
/// (weight download + model build), so `/healthz` returns 503 until the
/// enabled slots are ready. `gemma_ev` is only loaded when the config
/// opts in via `ENABLE_AUDIO_VIDEO_SUMMARY=true`.
pub struct AppState {
    pub siglip: arc_swap::ArcSwapOption<Box<dyn SiglipModel>>,
    pub gemma: arc_swap::ArcSwapOption<Box<dyn GemmaModel>>,
    pub gemma_ev: arc_swap::ArcSwapOption<Box<dyn GemmaEvModel>>,
    pub siglip_ready: AtomicBool,
    pub gemma_ready: AtomicBool,
    pub gemma_ev_ready: AtomicBool,
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
            gemma_ev: arc_swap::ArcSwapOption::empty(),
            siglip_ready: AtomicBool::new(false),
            gemma_ready: AtomicBool::new(false),
            gemma_ev_ready: AtomicBool::new(false),
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

    pub fn set_gemma_ev(&self, m: Box<dyn GemmaEvModel>) {
        self.gemma_ev.store(Some(Arc::new(m)));
        self.gemma_ev_ready.store(true, Ordering::Release);
    }

    pub fn is_ready(&self) -> bool {
        let base = self.siglip_ready.load(Ordering::Acquire)
            && self.gemma_ready.load(Ordering::Acquire);
        if self.config.enable_audio_video_summary {
            base && self.gemma_ev_ready.load(Ordering::Acquire)
        } else {
            base
        }
    }
}
