package com.erp.event;

/**
 * 事件监听器 — 模块通过实现此接口订阅事件。
 *
 * 使用 EventBus.subscribe() 注册。
 * 监听器按优先级排序执行，同优先级按注册顺序。
 */
@FunctionalInterface
public interface EventListener {

    /** 处理事件 */
    void onEvent(ErpEvent event);

    /** 监听器名称（用于日志和诊断） */
    default String getName() {
        return getClass().getSimpleName();
    }

    /** 对此监听器，感兴趣的事件类型（空集合 = 全部） */
    default java.util.Set<String> getInterestedTypes() {
        return java.util.Set.of();
    }

    /** 优先级（数值越大越优先执行） */
    default int getPriority() {
        return 0;
    }

    /** 是否异步执行此监听器 */
    default boolean isAsync() {
        return false;
    }
}
