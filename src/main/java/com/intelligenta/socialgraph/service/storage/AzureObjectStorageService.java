package com.intelligenta.socialgraph.service.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.intelligenta.socialgraph.config.StorageProperties;
import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Azure Blob Storage implementation.
 */
@Service
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "azure", matchIfMissing = true)
public class AzureObjectStorageService extends AbstractObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(AzureObjectStorageService.class);

    private BlobContainerClient containerClient;

    public AzureObjectStorageService(StorageProperties properties) {
        super(properties);
    }

    @PostConstruct
    public void init() {
        StorageProperties.Azure azure = getProperties().getAzure();
        try {
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azure.getConnectionString())
                .buildClient();

            containerClient = blobServiceClient.getBlobContainerClient(azure.getContainerName());
            if (!containerClient.exists()) {
                containerClient.create();
            }
        } catch (Exception e) {
            log.warn("Failed to initialize Azure Blob Storage client: {}", e.getMessage());
        }
    }

    @Override
    public String provider() {
        return "azure";
    }

    @Override
    public StorageUploadTarget createSignedUploadTarget(String extension, String contentType) {
        ensureContainerClient();

        String objectKey = nextObjectKey(extension);
        BlobClient blobClient = containerClient.getBlobClient(objectKey);

        BlobSasPermission permissions = new BlobSasPermission()
            .setCreatePermission(true)
            .setWritePermission(true)
            .setReadPermission(true);
        OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(getProperties().getSignedUrlTtlSeconds());
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permissions);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-ms-blob-type", "BlockBlob");
        if (StringUtils.hasText(contentType)) {
            headers.put("Content-Type", contentType);
        }

        return buildUploadTarget(
            objectKey,
            blobClient.getBlobUrl(),
            blobClient.getBlobUrl() + "?" + blobClient.generateSas(sasValues),
            headers
        );
    }

    @Override
    public StoredObject upload(byte[] bytes, String extension, String contentType) {
        ensureContainerClient();

        try {
            String objectKey = nextObjectKey(extension);
            BlobClient blobClient = containerClient.getBlobClient(objectKey);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
                blobClient.upload(inputStream, bytes.length, true);
            }

            if (StringUtils.hasText(contentType)) {
                blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
            }

            return buildStoredObject(objectKey, blobClient.getBlobUrl(), contentType);
        } catch (Exception e) {
            log.error("Failed to upload blob", e);
            throw new SocialGraphException("media_upload_failed", "Unable to upload the image");
        }
    }

    @Override
    public void download(String objectKey, OutputStream outputStream) {
        ensureContainerClient();

        try {
            BlobClient blobClient = containerClient.getBlobClient(objectKey);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            blobClient.downloadStream(buffer);
            outputStream.write(buffer.toByteArray());
        } catch (Exception e) {
            log.error("Failed to download blob: {}", objectKey, e);
            throw new SocialGraphException("storage_unavailable", "Unable to download the object");
        }
    }

    private void ensureContainerClient() {
        if (containerClient == null) {
            throw new SocialGraphException("storage_unavailable", "Object storage is not configured");
        }
    }
}
