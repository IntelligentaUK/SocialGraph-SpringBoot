use std::sync::Arc;

use bytes::BytesMut;
use futures::StreamExt;
use image::DynamicImage;
use moka::future::Cache;

use crate::error::AppError;

/// Fetches an image by URL, enforcing a byte cap, and caches the decoded
/// [`DynamicImage`] for subsequent calls. The cache is keyed by the raw URL
/// string with a 10-minute TTL, so repeated summarize/embed requests against
/// the same post don't re-download.
#[allow(dead_code)] // used by the real SigLIP-2 image-text embed path (feature = "models")
pub async fn fetch(
    http: &reqwest::Client,
    cache: &Cache<String, Arc<DynamicImage>>,
    url: &str,
    max_bytes: u64,
) -> Result<Arc<DynamicImage>, AppError> {
    if let Some(hit) = cache.get(url).await {
        return Ok(hit);
    }

    let resp = http.get(url).send().await?.error_for_status()?;
    if let Some(len) = resp.content_length() {
        if len > max_bytes {
            return Err(AppError::InvalidRequest(format!(
                "image too large ({} > {} bytes)",
                len, max_bytes
            )));
        }
    }

    let mut buf = BytesMut::with_capacity(1 << 15);
    let mut stream = resp.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        if (buf.len() + chunk.len()) as u64 > max_bytes {
            return Err(AppError::InvalidRequest(format!(
                "image exceeds {} bytes during streaming",
                max_bytes
            )));
        }
        buf.extend_from_slice(&chunk);
    }

    let img = image::load_from_memory(&buf)?;
    let arc = Arc::new(img);
    cache.insert(url.to_string(), arc.clone()).await;
    Ok(arc)
}
