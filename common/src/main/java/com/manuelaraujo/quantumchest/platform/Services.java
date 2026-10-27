package com.manuelaraujo.quantumchest.platform;

import com.manuelaraujo.quantumchest.platform.services.INetworkHelper;
import com.manuelaraujo.quantumchest.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Finds the loader specific implementation of an interface at runtime.
 * Each loader lists its implementations in META-INF/services.
 */
public final class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation found for " + clazz.getName()));
    }
}
