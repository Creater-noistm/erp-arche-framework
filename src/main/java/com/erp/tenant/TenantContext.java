package com.erp.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户上下文 — 线程级租户身份标识。
 *
 * 使用 ThreadLocal 实现，确保同一线程内所有操作
 * 都知道当前所属租户。这是实现多租户数据隔离的关键。
 *
 * 在请求/操作入口处设置，在出口处清除。
 *
 * 用法：
 *   TenantContext.setCurrentTenant("tenant_001");
 *   // ... 执行操作 ...
 *   TenantContext.clear();
 */
public class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> contextData = new ThreadLocal<>();

    /** 当前登录会话（线程级） */
    private static final ThreadLocal<LoginSession> loginSession = new ThreadLocal<>();

    /** 默认租户ID（单租户模式或未指定时使用） */
    private static String defaultTenantId = "default";

    private TenantContext() {}

    /** 设置登录会话（登录成功后调用） */
    public static void setLoginSession(LoginSession session) {
        loginSession.set(session);
        setCurrentTenant(session.tenantId());
    }

    /** 获取当前登录会话 */
    public static LoginSession getLoginSession() {
        return loginSession.get();
    }

    /** 初始化默认租户（内核启动时调用） */
    public static void initialize(String defaultId) {
        defaultTenantId = defaultId;
        log.info("TenantContext initialized with default tenant: {}", defaultId);
    }

    /** 设置当前线程的租户 */
    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        currentTenant.set(tenantId);
        contextData.set(new ConcurrentHashMap<>());
        log.trace("TenantContext set to: {}", tenantId);
    }

    /** 获取当前线程的租户ID */
    public static String getCurrentTenantId() {
        String tenant = currentTenant.get();
        return tenant != null ? tenant : defaultTenantId;
    }

    /** 清除当前线程的租户上下文 */
    public static void clear() {
        currentTenant.remove();
        contextData.remove();
        loginSession.remove();
    }

    /** 设置上下文数据 */
    public static void setData(String key, Object value) {
        Map<String, Object> data = contextData.get();
        if (data == null) {
            data = new ConcurrentHashMap<>();
            contextData.set(data);
        }
        data.put(key, value);
    }

    /** 获取上下文数据 */
    @SuppressWarnings("unchecked")
    public static <T> T getData(String key) {
        Map<String, Object> data = contextData.get();
        return data != null ? (T) data.get(key) : null;
    }

    /** 检查是否设置了租户 */
    public static boolean isTenantSet() {
        return currentTenant.get() != null;
    }
}
