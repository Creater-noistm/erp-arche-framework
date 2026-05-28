package com.erp.router;

import com.erp.kernel.MicroKernel;
import com.erp.model.DynamicEntity;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════
 * DataRouter — 数据路由器：始祖框架的"数据交通枢纽"
 *
 * 核心思想：
 * - 所有数据操作通过路由器转发，而非直接调用模块
 * - 路由器根据"实体类型 + 操作"自动路由到正确的模块
 * - 支持拦截器链（权限检查、数据隔离、审计日志、缓存等）
 * - 支持读写分离、负载均衡（未来）
 *
 * 路由流程：
 *   请求 → 拦截器链(权限→租户隔离→审计) → 路由到模块Handler → 响应
 * ═══════════════════════════════════════════════════════════════
 */
public class DataRouter {

    private static final Logger log = LoggerFactory.getLogger(DataRouter.class);

    private final MicroKernel kernel;

    /** 路由表： entityType → Map<操作, Handler> */
    private final Map<String, Map<String, DataHandler>> routeTable = new ConcurrentHashMap<>();
    private final Map<String, String> entityOwner = new ConcurrentHashMap<>();
    /** 全局拦截器链 */
    private final List<RoutingInterceptor> interceptors = new CopyOnWriteArrayList<>();

    /** 异步执行器 */
    private final ExecutorService asyncExecutor;

    public DataRouter(MicroKernel kernel) {
        this.kernel = kernel;
        this.asyncExecutor = Executors.newFixedThreadPool(
            kernel.getConfig().getCoreThreadPoolSize(),
            r -> {
                Thread t = new Thread(r, "datarouter-dispatcher");
                t.setDaemon(true);
                return t;
            }
        );
    }

    public void initialize() {
        // 注册内置拦截器
        registerInterceptor(new TenantIsolationInterceptor());
        registerInterceptor(new AuditInterceptor());

        log.info("DataRouter initialized");
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("DataRouter shut down");
    }

    // ── 路由注册 ──

    /**
     * 注册数据处理器。
     *
     * @param entityType 实体类型
     * @param operation  操作（create / read / update / delete / query / custom）
     * @param handler    处理器
     */
    public void registerHandler(String entityType, String operation, DataHandler handler) {
        routeTable.computeIfAbsent(entityType, k -> new ConcurrentHashMap<>())
            .put(operation, handler);
        log.debug("Handler registered: {}#{}", entityType, operation);
    }

    /** 注册标准 CRUD 处理器（不追踪模块来源） */
    public void registerCrudHandlers(String entityType, CrudHandler crud) {
        registerHandler(entityType, "create",  crud::create);
        registerHandler(entityType, "read",    crud::read);
        registerHandler(entityType, "update",  crud::update);
        registerHandler(entityType, "delete",  crud::delete);
        registerHandler(entityType, "query",   crud::query);
    }

    /** 注册标准 CRUD 处理器 + 追踪模块归属 */
    public void registerCrudHandlers(String entityType, String moduleId, CrudHandler crud) {
        entityOwner.put(entityType, moduleId);
        log.debug("Registered CRUD handlers: entityType='{}', module='{}'", entityType, moduleId);
        registerCrudHandlers(entityType, crud);
    }

    /** 取消注册某实体类型的所有处理器 */
    public void unregisterHandlers(String entityType) {
        routeTable.remove(entityType);
        entityOwner.remove(entityType);
        log.debug("Handlers unregistered for entity type: {}", entityType);
    }

    /** 取消注册某模块的所有处理器 */
    public void unregisterModuleHandlers(String moduleId) {
        entityOwner.entrySet().removeIf(e -> {
            if (e.getValue().equals(moduleId)) {
                routeTable.remove(e.getKey());
                return true;
            }
            return false;
        });
        log.debug("All handlers unregistered for module: {}", moduleId);
    }

    // ── 拦截器管理 ──

    public void registerInterceptor(RoutingInterceptor interceptor) {
        interceptors.add(interceptor);
        interceptors.sort(Comparator.comparingInt(RoutingInterceptor::order));
        log.debug("Interceptor registered: {}", interceptor.getClass().getSimpleName());
    }

