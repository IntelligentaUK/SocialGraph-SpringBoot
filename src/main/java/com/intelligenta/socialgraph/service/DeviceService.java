package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.util.Util;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Set;

/**
 * Service for device registration operations.
 */
@Service
public class DeviceService {

    private final StringRedisTemplate redisTemplate;

    public DeviceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String register(String username, String device) {
        String registeredDevice = (device == null || device.isBlank())
            ? new BigInteger(Util.UUID().replace("-", ""), 16).toString()
            : device;
        Long added = redisTemplate.opsForSet().add("user:" + username + ":devices", registeredDevice);
        if (added != null && added == 1) {
            return "Device Registered";
        } else {
            return "Device Already Registered";
        }
    }

    public String deregister(String username, String device) {
        Long removed = redisTemplate.opsForSet().remove("user:" + username + ":devices", device);
        if (removed != null && removed == 1) {
            return "Device Deregistered";
        } else {
            return "Not Removed: Device Not Registered";
        }
    }

    public boolean isRegistered(String username, String device) {
        Boolean isMember = redisTemplate.opsForSet().isMember("user:" + username + ":devices", device);
        return Boolean.TRUE.equals(isMember);
    }

    public Set<String> list(String username) {
        return redisTemplate.opsForSet().members("user:" + username + ":devices");
    }
}
