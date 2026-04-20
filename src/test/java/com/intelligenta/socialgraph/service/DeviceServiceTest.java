package com.intelligenta.socialgraph.service;

import java.util.Set;

import com.intelligenta.socialgraph.persistence.DeviceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceStore devices;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(devices);
    }

    @Test
    void registerGeneratesIdentifierWhenDeviceIsMissing() {
        when(devices.add(eq("alice"), anyString())).thenReturn(true);
        assertEquals("Device Registered", deviceService.register("alice", null));
        verify(devices).add(eq("alice"), anyString());
    }

    @Test
    void registerReturnsAlreadyRegisteredWhenStoreReturnsFalse() {
        when(devices.add("alice", "device-1")).thenReturn(false);
        assertEquals("Device Already Registered", deviceService.register("alice", "device-1"));
    }

    @Test
    void deregisterAndMembershipQueriesUseStore() {
        when(devices.remove("alice", "device-1")).thenReturn(true);
        when(devices.contains("alice", "device-1")).thenReturn(false);
        when(devices.list("alice")).thenReturn(Set.of("device-2"));

        assertEquals("Device Deregistered", deviceService.deregister("alice", "device-1"));
        assertFalse(deviceService.isRegistered("alice", "device-1"));
        assertEquals(Set.of("device-2"), deviceService.list("alice"));
    }
}
