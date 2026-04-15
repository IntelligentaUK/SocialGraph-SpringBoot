package com.intelligenta.socialgraph.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImagePayloadsTest {

    @Test
    void fromBytesDetectsPngWhenContentTypeIsGeneric() throws Exception {
        ImagePayloads.ImagePayload payload = ImagePayloads.fromBytes(createImageBytes("png"), "application/octet-stream");

        assertEquals("image/png", payload.mimeType());
        assertEquals(".png", payload.extension());
    }

    @Test
    void fromBase64ParsesDataUrlAndNormalizesJpgAlias() throws Exception {
        String dataUrl = "data:image/jpg;base64," + Base64.getEncoder().encodeToString(createImageBytes("jpg"));

        ImagePayloads.ImagePayload payload = ImagePayloads.fromBase64(dataUrl, null);

        assertEquals("image/jpeg", payload.mimeType());
        assertEquals(".jpg", payload.extension());
    }

    @Test
    void fromBytesDetectsWebpSignature() {
        byte[] webpHeader = new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

        ImagePayloads.ImagePayload payload = ImagePayloads.fromBytes(webpHeader, null);

        assertEquals("image/webp", payload.mimeType());
        assertEquals(".webp", payload.extension());
    }

    private byte[] createImageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
