package com.erp.monitor;

import com.erp.db.DatabaseManager;
import com.erp.kernel.MicroKernel;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查 — 提供 DB 连通性、内核状态、内存、模块数等运行时指标。
 */
public class HealthCheck {

    private static final LocalDateTime startTime = LocalDateTime.now();

    private HealthCheck() {}

    /** 完整健康报告（JSON-friendly Map） */
    public static Map<String, Object> report() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", isHealthy() ? "UP" : "DOWN");
        r.put("uptime", Duration.between(startTime, LocalDateTime.now()).toSeconds() + "s");

        if (MicroKernel.isActive()) {
            MicroKernel kernel = MicroKernel.getInstance();
            r.put("kernel", kernel.getState().name());
            r.put("modules", kernel.getModuleManager().getActiveModuleCount());
            r.put("services", kernel.getServiceRegistry().getServiceCount());
            r.put("events", kernel.getEventBus().getTotalPublished());
        } else {
            r.put("kernel", "INACTIVE");
        }

        r.put("db", dbStatus());
        r.put("memory", memoryInfo());
        return r;
    }

    /** 快速存活检查 */
    public static boolean isHealthy() {
        return MicroKernel.isActive() && dbPing();
    }

    private static Map<String, Object> dbStatus() {
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            DatabaseManager.getInstance().executeQuery("SELECT 1", rs -> rs.next());
            db.put("connected", true);
        } catch (Exception e) {
            db.put("connected", false);
            db.put("error", e.getMessage());
        }
        return db;
    }

    private static boolean dbPing() {
        try {
            DatabaseManager.getInstance().executeQuery("SELECT 1", rs -> true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, Object> memoryInfo() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heap_used_mb", rt.totalMemory() / 1024 / 1024 - rt.freeMemory() / 1024 / 1024);
        m.put("heap_max_mb", rt.maxMemory() / 1024 / 1024);
        m.put("heap_committed_mb", rt.totalMemory() / 1024 / 1024);
        return m;
    }

    /** 启动时间 */
    public static LocalDateTime getStartTime() {
        return startTime;
    }
}