    public void removeInterceptor(RoutingInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    // ── 路由执行 ──

    /**
     * 路由数据请求（同步）。
     *
     * @param request 路由请求
     * @return 路由响应
     */
    public RoutingResponse route(RoutingRequest request) {
        // 1. 确定目标实体类型和操作
        String entityType = request.entityType();
        String operation = request.operation();

        // 2. 执行拦截器链（前置）
        RoutingContext context = new RoutingContext(request, kernel);
        try {
            for (RoutingInterceptor interceptor : interceptors) {
                if (!interceptor.preHandle(context)) {
                    return RoutingResponse.denied("Blocked by interceptor: "
                        + interceptor.getClass().getSimpleName());
                }
            }

            // 3. 查找处理器
            Map<String, DataHandler> handlers = routeTable.get(entityType);
            if (handlers == null) {
                return RoutingResponse.error("No handlers for entity type: " + entityType);
            }

            DataHandler handler = handlers.get(operation);
            if (handler == null) {
                return RoutingResponse.error("No handler for operation '" + operation
                    + "' on entity type: " + entityType);
            }

            // 4. 执行处理器
            Object result = handler.handle(request, context);

            // 5. 执行拦截器链（后置）
            for (RoutingInterceptor interceptor : interceptors) {
                interceptor.postHandle(context, result);
            }

            return RoutingResponse.success(result);

        } catch (Exception e) {
            log.error("Routing error: {}#{}", entityType, operation, e);
            return RoutingResponse.error("Routing error: " + e.getMessage());
        }
    }

    /**
     * 异步路由。
     */
    public CompletableFuture<RoutingResponse> routeAsync(RoutingRequest request) {
        return CompletableFuture.supplyAsync(() -> route(request), asyncExecutor);
    }

    // ── 便捷路由方法 ──

    /** 创建实体 */
    public RoutingResponse create(String entityType, DynamicEntity entity) {
        return route(new RoutingRequest(entityType, "create", entity, TenantContext.getCurrentTenantId()));
    }

    /** 读取实体 */
    public RoutingResponse read(String entityType, String entityId) {
        return route(new RoutingRequest(entityType, "read", entityId, TenantContext.getCurrentTenantId()));
    }

    /** 更新实体 */
    public RoutingResponse update(String entityType, DynamicEntity entity) {
        return route(new RoutingRequest(entityType, "update", entity, TenantContext.getCurrentTenantId()));
    }

    /** 删除实体 */
    public RoutingResponse delete(String entityType, String entityId) {
        return route(new RoutingRequest(entityType, "delete", entityId, TenantContext.getCurrentTenantId()));
    }

    /** 查询实体 */
    public RoutingResponse query(String entityType, Map<String, Object> criteria) {
        return route(new RoutingRequest(entityType, "query", criteria, TenantContext.getCurrentTenantId()));
    }

    // ── 查询 ──

    /** 获取所有已注册的路由 */
    public Map<String, Set<String>> getRouteTable() {
        return routeTable.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().keySet()
            ));
    }

    public int getRouteCount() {
        return routeTable.values().stream().mapToInt(Map::size).sum();
    }

    // ── 内部类型 ──

    /** 路由请求 */
    public record RoutingRequest(
        String entityType,
        String operation,
        Object payload,
        String tenantId,
        String userId,
        Map<String, Object> parameters
    ) {
        public RoutingRequest(String entityType, String operation, Object payload, String tenantId) {
            this(entityType, operation, payload, tenantId, "", Map.of());
        }

        @SuppressWarnings("unchecked")
        public <T> T getPayload() { return (T) payload; }
    }

    /** 路由响应 */
    public record RoutingResponse(
        boolean success,
        String message,
        Object data,
        long processingTimeMs
    ) {
        public static RoutingResponse success(Object data) {
            return new RoutingResponse(true, "OK", data, 0);
        }

        public static RoutingResponse error(String message) {
            return new RoutingResponse(false, message, null, 0);
        }

        public static RoutingResponse denied(String message) {
            return new RoutingResponse(false, message, null, 0);
        }

        @SuppressWarnings("unchecked")
        public <T> T getData() { return (T) data; }
    }

    /** 数据处理器接口 */
    @FunctionalInterface
    public interface DataHandler {
        Object handle(RoutingRequest request, RoutingContext context);
    }

    /** CRUD 快捷接口 */
    public interface CrudHandler {
        default Object create(RoutingRequest req, RoutingContext ctx) {
            return RoutingResponse.error("create not implemented");
        }
        default Object read(RoutingRequest req, RoutingContext ctx) {
            return RoutingResponse.error("read not implemented");
        }
        default Object update(RoutingRequest req, RoutingContext ctx) {
            return RoutingResponse.error("update not implemented");
        }
        default Object delete(RoutingRequest req, RoutingContext ctx) {
            return RoutingResponse.error("delete not implemented");
        }
        default Object query(RoutingRequest req, RoutingContext ctx) {
            return RoutingResponse.error("query not implemented");
        }
    }

    /** 路由上下文 */
    public static class RoutingContext {
        private final RoutingRequest request;
        private final MicroKernel kernel;
        private final Map<String, Object> attributes;

        RoutingContext(RoutingRequest request, MicroKernel kernel) {
            this.request = request;
            this.kernel = kernel;
            this.attributes = new ConcurrentHashMap<>();
        }

        public RoutingRequest getRequest() { return request; }
        public MicroKernel getKernel() { return kernel; }

        public void setAttribute(String key, Object value) { attributes.put(key, value); }
        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String key) { return (T) attributes.get(key); }
    }

    /** 路由拦截器 */
    public interface RoutingInterceptor {
        /** 前置处理：返回 false 则终止请求 */
        default boolean preHandle(RoutingContext context) { return true; }

        /** 后置处理 */
        default void postHandle(RoutingContext context, Object result) {}

        /** 执行顺序（数值越小越先执行） */
        default int order() { return 0; }
    }

    // ── 内置拦截器 ──

    /** 租户隔离拦截器 — 确保数据操作限定在当前租户且租户状态正常 */
    private static class TenantIsolationInterceptor implements RoutingInterceptor {
        @Override
        public boolean preHandle(RoutingContext context) {
            String requestTenant = context.getRequest().tenantId();
            String currentTenant = TenantContext.getCurrentTenantId();
            if (!currentTenant.equals(requestTenant)) {
                log.warn("Tenant mismatch: request belongs to '{}', context is '{}'",
                    requestTenant, currentTenant);
                return false;
            }
            // 验证租户状态（防止已停用租户的旧会话继续操作）
            try {
                String status = com.erp.db.DatabaseManager.getInstance().executeQuery(
                    "SELECT status FROM erp_tenants WHERE id=?",
                    rs -> rs.next() ? rs.getString("status") : null,
                    currentTenant);
                if (status == null || !"ACTIVE".equals(status)) {
                    log.warn("Tenant '{}' is {} — request blocked", currentTenant, status);
                    return false;
                }
            } catch (Exception e) {
                log.warn("Failed to check tenant status: {}", e.getMessage());
            }
            return true;
        }

        @Override
        public int order() { return Integer.MIN_VALUE; }
    }

    /** 审计日志拦截器 */
    private static class AuditInterceptor implements RoutingInterceptor {
        @Override
        public boolean preHandle(RoutingContext context) {
            context.setAttribute("audit.startTime", System.currentTimeMillis());
            return true;
        }

        @Override
        public void postHandle(RoutingContext context, Object result) {
            if (!context.getKernel().getConfig().isEnableSecurityAudit()) return;

            long startTime = context.getAttribute("audit.startTime");
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("AUDIT: {}#{} by tenant={} — {}ms",
                context.getRequest().entityType(),
                context.getRequest().operation(),
                context.getRequest().tenantId(),
                elapsed);
        }

        @Override
        public int order() { return Integer.MAX_VALUE; }
    }
}
