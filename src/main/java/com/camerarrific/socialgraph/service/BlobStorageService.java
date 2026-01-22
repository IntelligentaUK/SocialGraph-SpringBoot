package com.camerarrific.socialgraph.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.camerarrific.socialgraph.util.PasswordHasher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for Azure Blob Storage operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlobStorageService {

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Value("${azure.storage.account-name:}")
    private String accountName;

    @Value("${azure.storage.container-name:photos}")
    private String containerName;

    private final PasswordHasher passwordHasher;
    
    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        if (connectionString != null && !connectionString.isEmpty()) {
            try {
                blobServiceClient = new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .buildClient();
                
                containerClient = blobServiceClient.getBlobContainerClient(containerName);
                if (!containerClient.exists()) {
                    containerClient.create();
                }
                log.info("Azure Blob Storage initialized with container: {}", containerName);
            } catch (Exception e) {
                log.warn("Failed to initialize Azure Blob Storage: {}", e.getMessage());
            }
        } else {
            log.warn("Azure Blob Storage connection string not configured");
        }
    }

    /**
     * Check if blob storage is configured.
     */
    public boolean isConfigured() {
        return containerClient != null;
    }

    /**
     * Upload bytes to blob storage.
     */
    public String upload(byte[] data) {
        if (!isConfigured()) {
            log.warn("Blob storage not configured, cannot upload");
            return null;
        }

        try {
            String fileName = passwordHasher.generateUuid() + ".jpg";
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            
            blobClient.upload(new ByteArrayInputStream(data), data.length, true);
            
            String blobUrl = String.format("https://%s.blob.core.windows.net/%s/%s",
                    accountName, containerName, fileName);
            
            log.debug("Uploaded blob: {}", blobUrl);
            return blobUrl;
        } catch (Exception e) {
            log.error("Failed to upload blob", e);
            return null;
        }
    }

    /**
     * Generate a SAS key for client-side uploads.
     */
    public Map<String, Object> generateSasKey() {
        if (!isConfigured()) {
            log.warn("Blob storage not configured, cannot generate SAS key");
            return Map.of("error", "Blob storage not configured");
        }

        try {
            String blobId = passwordHasher.generateUuid();
            String blobName = blobId + ".jpg";
            
            // Create SAS token with read permissions for 5 minutes
            BlobContainerSasPermission permission = new BlobContainerSasPermission()
                    .setReadPermission(true)
                    .setWritePermission(true);
            
            OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(5);
            
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permission);
            
            String sasToken = containerClient.generateSas(sasValues);
            
            String blobUrl = String.format("https://%s.blob.core.windows.net/%s/%s",
                    accountName, containerName, blobName);

            Map<String, Object> result = new HashMap<>();
            
            Map<String, String> blob = new HashMap<>();
            blob.put("uuid", blobId);
            blob.put("url", blobUrl);
            
            Map<String, Object> permissions = new HashMap<>();
            permissions.put("saskey", blobUrl + "?" + sasToken);
            permissions.put("readaccess", true);
            permissions.put("writeaccess", true);
            permissions.put("expiresIn", 300);
            
            result.put("blob", blob);
            result.put("permissions", permissions);

            log.debug("Generated SAS key for blob: {}", blobId);
            return result;
        } catch (Exception e) {
            log.error("Failed to generate SAS key", e);
            return Map.of("error", "Failed to generate SAS key");
        }
    }

    /**
     * Download a blob to an output stream.
     */
    public void download(String fileName, OutputStream outputStream) {
        if (!isConfigured()) {
            log.warn("Blob storage not configured, cannot download");
            return;
        }

        try {
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            blobClient.downloadStream(outputStream);
            log.debug("Downloaded blob: {}", fileName);
        } catch (Exception e) {
            log.error("Failed to download blob", e);
        }
    }
}

