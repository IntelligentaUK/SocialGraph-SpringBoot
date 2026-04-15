package com.intelligenta.socialgraph.service.storage;

import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;

import java.io.OutputStream;

/**
 * Abstraction for the active object storage provider.
 */
public interface ObjectStorageService {

    String provider();

    StorageUploadTarget createSignedUploadTarget(String extension, String contentType);

    StoredObject upload(byte[] bytes, String extension, String contentType);

    void download(String objectKey, OutputStream outputStream);
}
