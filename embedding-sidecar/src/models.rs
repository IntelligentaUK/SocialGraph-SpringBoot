//! Model interfaces used by the HTTP layer.
//!
//! The real candle-backed implementations live behind the `models` Cargo
//! feature. Without it (the default build) we use a deterministic stub that
//! returns fake-but-shape-correct responses. This lets the rest of the
//! system — the Java worker, the Redis Streams pipeline, the RediSearch
//! index, end-to-end tests — be exercised without a 20 GB model download
//! or a GPU.
//!
//! # SigLIP-2
//! - `embed_text(text) -> Vec<f32>` of length [`Config::vector_dim`]
//! - `embed_image_and_text(url, description) -> Vec<f32>` fused via
//!   `l2norm((l2norm(img) + l2norm(txt)) / 2)` leveraging SigLIP-2's shared
//!   image/text embedding space.
//!
//! # Gemma VLM
//! - `summarize(status_text, image_urls) -> String` produces a concise
//!   visual summary suitable for downstream retrieval.
//!
//! Both traits are synchronous at the call site; callers that want to keep
//! the Tokio runtime responsive should wrap calls in
//! `tokio::task::spawn_blocking`.

use std::sync::atomic::{AtomicUsize, Ordering};

/// SigLIP-2 image+text encoder surface.
pub trait SiglipModel: Send + Sync {
    fn embed_text(&self, text: &str) -> anyhow::Result<Vec<f32>>;
    fn embed_image_and_text(&self, image_url: &str, text_description: &str)
        -> anyhow::Result<Vec<f32>>;
}

/// Gemma VLM surface.
pub trait GemmaModel: Send + Sync {
    fn summarize(&self, status_text: &str, image_urls: &[String])
        -> anyhow::Result<String>;
}

/// Gemma edge-variant surface for audio + video summarization.
pub trait GemmaEvModel: Send + Sync {
    fn summarize_audio(&self, status_text: &str, media_url: &str)
        -> anyhow::Result<String>;
    fn summarize_video(&self, status_text: &str, media_url: &str)
        -> anyhow::Result<String>;
}

// ---------------------------------------------------------------------------
// Stub implementations (default build)
// ---------------------------------------------------------------------------

/// Deterministic stub that returns unit-norm vectors seeded off the input.
/// Useful in tests and when iterating on the rest of the stack without
/// downloading real weights.
pub struct StubSiglip {
    pub dim: usize,
    counter: AtomicUsize,
}

impl StubSiglip {
    pub fn new(dim: usize) -> Self {
        Self {
            dim,
            counter: AtomicUsize::new(0),
        }
    }

    fn seeded_unit_vec(&self, seed: u64) -> Vec<f32> {
        // Hash-ish PRNG: xorshift. Enough for deterministic variety without
        // pulling in an RNG crate.
        let mut state: u64 = seed ^ 0x9E37_79B9_7F4A_7C15;
        let mut v = Vec::with_capacity(self.dim);
        let mut sum_sq = 0.0_f64;
        for _ in 0..self.dim {
            state ^= state << 13;
            state ^= state >> 7;
            state ^= state << 17;
            let f = ((state as u32) as f32 / u32::MAX as f32) - 0.5;
            sum_sq += (f as f64) * (f as f64);
            v.push(f);
        }
        let norm = (sum_sq.sqrt() as f32).max(1e-12);
        for f in &mut v {
            *f /= norm;
        }
        v
    }
}

impl SiglipModel for StubSiglip {
    fn embed_text(&self, text: &str) -> anyhow::Result<Vec<f32>> {
        let seed = fnv1a64(text.as_bytes());
        Ok(self.seeded_unit_vec(seed))
    }

    fn embed_image_and_text(&self, image_url: &str, text_description: &str)
        -> anyhow::Result<Vec<f32>> {
        let seed = fnv1a64(image_url.as_bytes()) ^ fnv1a64(text_description.as_bytes());
        let _ = self.counter.fetch_add(1, Ordering::Relaxed);
        Ok(self.seeded_unit_vec(seed))
    }
}

pub struct StubGemma;

impl GemmaModel for StubGemma {
    fn summarize(&self, status_text: &str, image_urls: &[String])
        -> anyhow::Result<String> {
        Ok(format!(
            "[stub summary] caption=\"{}\"; images={}",
            status_text.replace('\n', " "),
            image_urls.len()
        ))
    }
}

pub struct StubGemmaEv;

impl GemmaEvModel for StubGemmaEv {
    fn summarize_audio(&self, status_text: &str, media_url: &str)
        -> anyhow::Result<String> {
        Ok(format!(
            "[stub audio summary] caption=\"{}\"; url={}",
            status_text.replace('\n', " "),
            media_url
        ))
    }

    fn summarize_video(&self, status_text: &str, media_url: &str)
        -> anyhow::Result<String> {
        Ok(format!(
            "[stub video summary] caption=\"{}\"; url={}",
            status_text.replace('\n', " "),
            media_url
        ))
    }
}

fn fnv1a64(bytes: &[u8]) -> u64 {
    let mut h: u64 = 0xcbf2_9ce4_8422_2325;
    for &b in bytes {
        h ^= b as u64;
        h = h.wrapping_mul(0x0000_0100_0000_01B3);
    }
    h
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stub_text_vector_is_unit_norm() {
        let m = StubSiglip::new(1152);
        let v = m.embed_text("hello world").unwrap();
        assert_eq!(v.len(), 1152);
        let n: f64 = v.iter().map(|x| (*x as f64) * (*x as f64)).sum::<f64>().sqrt();
        assert!((n - 1.0).abs() < 1e-4, "not unit norm: {n}");
    }

    #[test]
    fn stub_text_vector_is_deterministic() {
        let m = StubSiglip::new(128);
        assert_eq!(m.embed_text("x").unwrap(), m.embed_text("x").unwrap());
    }

    #[test]
    fn stub_text_vector_differs_by_input() {
        let m = StubSiglip::new(128);
        assert_ne!(m.embed_text("x").unwrap(), m.embed_text("y").unwrap());
    }

    #[test]
    fn stub_gemma_summary_mentions_image_count() {
        let s = StubGemma.summarize("a caption", &["a".into(), "b".into()]).unwrap();
        assert!(s.contains("images=2"));
    }
}
