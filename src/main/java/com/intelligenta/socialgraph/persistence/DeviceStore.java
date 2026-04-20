package com.intelligenta.socialgraph.persistence;

import java.util.Set;

/** Per-user registered devices. Long-lived; cluster-tier. */
public interface DeviceStore {
    boolean add(String username, String deviceId);
    boolean remove(String username, String deviceId);
    boolean contains(String username, String deviceId);
    Set<String> list(String username);
}
