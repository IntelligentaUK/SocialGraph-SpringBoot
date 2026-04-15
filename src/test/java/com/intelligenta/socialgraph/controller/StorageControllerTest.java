package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.exception.GlobalExceptionHandler;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.service.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    private ObjectStorageService objectStorageService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        StorageController controller = new StorageController(objectStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void requestStorageKeyReturnsProviderNeutralShape() throws Exception {
        when(objectStorageService.createSignedUploadTarget(null, null)).thenReturn(
            new StorageUploadTarget(
                "azure",
                "obj-1",
                "https://example.com/obj-1",
                "https://example.com/obj-1?sig=test",
                "PUT",
                Map.of("x-ms-blob-type", "BlockBlob"),
                300
            )
        );

        mockMvc.perform(post("/api/request/storage/key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("azure"))
            .andExpect(jsonPath("$.objectKey").value("obj-1"))
            .andExpect(jsonPath("$.uploadUrl").value("https://example.com/obj-1?sig=test"))
            .andExpect(jsonPath("$.method").value("PUT"));
    }

    @Test
    void lqUploadAcceptsPngDataUrlAndReturnsUploadedObjectMetadata() throws Exception {
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(createImageBytes("png"));
        when(objectStorageService.upload(any(byte[].class), eq(".png"), eq("image/png"))).thenReturn(
            new StoredObject("gcp", "object-1.png", "https://storage.googleapis.com/bucket/object-1.png", "image/png")
        );

        mockMvc.perform(post("/api/lq/upload")
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "imageBase64", dataUrl,
                    "cut", 1
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("gcp"))
            .andExpect(jsonPath("$.objectKey").value("object-1.png"))
            .andExpect(jsonPath("$.objectUrl").value("https://storage.googleapis.com/bucket/object-1.png"))
            .andExpect(jsonPath("$.mimeType").value("image/png"));

        verify(objectStorageService).upload(any(byte[].class), eq(".png"), eq("image/png"));
    }

    @Test
    void lqUploadRejectsUnsupportedMimeType() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(createImageBytes("png"));

        mockMvc.perform(post("/api/lq/upload")
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "imageBase64", base64,
                    "cut", 1,
                    "mimeType", "image/gif"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unsupported_image_type"));
    }

    private byte[] createImageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
