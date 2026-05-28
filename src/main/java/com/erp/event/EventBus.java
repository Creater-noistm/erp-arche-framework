package com.erp.event;

import com.erp.kernel.MicroKernel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ═══════════════════════════════════════════════════════════════
 * EventBus — 始祖ERP框架的"中枢神经系统"
 *
 * 职责：
 * - 模块间异步/同步通信
 * - 事件类型过滤与路由
 * - 优先级调度
 * - 死信处理
 * - 事件溯源（可选）
 *
 * 设计特色：
 * - 支持通配符订阅（如 "data.entity.*" 匹配所有数据实体事件）
 * - 同步监听器按优先级顺序执行
 * - 异步监听器由线程池执行
 * - 支持事件过滤（Listener 可指定只关心哪些类型）
 * ═══════════════════════════════════════════════════════════════
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final MicroKernel kernel;
    private final List<ListenerEntry> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Set<ListenerEntry>> typeIndex = new ConcurrentHashMap<>();

    /* 异步分发线程池 */
    private final ExecutorService asyncExecutor;

    /* 统计 */
    private final AtomicLong totalEventsPublished = new AtomicLong(0);
    private final AtomicLong totalEventsDispatched = new AtomicLong(0);

    /* 死信队列 */
    private final BlockingQueue<ErpEvent> deadLetterQueue = new LinkedBlockingQueue<>();

    public EventBus(MicroKernel kernel) {
        this.kernel = kernel;
        int poolSize = kernel.getConfig().getCoreThreadPoolSize();
        this.asyncExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "eventbus-dispatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public void initialize() {
        // 启动死信处理线程
        Thread dlqThread = new Thread(this::processDeadLetters, "eventbus-dlq");
        dlqThread.setDaemon(true);
        dlqThread.start();
        log.info("EventBus initialized");
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
        listeners.clear();
        typeIndex.clear();
        log.info("EventBus shut down. Total events published: {}", totalEventsPublished.get());
    }

    // ── 订阅 ──

    /**
     * 订阅事件。
     *
     * @param listener 事件监听器
     * @return AutoCloseable，调用 close() 可取消订阅
     */
    public AutoCloseable subscribe(EventListener listener) {
        ListenerEntry entry = new ListenerEntry(listener);
        listeners.add(entry);

        // 构建类型索引
        Set<String> types = listener.getInterestedTypes();
        if (types.isEmpty()) {
            // 空集合 = 订阅所有
            typeIndex.computeIfAbsent("*", k -> ConcurrentHashMap.newKeySet()).add(entry);
        } else {
            for (String type : types) {
                typeIndex.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(entry);
            }
        }

        log.debug("Listener subscribed: {}", listener.getName());

        return () -> {
            listeners.remove(entry);
            typeIndex.values().forEach(s -> s.remove(entry));
        };
    }

    /** 便捷订阅（按类型 + lambda） */
    public AutoCloseable subscribe(String eventType, EventListener listener) {
        EventListener filtered = new EventListener() {
            @Override
            public void onEvent(ErpEvent event) {
                if (event.type().equals(eventType) || eventType.endsWith("*")) {
                    listener.onEvent(event);
                }
            }

            @Override
            public String getName() { return listener.getName(); }

            @Override
            public Set<String> getInterestedTypes() {
                return Set.of(eventType);
            }

            @Override
            public int getPriority() { return listener.getPriority(); }

            @Override
            public boolean isAsync() { return listener.isAsync(); }
        };
        return subscribe(filtered);
    }

    // ── 发布 ──

    /** 发布事件（同步/异步取决于监听器配置） */
    public void publish(ErpEvent event) {
        if (event == null) return;

        totalEventsPublished.incrementAndGet();

        // 找匹配的监听器
        List<ListenerEntry> matched = findMatchingListeners(event.type());

        if (matched.isEmpty()) {
            log.trace("No listeners for event: {}", event.type());
            return;
        }

        // 按优先级排序
        matched.sort(Comparator.comparingInt(ListenerEntry::priority).reversed());

        for (ListenerEntry entry : matched) {
            try {
                if (entry.async && kernel.getConfig().isAsyncEventDispatch()) {
                    // 异步分发
                    asyncExecutor.submit(() -> {
                        try {
                            entry.listener.onEvent(event);
                            totalEventsDispatched.incrementAndGet();
                        } catch (Exception e) {
                            log.error("Async listener error: {}", entry.name, e);
                            deadLetterQueue.offer(event);
                        }
                    });
                } else {
                    // 同步分发
                    entry.listener.onEvent(event);
                    totalEventsDispatched.incrementAndGet();
                }
            } catch (Exception e) {
                log.error("Listener error: {}", entry.name, e);
                if (event.priority() == ErpEvent.Priority.CRITICAL) {
                    deadLetterQueue.offer(event);
                }
            }
        }
    }

    /** 发布事件并等待所有同步监听器执行完毕 */
    public void publishAndWait(ErpEvent event) {
        publish(event);
        // 等待异步任务完成（仅用于测试/关键路径）
        if (kernel.getConfig().isAsyncEventDispatch()) {
            try {
                asyncExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("publishAndWait timeout", e);
            }
        }
    }

    // ── 查询 ──

    public int getListenerCount() { return listeners.size(); }
    public long getTotalPublished() { return totalEventsPublished.get(); }
    public long getTotalDispatched() { return totalEventsDispatched.get(); }
    public int getDeadLetterCount() { return deadLetterQueue.size(); }

    // ── 内部 ──

    private List<ListenerEntry> findMatchingListeners(String type) {
        Set<ListenerEntry> matched = new HashSet<>();

        // 精确匹配
        Set<ListenerEntry> exact = typeIndex.get(type);
        if (exact != null) matched.addAll(exact);

        // 通配符匹配：将类型按点分段，逐层匹配
        String[] parts = type.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".");
            sb.append(parts[i]);

            // 匹配 "module.*" 或 "module.resource.*"
            String wildcard = sb + ".*";
            Set<ListenerEntry> wildcardMatch = typeIndex.get(wildcard);
            if (wildcardMatch != null) matched.addAll(wildcardMatch);
        }

        // 全局通配符
        Set<ListenerEntry> global = typeIndex.get("*");
        if (global != null) matched.addAll(global);

        return new ArrayList<>(matched);
    }

    private void processDeadLetters() {
        while (true) {
            try {
                ErpEvent event = deadLetterQueue.poll(30, TimeUnit.SECONDS);
                if (event != null) {
                    log.warn("Dead letter event: type={}, source={}, priority={}",
                        event.type(), event.source(), event.priority());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private record ListenerEntry(
        EventListener listener,
        String name,
        int priority,
        boolean async,
        Set<String> types
    ) {
        ListenerEntry(EventListener listener) {
            this(listener, listener.getName(), listener.getPriority(),
                listener.isAsync(), listener.getInterestedTypes());
        }
    }
}
