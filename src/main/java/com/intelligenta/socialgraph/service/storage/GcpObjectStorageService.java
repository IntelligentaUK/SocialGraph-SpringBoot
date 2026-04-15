package com.intelligenta.socialgraph.service.storage;

import com.intelligenta.socialgraph.config.StorageProperties;
import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Storage implementation.
 */
@Service
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "gcp")
public class GcpObjectStorageService extends AbstractObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(GcpObjectStorageService.class);

    private Storage storage;

    public GcpObjectStorageService(StorageProperties properties) {
        super(properties);
    }

    @PostConstruct
    public void init() {
        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder();
            if (StringUtils.hasText(getProperties().getGcp().getProjectId())) {
                builder.setProjectId(getProperties().getGcp().getProjectId());
            }

            storage = builder.build().getService();
            Bucket bucket = storage.get(getProperties().getGcp().getBucketName());
            if (bucket == null) {
                log.warn("Configured GCP bucket does not exist: {}", getProperties().getGcp().getBucketName());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize Google Cloud Storage client: {}", e.getMessage());
        }
    }

    @Override
    public String provider() {
        return "gcp";
    }

    @Override
    public StorageUploadTarget createSignedUploadTarget(String extension, String contentType) {
        ensureStorage();

        try {
            String objectKey = nextObjectKey(extension);
            BlobInfo blobInfo = blobInfo(objectKey, contentType);

            Map<String, String> headers = new LinkedHashMap<>();
            List<Storage.SignUrlOption> options = new ArrayList<>();
            options.add(Storage.SignUrlOption.httpMethod(HttpMethod.PUT));
            options.add(Storage.SignUrlOption.withV4Signature());

            if (StringUtils.hasText(contentType)) {
                headers.put("Content-Type", contentType);
                options.add(Storage.SignUrlOption.withExtHeaders(headers));
            }

            URL signedUrl = storage.signUrl(
                blobInfo,
                getProperties().getSignedUrlTtlSeconds(),
                TimeUnit.SECONDS,
                options.toArray(Storage.SignUrlOption[]::new)
            );

            return buildUploadTarget(objectKey, objectUrl(objectKey), signedUrl.toString(), headers);
        } catch (Exception e) {
            log.error("Failed to create GCP signed URL", e);
            throw new SocialGraphException("storage_unavailable", "Unable to create an upload target");
        }
    }

    @Override
    public StoredObject upload(byte[] bytes, String extension, String contentType) {
        ensureStorage();

        try {
            String objectKey = nextObjectKey(extension);
            storage.create(blobInfo(objectKey, contentType), bytes);
            return buildStoredObject(objectKey, objectUrl(objectKey), contentType);
        } catch (Exception e) {
            log.error("Failed to upload object to GCP", e);
            throw new SocialGraphException("media_upload_failed", "Unable to upload the image");
        }
    }

    @Override
    public void download(String objectKey, OutputStream outputStream) {
        ensureStorage();

        try {
            outputStream.write(storage.readAllBytes(BlobId.of(getProperties().getGcp().getBucketName(), objectKey)));
        } catch (Exception e) {
            log.error("Failed to download object from GCP: {}", objectKey, e);
            throw new SocialGraphException("storage_unavailable", "Unable to download the object");
        }
    }

    private BlobInfo blobInfo(String objectKey, String contentType) {
        BlobInfo.Builder builder = BlobInfo.newBuilder(BlobId.of(getProperties().getGcp().getBucketName(), objectKey));
        if (StringUtils.hasText(contentType)) {
            builder.setContentType(contentType);
        }
        return builder.build();
    }

    private String objectUrl(String objectKey) {
        return "https://storage.googleapis.com/"
            + getProperties().getGcp().getBucketName()
            + "/"
            + encodeObjectKey(objectKey);
    }

    private void ensureStorage() {
        if (storage == null) {
            throw new SocialGraphException("storage_unavailable", "Object storage is not configured");
        }
    }
}
