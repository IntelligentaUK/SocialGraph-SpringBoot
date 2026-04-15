package com.intelligenta.socialgraph.service.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.intelligenta.socialgraph.config.StorageProperties;
import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectStorageServicesTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private Storage storage;

    @Mock
    private ObjectStorageClient objectStorageClient;

    @Mock
    private CreatePreauthenticatedRequestResponse createPreauthenticatedRequestResponse;

    @Mock
    private PreauthenticatedRequest preauthenticatedRequest;

    @Mock
    private GetObjectResponse getObjectResponse;

    @Test
    void abstractHelpersNormalizeKeysAndHeaders() {
        StorageProperties properties = new StorageProperties();
        properties.setObjectKeyPrefix("uploads");
        TestStorageService service = new TestStorageService(properties);

        String objectKey = service.nextObjectKey("png");
        var target = service.buildUploadTarget("file.png", "https://example/object", "https://example/upload", Map.of("A", "B"));

        org.junit.jupiter.api.Assertions.assertTrue(objectKey.startsWith("uploads/"));
        assertEquals("PUT", target.method());
        assertEquals("B", target.headers().get("A"));
        assertThrows(UnsupportedOperationException.class, () -> target.headers().put("X", "Y"));
    }

    @Test
    void azureServiceCreatesUploadTargetAndTransfersDownloads() throws Exception {
        AzureObjectStorageService service = new AzureObjectStorageService(azureProperties());
        ReflectionTestUtils.setField(service, "containerClient", blobContainerClient);

        when(blobContainerClient.getBlobClient(any())).thenReturn(blobClient);
        when(blobClient.getBlobUrl()).thenReturn("https://example.blob.core.windows.net/photos/blob.png");
        when(blobClient.generateSas(any())).thenReturn("sig=1");
        doAnswer(invocation -> {
            ByteArrayOutputStream output = invocation.getArgument(0);
            output.write("azure".getBytes());
            return null;
        }).when(blobClient).downloadStream(any(ByteArrayOutputStream.class));

        var uploadTarget = service.createSignedUploadTarget(".png", "image/png");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.download("blob.png", outputStream);

        assertEquals("azure", uploadTarget.provider());
        assertEquals("BlockBlob", uploadTarget.headers().get("x-ms-blob-type"));
        assertArrayEquals("azure".getBytes(), outputStream.toByteArray());
    }

    @Test
    void gcpServiceSignsUploadsAndWritesDownloads() throws Exception {
        GcpObjectStorageService service = new GcpObjectStorageService(gcpProperties());
        ReflectionTestUtils.setField(service, "storage", storage);

        doReturn(new URL("https://storage.googleapis.com/social/blob.png?sig=1"))
            .when(storage)
            .signUrl(any(BlobInfo.class), eq(300L), eq(TimeUnit.SECONDS), any(Storage.SignUrlOption[].class));
        when(storage.readAllBytes(any(BlobId.class))).thenReturn("gcp".getBytes());

        var uploadTarget = service.createSignedUploadTarget(".png", "image/png");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.download("blob.png", outputStream);

        assertEquals("gcp", uploadTarget.provider());
        assertEquals("image/png", uploadTarget.headers().get("Content-Type"));
        assertArrayEquals("gcp".getBytes(), outputStream.toByteArray());
    }

    @Test
    void ociServiceUsesConfiguredEndpointAndStreamsDownloads() throws Exception {
        OciObjectStorageService service = new OciObjectStorageService(ociProperties());
        ReflectionTestUtils.setField(service, "objectStorageClient", objectStorageClient);

        when(objectStorageClient.createPreauthenticatedRequest(any())).thenReturn(createPreauthenticatedRequestResponse);
        when(createPreauthenticatedRequestResponse.getPreauthenticatedRequest()).thenReturn(preauthenticatedRequest);
        when(preauthenticatedRequest.getAccessUri()).thenReturn("/p/par");
        when(objectStorageClient.getObject(any())).thenReturn(getObjectResponse);
        when(getObjectResponse.getInputStream()).thenReturn(new ByteArrayInputStream("oci".getBytes()));

        var uploadTarget = service.createSignedUploadTarget(".png", "image/png");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.download("blob.png", outputStream);

        assertEquals("oci", uploadTarget.provider());
        org.junit.jupiter.api.Assertions.assertTrue(uploadTarget.uploadUrl().startsWith("https://object.example.com"));
        assertArrayEquals("oci".getBytes(), outputStream.toByteArray());
    }

    @Test
    void missingClientThrowsStorageUnavailable() {
        AzureObjectStorageService service = new AzureObjectStorageService(azureProperties());

        SocialGraphException ex = assertThrows(
            SocialGraphException.class,
            () -> service.createSignedUploadTarget(".png", "image/png")
        );

        assertEquals("storage_unavailable", ex.getErrorCode());
    }

    private static StorageProperties azureProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getAzure().setContainerName("photos");
        return properties;
    }

    private static StorageProperties gcpProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getGcp().setBucketName("social");
        return properties;
    }

    private static StorageProperties ociProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getOci().setBucketName("social");
        properties.getOci().setNamespaceName("namespace");
        properties.getOci().setEndpoint("https://object.example.com");
        return properties;
    }

    private static final class TestStorageService extends AbstractObjectStorageService {

        private TestStorageService(StorageProperties properties) {
            super(properties);
        }

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public com.intelligenta.socialgraph.model.StorageUploadTarget createSignedUploadTarget(String extension, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.intelligenta.socialgraph.model.StoredObject upload(byte[] bytes, String extension, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void download(String objectKey, java.io.OutputStream outputStream) {
            throw new UnsupportedOperationException();
        }
    }
}
