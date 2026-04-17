package com.intelligenta.socialgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the vector-embedding pipeline: sidecar URL,
 * timeouts, vector dimension, search window, multi-image limits.
 */
@Configuration
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    private String sidecarUrl = "http://localhost:8000";
    private int summarizeTimeoutMs = 30_000;
    private int embedTimeoutMs = 15_000;
    private int vectorDim = 1152;
    private int maxImagesPerPost = 10;
    private int imagesForEmbedding = 5;
    private int searchWindowDays = 7;
    private int dlqMaxRetries = 3;
    private long embeddingTtlSeconds = 691_200L;
    private int searchLimitDefault = 20;
    private int searchLimitMax = 100;

    public String getSidecarUrl() {
        return sidecarUrl;
    }

    public void setSidecarUrl(String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
    }

    public int getSummarizeTimeoutMs() {
        return summarizeTimeoutMs;
    }

    public void setSummarizeTimeoutMs(int summarizeTimeoutMs) {
        this.summarizeTimeoutMs = summarizeTimeoutMs;
    }

    public int getEmbedTimeoutMs() {
        return embedTimeoutMs;
    }

    public void setEmbedTimeoutMs(int embedTimeoutMs) {
        this.embedTimeoutMs = embedTimeoutMs;
    }

    public int getVectorDim() {
        return vectorDim;
    }

    public void setVectorDim(int vectorDim) {
        this.vectorDim = vectorDim;
    }

    public int getMaxImagesPerPost() {
        return maxImagesPerPost;
    }

    public void setMaxImagesPerPost(int maxImagesPerPost) {
        this.maxImagesPerPost = maxImagesPerPost;
    }

    public int getImagesForEmbedding() {
        return imagesForEmbedding;
    }

    public void setImagesForEmbedding(int imagesForEmbedding) {
        this.imagesForEmbedding = imagesForEmbedding;
    }

    public int getSearchWindowDays() {
        return searchWindowDays;
    }

    public void setSearchWindowDays(int searchWindowDays) {
        this.searchWindowDays = searchWindowDays;
    }

    public int getDlqMaxRetries() {
        return dlqMaxRetries;
    }

    public void setDlqMaxRetries(int dlqMaxRetries) {
        this.dlqMaxRetries = dlqMaxRetries;
    }

    public long getEmbeddingTtlSeconds() {
        return embeddingTtlSeconds;
    }

    public void setEmbeddingTtlSeconds(long embeddingTtlSeconds) {
        this.embeddingTtlSeconds = embeddingTtlSeconds;
    }

    public int getSearchLimitDefault() {
        return searchLimitDefault;
    }

    public void setSearchLimitDefault(int searchLimitDefault) {
        this.searchLimitDefault = searchLimitDefault;
    }

    public int getSearchLimitMax() {
        return searchLimitMax;
    }

    public void setSearchLimitMax(int searchLimitMax) {
        this.searchLimitMax = searchLimitMax;
    }
}
