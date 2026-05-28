package com.erp.ui;

/**
 * 版本发行记录 — 从 erp_releases 表读取的更新元数据。
 */
class ReleaseRecord {
    String version;
    String jarUrl;
    String notes;
    byte[] jarData;
}
