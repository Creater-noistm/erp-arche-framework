package com.erp.kernel;

import java.io.*;
import java.util.Properties;

/**
 * 内核配置 — 框架级参数，模块配置由各自管理。
 * 支持从 properties 文件加载，也支持编程设定。
 */
public class KernelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /* ── 线程池 ── */
    private int coreThreadPoolSize = 4;
    private int maxThreadPoolSize = 32;
    private int schedulerQueueCapacity = 1024;

    /* ── 模块系统 ── */
    private String moduleScanPath = "modules/";
    private boolean enableHotDeploy = true;
    private long moduleStartTimeoutMs = 30_000;

    /* ── 多租户 ── */
    private String defaultTenantId = "default";
    private TenantIsolationLevel tenantIsolation = TenantIsolationLevel.DATABASE;

    /* ── 安全 ── */
    private boolean enableSecurityAudit = true;
    private int passwordHashIterations = 10000;

    /* ── 数据 ── */
    private int maxCustomFieldsPerEntity = 200;
    private boolean enableDynamicValidation = true;

    /* ── 事件 ── */
    private int eventBusQueueCapacity = 4096;
    private boolean asyncEventDispatch = true;

    /* ── UI ── */
    private String uiTheme = "Nimbus";
    private boolean showModuleConsole = true;

    /* ── 扩展属性（模块可在此存取自定义配置） ── */
    private final Properties extensions = new Properties();

    public enum TenantIsolationLevel {
        /** 共享数据库，通过 tenant_id 列隔离 */
        ROW,
        /** 独立表空间/数据源 */
        DATABASE,
        /** 完全独立实例 */
        INSTANCE
    }

    // ── 构造 ──

    public KernelConfig() {}

    /** 从 properties 文件加载 */
    public static KernelConfig load(String path) throws IOException {
        KernelConfig config = new KernelConfig();
        try (InputStream is = new FileInputStream(path)) {
            Properties props = new Properties();
            props.load(is);
            config.applyProperties(props);
        }
        return config;
    }

    private void applyProperties(Properties props) {
        coreThreadPoolSize = intProp(props, "kernel.threads.core", coreThreadPoolSize);
        maxThreadPoolSize = intProp(props, "kernel.threads.max", maxThreadPoolSize);
        moduleScanPath = props.getProperty("kernel.module.scanPath", moduleScanPath);
        enableHotDeploy = boolProp(props, "kernel.module.hotDeploy", enableHotDeploy);
        defaultTenantId = props.getProperty("kernel.tenant.defaultId", defaultTenantId);
        enableSecurityAudit = boolProp(props, "kernel.security.audit", enableSecurityAudit);
        maxCustomFieldsPerEntity = intProp(props, "kernel.model.maxCustomFields", maxCustomFieldsPerEntity);
        uiTheme = props.getProperty("kernel.ui.theme", uiTheme);
        // 剩余的都放入 extensions
        props.forEach((k, v) -> {
            if (!k.toString().startsWith("kernel.")) {
                extensions.setProperty(k.toString(), v.toString());
            }
        });
    }

    private int intProp(Properties p, String key, int def) {
        String v = p.getProperty(key);
        return v != null ? Integer.parseInt(v) : def;
    }

    private boolean boolProp(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v != null ? Boolean.parseBoolean(v) : def;
    }

    // ── Getter / Setter ──

    public int getCoreThreadPoolSize() { return coreThreadPoolSize; }
    public void setCoreThreadPoolSize(int coreThreadPoolSize) { this.coreThreadPoolSize = coreThreadPoolSize; }

    public int getMaxThreadPoolSize() { return maxThreadPoolSize; }
    public void setMaxThreadPoolSize(int maxThreadPoolSize) { this.maxThreadPoolSize = maxThreadPoolSize; }

    public int getSchedulerQueueCapacity() { return schedulerQueueCapacity; }
    public void setSchedulerQueueCapacity(int schedulerQueueCapacity) { this.schedulerQueueCapacity = schedulerQueueCapacity; }

    public String getModuleScanPath() { return moduleScanPath; }
    public void setModuleScanPath(String moduleScanPath) { this.moduleScanPath = moduleScanPath; }

    public boolean isEnableHotDeploy() { return enableHotDeploy; }
    public void setEnableHotDeploy(boolean enableHotDeploy) { this.enableHotDeploy = enableHotDeploy; }

    public long getModuleStartTimeoutMs() { return moduleStartTimeoutMs; }
    public void setModuleStartTimeoutMs(long moduleStartTimeoutMs) { this.moduleStartTimeoutMs = moduleStartTimeoutMs; }

    public String getDefaultTenantId() { return defaultTenantId; }
    public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; }

    public TenantIsolationLevel getTenantIsolation() { return tenantIsolation; }
    public void setTenantIsolation(TenantIsolationLevel tenantIsolation) { this.tenantIsolation = tenantIsolation; }

    public boolean isEnableSecurityAudit() { return enableSecurityAudit; }
    public void setEnableSecurityAudit(boolean enableSecurityAudit) { this.enableSecurityAudit = enableSecurityAudit; }

    public int getPasswordHashIterations() { return passwordHashIterations; }
    public void setPasswordHashIterations(int passwordHashIterations) { this.passwordHashIterations = passwordHashIterations; }

    public int getMaxCustomFieldsPerEntity() { return maxCustomFieldsPerEntity; }
    public void setMaxCustomFieldsPerEntity(int maxCustomFieldsPerEntity) { this.maxCustomFieldsPerEntity = maxCustomFieldsPerEntity; }

    public boolean isEnableDynamicValidation() { return enableDynamicValidation; }
    public void setEnableDynamicValidation(boolean enableDynamicValidation) { this.enableDynamicValidation = enableDynamicValidation; }

    public int getEventBusQueueCapacity() { return eventBusQueueCapacity; }
    public void setEventBusQueueCapacity(int eventBusQueueCapacity) { this.eventBusQueueCapacity = eventBusQueueCapacity; }

    public boolean isAsyncEventDispatch() { return asyncEventDispatch; }
    public void setAsyncEventDispatch(boolean asyncEventDispatch) { this.asyncEventDispatch = asyncEventDispatch; }

    public String getUiTheme() { return uiTheme; }
    public void setUiTheme(String uiTheme) { this.uiTheme = uiTheme; }

    public boolean isShowModuleConsole() { return showModuleConsole; }
    public void setShowModuleConsole(boolean showModuleConsole) { this.showModuleConsole = showModuleConsole; }

    public Properties getExtensions() { return extensions; }

    @Override
    public String toString() {
        return "KernelConfig{threads=" + coreThreadPoolSize + "/" + maxThreadPoolSize
            + ", hotDeploy=" + enableHotDeploy
            + ", tenantIsolation=" + tenantIsolation
            + ", theme=" + uiTheme + "}";
    }
}
