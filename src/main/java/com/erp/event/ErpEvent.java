package com.erp.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局事件 — 模块间通信的基本单位。
 *
 * 不可变对象，创建后不可修改。
 * 事件类型是字符串（命名空间:名称），支持点号分层。
 *
 * 内置事件类型：
 *   kernel.startup      — 内核启动完成
 *   kernel.shutdown     — 内核关闭中
 *   module.started      — 模块已启动
 *   module.stopped      — 模块已停止
 *   module.error        — 模块异常
 *   tenant.switched     — 租户切换
 *   user.login          — 用户登录
 *   user.logout         — 用户登出
 *   data.entity.created — 实体创建
 *   data.entity.updated — 实体更新
 *   data.entity.deleted — 实体删除
 */
public record ErpEvent(
    /** 全局唯一事件ID */
    String eventId,

    /** 事件类型（如 "module.started"） */
    String type,

    /** 事件负载 */
    Map<String, Object> payload,

    /** 优先级 */
    Priority priority,

    /** 来源（模块ID 或 "kernel"） */
    String source,

    /** 时间戳 */
    long timestamp
) {
    public ErpEvent(String type, Map<String, Object> payload, Priority priority) {
        this(UUID.randomUUID().toString(), type,
            payload == null ? Map.of() : new ConcurrentHashMap<>(payload),
            priority, "kernel", System.currentTimeMillis());
    }

    public ErpEvent withSource(String source) {
        return new ErpEvent(this.eventId, this.type, this.payload,
            this.priority, source, this.timestamp);
    }

    @SuppressWarnings("unchecked")
    public <T> T getPayloadValue(String key) {
        return (T) payload.get(key);
    }

    public enum Priority {
        LOW,        // 后台任务通知
        NORMAL,     // 常规业务事件
        HIGH,       // 需要尽快处理
        CRITICAL    // 关键事件（如模块故障）
    }
}
