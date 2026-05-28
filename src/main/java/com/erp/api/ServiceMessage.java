package com.erp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 服务消息 — 模块间远程/本地调用的标准协议格式。
 *
 * 所有模块间通信经由 ServiceMessage 封装，确保：
 * - 一致的请求/响应格式
 * - 统一的错误处理
 * - 可序列化（JSON）
 * - 可追踪（消息ID + 时间戳）
 */
public class ServiceMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全局序列化器 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final String messageId;
    private final MessageType type;
    private final String serviceName;
    private final String methodName;
    private final String version;
    private final String tenantId;
    private final String sourceModuleId;
    private final String targetModuleId;
    private final Object payload;
    private final Map<String, Object> headers;
    private final LocalDateTime timestamp;

    private ServiceMessage(Builder builder) {
        this.messageId = builder.messageId != null ? builder.messageId : UUID.randomUUID().toString();
        this.type = builder.type;
        this.serviceName = builder.serviceName;
        this.methodName = builder.methodName;
        this.version = builder.version;
        this.tenantId = builder.tenantId;
        this.sourceModuleId = builder.sourceModuleId;
        this.targetModuleId = builder.targetModuleId;
        this.payload = builder.payload;
        this.headers = builder.headers;
        this.timestamp = LocalDateTime.now();
    }

    // ── 工厂方法 ──

    /** 创建请求消息 */
    public static ServiceMessage request(String serviceName, String methodName, Object payload) {
        return builder()
            .type(MessageType.REQUEST)
            .serviceName(serviceName)
            .methodName(methodName)
            .payload(payload)
            .build();
    }

    /** 创建成功响应 */
    public static ServiceMessage success(Object data) {
        return builder()
            .type(MessageType.RESPONSE)
            .payload(new ServiceResult(true, data, null))
            .build();
    }

    /** 创建错误响应 */
    public static ServiceMessage error(String errorMessage) {
        return builder()
            .type(MessageType.RESPONSE)
            .payload(new ServiceResult(false, null, errorMessage))
            .build();
    }

    /** 创建事件消息 */
    public static ServiceMessage event(String eventType, Map<String, Object> data) {
        return builder()
            .type(MessageType.EVENT)
            .serviceName(eventType)
            .payload(data)
            .build();
    }

    // ── JSON 序列化 ──

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ServiceMessage", e);
        }
    }

    public static ServiceMessage fromJson(String json) {
        try {
            return MAPPER.readValue(json, ServiceMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize ServiceMessage", e);
        }
    }

    // ── Getters ──

    public String getMessageId() { return messageId; }
    public MessageType getType() { return type; }
    public String getServiceName() { return serviceName; }
    public String getMethodName() { return methodName; }
    public String getVersion() { return version; }
    public String getTenantId() { return tenantId; }
    public String getSourceModuleId() { return sourceModuleId; }
    public String getTargetModuleId() { return targetModuleId; }
    public Object getPayload() { return payload; }
    public Map<String, Object> getHeaders() { return headers; }
    public LocalDateTime getTimestamp() { return timestamp; }

    /** 检查是否为成功响应 */
    public boolean isSuccess() {
        if (type != MessageType.RESPONSE || payload == null) return false;
        if (payload instanceof ServiceResult r) return r.success();
        return false;
    }

    /** 获取响应数据 */
    @SuppressWarnings("unchecked")
    public <T> T getData() {
        if (payload instanceof ServiceResult r) return (T) r.data();
        return (T) payload;
    }

    /** 获取错误消息 */
    public String getErrorMessage() {
        if (payload instanceof ServiceResult r && !r.success()) {
            return r.errorMessage();
        }
        return null;
    }

    // ── Builder ──

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String messageId;
        private MessageType type;
        private String serviceName;
        private String methodName;
        private String version = "1.0.0";
        private String tenantId;
        private String sourceModuleId;
        private String targetModuleId;
        private Object payload;
        private Map<String, Object> headers = Map.of();

        public Builder messageId(String id) { this.messageId = id; return this; }
        public Builder type(MessageType type) { this.type = type; return this; }
        public Builder serviceName(String name) { this.serviceName = name; return this; }
        public Builder methodName(String name) { this.methodName = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder sourceModuleId(String moduleId) { this.sourceModuleId = moduleId; return this; }
        public Builder targetModuleId(String moduleId) { this.targetModuleId = moduleId; return this; }
        public Builder payload(Object payload) { this.payload = payload; return this; }
        public Builder headers(Map<String, Object> headers) { this.headers = headers; return this; }

        public ServiceMessage build() {
            if (type == null) throw new IllegalStateException("type is required");
            return new ServiceMessage(this);
        }
    }

    // ── 消息类型 ──

    public enum MessageType {
        REQUEST,    // 请求
        RESPONSE,   // 响应
        EVENT,      // 事件通知
        COMMAND,    // 命令
        QUERY       // 查询
    }

    /** 统一服务结果 */
    public record ServiceResult(
        boolean success,
        Object data,
        String errorMessage
    ) {}

    @Override
    public String toString() {
        return "ServiceMessage{" +
            "type=" + type +
            ", service='" + serviceName + "." + methodName + '\'' +
            ", from='" + sourceModuleId + '\'' +
            '}';
    }
}
