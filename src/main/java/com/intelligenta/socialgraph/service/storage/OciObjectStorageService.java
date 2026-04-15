package com.intelligenta.socialgraph.service.storage;

import com.intelligenta.socialgraph.config.StorageProperties;
import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Oracle Cloud Object Storage implementation.
 */
@Service
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "oci")
public class OciObjectStorageService extends AbstractObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(OciObjectStorageService.class);

    private ObjectStorageClient objectStorageClient;

    public OciObjectStorageService(StorageProperties properties) {
        super(properties);
    }

    @PostConstruct
    public void init() {
        StorageProperties.Oci oci = getProperties().getOci();
        try {
            ConfigFileAuthenticationDetailsProvider provider;
            if (StringUtils.hasText(oci.getConfigFile())) {
                provider = new ConfigFileAuthenticationDetailsProvider(oci.getConfigFile(), oci.getProfile());
            } else {
                provider = new ConfigFileAuthenticationDetailsProvider(oci.getProfile());
            }

            objectStorageClient = new ObjectStorageClient(provider);
            if (StringUtils.hasText(oci.getEndpoint())) {
                objectStorageClient.setEndpoint(oci.getEndpoint());
            } else if (StringUtils.hasText(oci.getRegion())) {
                objectStorageClient.setRegion(Region.fromRegionId(oci.getRegion()));
            }
        } catch (Exception e) {
            log.warn("Failed to initialize OCI Object Storage client: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (objectStorageClient != null) {
            objectStorageClient.close();
        }
    }

    @Override
    public String provider() {
        return "oci";
    }

    @Override
    public StorageUploadTarget createSignedUploadTarget(String extension, String contentType) {
        ensureClient();

        try {
            String objectKey = nextObjectKey(extension);
            Date expiry = new Date(System.currentTimeMillis() + (getProperties().getSignedUrlTtlSeconds() * 1000L));

            CreatePreauthenticatedRequestDetails details = CreatePreauthenticatedRequestDetails.builder()
                .name("socialgraph-" + objectKey)
                .objectName(objectKey)
                .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite)
                .timeExpires(expiry)
                .build();

            CreatePreauthenticatedRequestResponse response = objectStorageClient.createPreauthenticatedRequest(
                CreatePreauthenticatedRequestRequest.builder()
                    .namespaceName(getProperties().getOci().getNamespaceName())
                    .bucketName(getProperties().getOci().getBucketName())
                    .createPreauthenticatedRequestDetails(details)
                    .build()
            );

            Map<String, String> headers = new LinkedHashMap<>();
            if (StringUtils.hasText(contentType)) {
                headers.put("Content-Type", contentType);
            }

            String uploadUrl = baseEndpoint() + response.getPreauthenticatedRequest().getAccessUri();
            return buildUploadTarget(objectKey, objectUrl(objectKey), uploadUrl, headers);
        } catch (Exception e) {
            log.error("Failed to create OCI pre-authenticated upload target", e);
            throw new SocialGraphException("storage_unavailable", "Unable to create an upload target");
        }
    }

    @Override
    public StoredObject upload(byte[] bytes, String extension, String contentType) {
        ensureClient();

        try {
            String objectKey = nextObjectKey(extension);
            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .namespaceName(getProperties().getOci().getNamespaceName())
                .bucketName(getProperties().getOci().getBucketName())
                .objectName(objectKey)
                .contentLength((long) bytes.length)
                .putObjectBody(new ByteArrayInputStream(bytes));

            if (StringUtils.hasText(contentType)) {
                builder.contentType(contentType);
            }

            objectStorageClient.putObject(builder.build());
            return buildStoredObject(objectKey, objectUrl(objectKey), contentType);
        } catch (Exception e) {
            log.error("Failed to upload object to OCI", e);
            throw new SocialGraphException("media_upload_failed", "Unable to upload the image");
        }
    }

    @Override
    public void download(String objectKey, OutputStream outputStream) {
        ensureClient();

        try {
            GetObjectResponse response = objectStorageClient.getObject(
            GetObjectRequest.builder()
                .namespaceName(getProperties().getOci().getNamespaceName())
                .bucketName(getProperties().getOci().getBucketName())
                .objectName(objectKey)
                .build()
            );
            try (var inputStream = response.getInputStream()) {
                inputStream.transferTo(outputStream);
            }
        } catch (Exception e) {
            log.error("Failed to download object from OCI: {}", objectKey, e);
            throw new SocialGraphException("storage_unavailable", "Unable to download the object");
        }
    }

    private String objectUrl(String objectKey) {
        return baseEndpoint()
            + "/n/"
            + getProperties().getOci().getNamespaceName()
            + "/b/"
            + getProperties().getOci().getBucketName()
            + "/o/"
            + encodeObjectKey(objectKey);
    }

    private String baseEndpoint() {
        StorageProperties.Oci oci = getProperties().getOci();
        if (StringUtils.hasText(oci.getEndpoint())) {
            return oci.getEndpoint().endsWith("/") ? oci.getEndpoint().substring(0, oci.getEndpoint().length() - 1) : oci.getEndpoint();
        }
        if (!StringUtils.hasText(oci.getRegion())) {
            throw new SocialGraphException("storage_unavailable", "OCI region or endpoint must be configured");
        }
        return "https://objectstorage." + oci.getRegion() + ".oraclecloud.com";
    }

    private void ensureClient() {
        if (objectStorageClient == null) {
            throw new SocialGraphException("storage_unavailable", "Object storage is not configured");
        }
    }
}
