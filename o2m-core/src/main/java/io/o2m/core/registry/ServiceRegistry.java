package io.o2m.core.registry;

import io.o2m.spi.*;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ServiceRegistry {
    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T require(Class<T> type) {
        return (T) Optional.ofNullable(services.get(type))
                .orElseThrow(() -> new IllegalStateException("Service not registered: " + type.getName()));
    }

    public <T> T getOrCreate(Class<T> type, Supplier<T> supplier) {
        return (T) services.computeIfAbsent(type, k -> supplier.get());
    }
}
