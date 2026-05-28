package com.erp.module;

/**
 * 模块生命周期状态枚举。
 *
 * ┌──────────┐    deploy()    ┌──────────────┐
 * │ CREATED   │ ────────────→ │ INITIALIZED  │
 * └──────────┘               └──────┬───────┘
 *                                   │ start()
 *                                  ↓
 *                            ┌───────────┐
 *                            │  STARTED   │
 *                            └─────┬─────┘
 *                                  │ stop()
 *                                  ↓
 *                            ┌───────────┐
 *                            │  STOPPED   │
 *                            └─────┬─────┘
 *                                  │ destroy()
 *                                  ↓
 *                            ┌────────────┐
 *                            │  DESTROYED │
 *                            └────────────┘
 *
 * ERROR 状态可从任何活跃状态进入。
 */
public enum ModuleState {
    /** 已创建但未初始化 */
    CREATED,
    /** 已初始化但未启动 */
    INITIALIZED,
    /** 运行中 */
    STARTED,
    /** 已停止 */
    STOPPED,
    /** 已销毁 */
    DESTROYED,
    /** 错误状态 */
    ERROR
}
