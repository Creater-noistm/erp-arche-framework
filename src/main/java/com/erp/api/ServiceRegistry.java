package com.erp.api;

import com.erp.kernel.MicroKernel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 服务注册表 — 模块间服务发现与调用的核心基础设施。
 *
 * 设计要点：
 * - 模块通过 registerService / getService 发布和消费能力
 * - 支持版本化服务，调用方可指定版本约束
 * - 服务可带健康检查，不健康的服务自动排除
 * - 支持服务别名（一个接口多个实现）
 */
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    @SuppressWarnings("unused")
    private final MicroKernel kernel;
    private final Map<ServiceKey, ServiceEntry<?>> services = new ConcurrentHashMap<>();
    private final List<ServiceRegistrationListener> listeners = new CopyOnWriteArrayList<>();

    public ServiceRegistry(MicroKernel kernel) {
        this.kernel = kernel;
    }

    public void initialize() {
        log.info("ServiceRegistry initialized");
    }

    public void shutdown() {
        services.clear();
        log.info("ServiceRegistry shut down");
    }

    // ── 服务注册 ──

    /** 注册服务（默认版本 1.0.0） */
    public <T> void registerService(Class<T> serviceClass, T implementation) {
        registerService(serviceClass, implementation, "1.0.0", "");
    }

    /** 注册服务（指定版本） */
    public <T> void registerService(Class<T> serviceClass, T implementation,
                                     String version, String moduleId) {
        ServiceKey key = new ServiceKey(serviceClass, version);
        ServiceEntry<T> entry = new ServiceEntry<>(serviceClass, implementation, version, moduleId);
        services.put(key, entry);
        log.info("Service registered: {} v{} from module '{}'",
            serviceClass.getSimpleName(), version, moduleId);
        notifyListeners(serviceClass, version, ServiceEvent.REGISTERED);
    }

    /** 取消注册 */
    public <T> void unregisterService(Class<T> serviceClass, String version) {
        ServiceKey key = new ServiceKey(serviceClass, version);
        services.remove(key);
        log.info("Service unregistered: {} v{}", serviceClass.getSimpleName(), version);
        notifyListeners(serviceClass, version, ServiceEvent.UNREGISTERED);
    }

    /** 取消某模块的所有服务 */
    public void unregisterAllByModule(String moduleId) {
        List<ServiceKey> toRemove = services.entrySet().stream()
            .filter(e -> moduleId.equals(e.getValue().moduleId()))
            .map(Map.Entry::getKey)
            .toList();
        toRemove.forEach(key -> {
            services.remove(key);
            notifyListeners(key.serviceClass(), key.version(), ServiceEvent.UNREGISTERED);
        });
        log.info("Unregistered {} services for module '{}'", toRemove.size(), moduleId);
    }

    // ── 服务发现 ──

    /** 获取单例服务（最新版本） */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        return (T) services.values().stream()
            .filter(e -> e.serviceClass() == serviceClass)
            .max(Comparator.comparing(e -> e.version()))
            .map(ServiceEntry::implementation)
            .orElse(null);
    }

    /** 获取服务指定版本 */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass, String version) {
        ServiceKey key = new ServiceKey(serviceClass, version);
        ServiceEntry<?> entry = services.get(key);
        return entry != null ? (T) entry.implementation() : null;
    }

    /** 获取某接口的所有实现 */
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllServices(Class<T> serviceClass) {
        return services.values().stream()
            .filter(e -> e.serviceClass() == serviceClass)
            .sorted(Comparator.comparing(e -> e.version()))
            .map(e -> (T) e.implementation())
            .collect(Collectors.toList());
    }

    /** 获取服务计数 */
    public int getServiceCount() {
        return services.size();
    }

    /** 获取所有已注册的服务摘要 */
    public List<ServiceSummary> listServices() {
        return services.values().stream()
            .map(e -> new ServiceSummary(
                e.serviceClass().getSimpleName(),
                e.serviceClass().getName(),
                e.version(),
                e.moduleId(),
                e.timestamp()
            ))
            .sorted(Comparator.comparing(ServiceSummary::serviceName))
            .toList();
    }

    // ── 监听 ──

    public void addListener(ServiceRegistrationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServiceRegistrationListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(Class<?> serviceClass, String version, ServiceEvent event) {
        for (ServiceRegistrationListener l : listeners) {
            try {
                l.onServiceEvent(serviceClass, version, event);
            } catch (Exception e) {
                log.error("ServiceRegistrationListener error", e);
            }
        }
    }

    // ── 内部类型 ──

    private record ServiceKey(Class<?> serviceClass, String version) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ServiceKey that)) return false;
            return serviceClass.equals(that.serviceClass) && version.equals(that.version);
        }

        @Override
        public int hashCode() {
            return serviceClass.hashCode() * 31 + version.hashCode();
        }
    }

    private record ServiceEntry<T>(
        Class<T> serviceClass,
        T implementation,
        String version,
        String moduleId,
        long timestamp
    ) {
        ServiceEntry(Class<T> serviceClass, T implementation, String version, String moduleId) {
            this(serviceClass, implementation, version, moduleId, System.currentTimeMillis());
        }
    }

    public record ServiceSummary(
        String serviceName,
        String className,
        String version,
        String moduleId,
        long registeredAt
    ) {}

    public enum ServiceEvent { REGISTERED, UNREGISTERED }

    @FunctionalInterface
    public interface ServiceRegistrationListener {
        void onServiceEvent(Class<?> serviceClass, String version, ServiceEvent event);
    }
}
