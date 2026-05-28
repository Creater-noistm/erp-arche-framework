package com.erp.tenant;

/**
 * 登录后的用户与租户信息快照。
 */
public record LoginSession(String tenantId, String tenantName, String userId, String username,
                           String displayName, String roleId, boolean isSystemAdmin) {}
