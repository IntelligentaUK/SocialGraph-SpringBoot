package com.intelligenta.socialgraph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        deviceService = new DeviceService(redisTemplate);
    }

    @Test
    void registerGeneratesIdentifierWhenDeviceIsMissing() {
        when(setOperations.add(anyString(), anyString())).thenReturn(1L);

        String result = deviceService.register("alice", null);

        assertEquals("Device Registered", result);
        verify(setOperations).add(anyString(), anyString());
    }

    @Test
    void registerReturnsAlreadyRegisteredWhenSetAddReturnsZero() {
        when(setOperations.add("user:alice:devices", "device-1")).thenReturn(0L);

        String result = deviceService.register("alice", "device-1");

        assertEquals("Device Already Registered", result);
    }

    @Test
    void deregisterAndMembershipQueriesUseRedisSetOperations() {
        when(setOperations.remove("user:alice:devices", "device-1")).thenReturn(1L);
        when(setOperations.isMember("user:alice:devices", "device-1")).thenReturn(false);
        when(setOperations.members("user:alice:devices")).thenReturn(Set.of("device-2"));

        assertEquals("Device Deregistered", deviceService.deregister("alice", "device-1"));
        assertFalse(deviceService.isRegistered("alice", "device-1"));
        assertEquals(Set.of("device-2"), deviceService.list("alice"));
    }
}
