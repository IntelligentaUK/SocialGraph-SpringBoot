package com.intelligenta.socialgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the active object storage provider and its provider-specific settings.
 */
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private Provider provider = Provider.AZURE;
    private long signedUrlTtlSeconds = 300;
    private String objectKeyPrefix = "";
    private Azure azure = new Azure();
    private Gcp gcp = new Gcp();
    private Oci oci = new Oci();

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public long getSignedUrlTtlSeconds() {
        return signedUrlTtlSeconds;
    }

    public void setSignedUrlTtlSeconds(long signedUrlTtlSeconds) {
        this.signedUrlTtlSeconds = signedUrlTtlSeconds;
    }

    public String getObjectKeyPrefix() {
        return objectKeyPrefix;
    }

    public void setObjectKeyPrefix(String objectKeyPrefix) {
        this.objectKeyPrefix = objectKeyPrefix;
    }

    public Azure getAzure() {
        return azure;
    }

    public void setAzure(Azure azure) {
        this.azure = azure;
    }

    public Gcp getGcp() {
        return gcp;
    }

    public void setGcp(Gcp gcp) {
        this.gcp = gcp;
    }

    public Oci getOci() {
        return oci;
    }

    public void setOci(Oci oci) {
        this.oci = oci;
    }

    public enum Provider {
        AZURE,
        GCP,
        OCI
    }

    public static class Azure {
        private String connectionString;
        private String accountName;
        private String accountKey;
        private String containerName;

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public String getAccountName() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }

        public String getAccountKey() {
            return accountKey;
        }

        public void setAccountKey(String accountKey) {
            this.accountKey = accountKey;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }
    }

    public static class Gcp {
        private String projectId;
        private String bucketName;

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }
    }

    public static class Oci {
        private String namespaceName;
        private String bucketName;
        private String region;
        private String configFile;
        private String profile = "DEFAULT";
        private String endpoint;

        public String getNamespaceName() {
            return namespaceName;
        }

        public void setNamespaceName(String namespaceName) {
            this.namespaceName = namespaceName;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getConfigFile() {
            return configFile;
        }

        public void setConfigFile(String configFile) {
            this.configFile = configFile;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
