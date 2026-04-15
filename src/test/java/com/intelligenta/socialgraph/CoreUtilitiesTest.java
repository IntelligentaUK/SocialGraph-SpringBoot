package com.intelligenta.socialgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligenta.socialgraph.config.AppProperties;
import com.intelligenta.socialgraph.config.RedisConfig;
import com.intelligenta.socialgraph.config.StorageProperties;
import com.intelligenta.socialgraph.exception.AlreadyFollowingException;
import com.intelligenta.socialgraph.exception.InvalidCredentialsException;
import com.intelligenta.socialgraph.model.ActionActor;
import com.intelligenta.socialgraph.model.ActionResponse;
import com.intelligenta.socialgraph.model.AuthResponse;
import com.intelligenta.socialgraph.model.ErrorResponse;
import com.intelligenta.socialgraph.model.MemberInfo;
import com.intelligenta.socialgraph.model.MembersResponse;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.model.TimelineEntry;
import com.intelligenta.socialgraph.model.TimelineResponse;
import com.intelligenta.socialgraph.util.Util;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class CoreUtilitiesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void utilMethodsProduceExpectedValues() {
        assertNotEquals(Util.UUID(), Util.UUID());
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Util.getMD5("abc".getBytes()));
        assertTrue(Util.unixtime().matches("\\d+"));
    }

    @Test
    void passwordHashSupportsArgonAndPbkdf2Validation() throws Exception {
        String salt = PasswordHash.createSalt();
        assertFalse(salt.isBlank());

        String argonHash = PasswordHash.createArgon2Hash("secret");
        assertTrue(PasswordHash.validateArgon2Hash("secret", argonHash));
        assertFalse(PasswordHash.validateArgon2Hash("wrong", argonHash));

        String pbkdf2Hash = PasswordHash.createHash("secret");
        assertTrue(PasswordHash.validatePassword("secret", pbkdf2Hash));
        assertFalse(PasswordHash.validatePassword("wrong", pbkdf2Hash));
    }

    @Test
    void liquidRescalerReturnsSmallerImageAndValidatesCut() throws Exception {
        byte[] source = TestImages.pngBytes(4, 3);

        LiquidRescaler.RescaledImage image = LiquidRescaler.rescaleImage(source, 1, 1, "image/png");

        assertEquals("image/png", image.mimeType());
        assertEquals(".png", image.extension());
        org.junit.jupiter.api.Assertions.assertTrue(image.bytes().length > 0);

        var ex = assertThrows(
            com.intelligenta.socialgraph.exception.SocialGraphException.class,
            () -> LiquidRescaler.rescaleImage(source, 4, 1, "image/png")
        );
        assertEquals("invalid_image_payload", ex.getErrorCode());
    }

    @Test
    void numericUtilityClassesPreserveLegacyBehavior() {
        assertEquals(new BigDecimal("3.5"), BigHex.toBigDecimal("0x1.cp1"));
        assertEquals(new BigDecimal("-255"), BigHex.toBigDecimal("-0xf.fp4"));
        assertEquals(750, new EdgeScore().main(EdgeScore.Groups.ZScoreGroupVIP, EdgeScore.Multipliers.ZScoreMultiplierVerified).toInt());
        assertEquals("hugged", Verbs.Action.HUG.pastTense());
        assertEquals("shares:", Verbs.Action.SHARE.key());
    }

    @Test
    void configAndModelTypesExposeExpectedStateAndJsonFields() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.getSecurity().setTokenExpirationSeconds(42);
        appProperties.setPublicEndpoints(List.of("/public"));
        assertEquals(42, appProperties.getSecurity().getTokenExpirationSeconds());
        assertEquals(List.of("/public"), appProperties.getPublicEndpoints());

        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setProvider(StorageProperties.Provider.GCP);
        storageProperties.getGcp().setBucketName("bucket");
        assertEquals(StorageProperties.Provider.GCP, storageProperties.getProvider());
        assertEquals("bucket", storageProperties.getGcp().getBucketName());

        StorageUploadTarget uploadTarget = new StorageUploadTarget(
            "azure", "obj", "https://example/object", "https://example/upload", "PUT", Map.of("A", "B"), 300
        );
        assertEquals("B", uploadTarget.headers().get("A"));
        assertThrows(UnsupportedOperationException.class, () -> uploadTarget.headers().put("X", "Y"));

        AuthResponse authResponse = new AuthResponse("alice", "token", "uid", 60);
        authResponse.setActivationToken("activate");
        ErrorResponse errorResponse = new ErrorResponse("error", "details");
        ActionResponse actionResponse = new ActionResponse(
            Verbs.Action.LIKE, "post-1", List.of(new ActionActor("u1", "alice", "Alice")), 1, 5
        );
        TimelineEntry timelineEntry = new TimelineEntry();
        timelineEntry.setUuid("post-1");
        MembersResponse membersResponse = new MembersResponse("followers", List.of(new MemberInfo("u1", "alice", "Alice")), 10);
        TimelineResponse timelineResponse = new TimelineResponse(List.of(timelineEntry), 1, 10);

        String authJson = objectMapper.writeValueAsString(authResponse);
        String errorJson = objectMapper.writeValueAsString(errorResponse);
        String actorJson = objectMapper.writeValueAsString(new ActionActor("u1", "alice", "Alice"));

        assertTrue(authJson.contains("\"expires_in\":60"));
        assertTrue(authJson.contains("\"activation_token\":\"activate\""));
        assertTrue(errorJson.contains("\"error_description\":\"details\""));
        assertTrue(actorJson.contains("\"@type\":\"person\""));
        assertEquals("likes", actionResponse.getActionType());
        assertEquals(1, membersResponse.getCount());
        assertEquals(1, timelineResponse.getCount());

        assertEquals("cannot_follow", new AlreadyFollowingException("Already").getErrorCode());
        assertEquals("invalid_grant", new InvalidCredentialsException("Bad").getErrorCode());
        assertEquals("image/png", new StoredObject("gcp", "obj", "url", "image/png").contentType());
    }

    @Test
    void redisConfigCreatesTemplatesWithStringSerializers() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConfig redisConfig = new RedisConfig();

        StringRedisTemplate stringRedisTemplate = redisConfig.stringRedisTemplate(connectionFactory);
        RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(connectionFactory);

        assertEquals(connectionFactory, stringRedisTemplate.getConnectionFactory());
        assertEquals(connectionFactory, redisTemplate.getConnectionFactory());
        assertEquals("org.springframework.data.redis.serializer.StringRedisSerializer", redisTemplate.getKeySerializer().getClass().getName());
        assertEquals("org.springframework.data.redis.serializer.StringRedisSerializer", redisTemplate.getValueSerializer().getClass().getName());
    }

    @Test
    void applicationMainDelegatesToSpringApplication() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            SocialGraphApplication.main(new String[] {"--demo"});
            springApplication.verify(() -> SpringApplication.run(SocialGraphApplication.class, "--demo"));
        }
    }

    private static final class TestImages {
        private TestImages() {
        }

        private static byte[] pngBytes(int width, int height) throws Exception {
            java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
