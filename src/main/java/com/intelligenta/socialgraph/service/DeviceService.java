package com.intelligenta.socialgraph.service;

import java.math.BigInteger;
import java.util.Set;

import com.intelligenta.socialgraph.persistence.DeviceStore;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.stereotype.Service;

/** Device registration, backed by {@link DeviceStore}. */
@Service
public class DeviceService {

    private final DeviceStore devices;

    public DeviceService(DeviceStore devices) {
        this.devices = devices;
    }

    public String register(String username, String device) {
        String registeredDevice = (device == null || device.isBlank())
            ? new BigInteger(Util.UUID().replace("-", ""), 16).toString()
            : device;
        return devices.add(username, registeredDevice) ? "Device Registered" : "Device Already Registered";
    }

    public String deregister(String username, String device) {
        return devices.remove(username, device)
            ? "Device Deregistered"
            : "Not Removed: Device Not Registered";
    }

    public boolean isRegistered(String username, String device) {
        return devices.contains(username, device);
    }

    public Set<String> list(String username) {
        return devices.list(username);
    }
}
