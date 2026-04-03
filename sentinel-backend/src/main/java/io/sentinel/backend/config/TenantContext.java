package io.sentinel.backend.config;

/**
 * Request-scoped tenant context populated by JWT filter.
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT = "default";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set((tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String getOrDefault() {
        String tenantId = CURRENT.get();
        return (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

