package com.erp.auth;

import com.erp.kernel.MicroKernel;
import com.erp.module.ModulePermission;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 权限管理器 — 统一认证与授权服务。
 *
 * 职责：
 * 1. 用户认证（登录验证）
 * 2. 权限检查（用户 → 角色 → 权限）
 * 3. 模块权限注册
 * 4. 权限缓存
 * 5. 审计日志
 *
 * 权限解析链：
 *   用户直接权限 + 所有角色权限 + 角色继承链权限 = 最终有效权限
 */
public class AuthManager {

    private static final Logger log = LoggerFactory.getLogger(AuthManager.class);

    private final MicroKernel kernel;

    /** 所有已注册的权限定义 */
    private final Map<String, Permission> permissionRegistry = new ConcurrentHashMap<>();

    /** 用户存储（生产环境应替换为数据库实现） */
    private final Map<String, User> users = new ConcurrentHashMap<>();

    /** 角色存储 */
    private final Map<String, Role> roles = new ConcurrentHashMap<>();

    /** 权限缓存： userId → Set<权限ID> */
    private final Map<String, Set<String>> permissionCache = new ConcurrentHashMap<>();

    public AuthManager(MicroKernel kernel) {
        this.kernel = kernel;
    }

    public void initialize() {
        // 注册内置权限
        registerBuiltinPermissions();
        log.info("AuthManager initialized");
    }

    // ── 权限注册 ──

    /** 注册一个权限（从 ModulePermission 委托） */
    public void registerPermission(ModulePermission modulePerm) {
        registerPermission(new Permission(
            modulePerm.id(),
            modulePerm.displayName(),
            modulePerm.description(),
            modulePerm.moduleId(),
            false
        ));
    }

    /** 注册一个权限（从 Permission 对象） */
    public void registerPermission(Permission perm) {
        permissionRegistry.put(perm.getId(), perm);
        log.debug("Permission registered: {}", perm.getId());
    }

    /** 注册多个权限 */
    public void registerPermissions(List<ModulePermission> modulePerms) {
        for (ModulePermission mp : modulePerms) {
            registerPermission(mp);
        }
    }

    /** 注册内置权限 */
    private void registerBuiltinPermissions() {
        // 系统级权限
        registerPermission(new Permission("system:admin", "系统管理", "系统管理员权限", "系统"));
        registerPermission(new Permission("system:config:read", "读取配置", "读取系统配置", "系统"));
        registerPermission(new Permission("system:config:write", "修改配置", "修改系统配置", "系统"));
        registerPermission(new Permission("system:module:manage", "模块管理", "安装/卸载/配置模块", "系统"));
        registerPermission(new Permission("system:tenant:manage", "租户管理", "管理多租户", "系统"));
        registerPermission(new Permission("system:user:manage", "用户管理", "创建/修改/删除用户", "系统"));
        registerPermission(new Permission("system:role:manage", "角色管理", "创建/修改/删除角色", "系统"));
        registerPermission(new Permission("system:permission:manage", "权限管理", "管理权限定义", "系统"));
        registerPermission(new Permission("system:audit:view", "审计日志", "查看审计日志", "系统"));

        // 组织架构权限
        registerPermission(new Permission("org:structure:read", "查看组织", "查看组织架构", "组织"));
        registerPermission(new Permission("org:structure:write", "修改组织", "修改组织架构", "组织"));
        registerPermission(new Permission("org:employee:read", "查看员工", "查看员工信息", "组织"));
        registerPermission(new Permission("org:employee:write", "管理员工", "管理员工信息", "组织"));
    }

    // ── 用户管理 ──

    public void addUser(User user) {
        users.put(user.getId(), user);
        permissionCache.remove(user.getId());
    }

    public void removeUser(String userId) {
        users.remove(userId);
        permissionCache.remove(userId);
    }

    public User getUser(String userId) {
        return users.get(userId);
    }

    public User getUserByUsername(String username) {
        return users.values().stream()
            .filter(u -> u.getUsername().equals(username)
                && u.getTenantId().equals(TenantContext.getCurrentTenantId()))
            .findFirst()
            .orElse(null);
    }

    public List<User> getAllUsers() {
        String tenantId = TenantContext.getCurrentTenantId();
        return users.values().stream()
            .filter(u -> u.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
    }

    // ── 角色管理 ──

    public void addRole(Role role) {
        roles.put(role.getId(), role);
        // 清除受影响的缓存
        users.values().stream()
            .filter(u -> u.hasRole(role.getId()))
            .forEach(u -> permissionCache.remove(u.getId()));
    }

    public void removeRole(String roleId) {
        roles.remove(roleId);
        users.values().stream()
            .filter(u -> u.hasRole(roleId))
            .forEach(u -> {
                u.removeRole(roleId);
                permissionCache.remove(u.getId());
            });
    }

    public Role getRole(String roleId) {
        return roles.get(roleId);
    }

    public List<Role> getAllRoles() {
        String tenantId = TenantContext.getCurrentTenantId();
        return roles.values().stream()
            .filter(r -> r.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
    }

    // ── 认证 ──

    /**
     * 简单认证（生产需替换为安全哈希验证）
     * @return 认证成功返回 User，否则 null
     */
    public User authenticate(String username, String password) {
        User user = getUserByUsername(username);
        if (user == null) {
            log.warn("Authentication failed: user '{}' not found", username);
            return null;
        }
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            log.warn("Authentication failed: user '{}' is {}", username, user.getStatus());
            return null;
        }
        // 简单密码校验（生产应使用 BCrypt 等）
        String expectedHash = hashPassword(password);
        if (!expectedHash.equals(user.getPasswordHash())) {
            log.warn("Authentication failed: wrong password for '{}'", username);
            return null;
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        log.info("User '{}' authenticated successfully", username);

        // 发布登录事件
        kernel.getEventBus().publish(new com.erp.event.ErpEvent(
            "user.login",
            Map.of("userId", user.getId(), "username", user.getUsername()),
            com.erp.event.ErpEvent.Priority.NORMAL
        ));

        return user;
    }

    private String hashPassword(String password) {
        // 占位：生产应使用 BCrypt / PBKDF2
        return Integer.toHexString((password + "erp_salt_2024").hashCode());
    }

    // ── 授权 ──

    /**
     * 检查用户是否有指定权限。
     * 系统管理员拥有所有权限。
     */
    public boolean hasPermission(String userId, String permissionId) {
        User user = users.get(userId);
        if (user == null) return false;
        if (user.isSystemAdmin()) return true;
        if (user.getStatus() != User.UserStatus.ACTIVE) return false;

        Set<String> effectivePerms = getEffectivePermissions(userId);

        // 通配符匹配
        for (String perm : effectivePerms) {
            Permission p = permissionRegistry.get(perm);
            if (p != null && p.matches(permissionId)) return true;
            // 精确匹配
            if (perm.equals(permissionId)) return true;
            // 简单通配
            if (perm.endsWith(":*") && permissionId.startsWith(perm.substring(0, perm.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前线程上下文中用户是否有权限。
     * 从 TenantContext 获取当前用户。
     */
    public boolean checkPermission(String permissionId) {
        String userId = TenantContext.getData("currentUserId");
        return userId != null && hasPermission(userId, permissionId);
    }

    /** 获取用户的所有有效权限（合并自角色 + 直接授权 + 角色继承） */
    public Set<String> getEffectivePermissions(String userId) {
        // 缓存命中
        Set<String> cached = permissionCache.get(userId);
        if (cached != null) return cached;

        User user = users.get(userId);
        if (user == null) return Set.of();

        Set<String> effectivePerms = ConcurrentHashMap.newKeySet();

        // 1. 用户直接权限
        effectivePerms.addAll(user.getDirectPermissions());

        // 2. 角色的权限 + 角色继承链
        for (String roleId : user.getRoleIds()) {
            collectRolePermissions(roleId, effectivePerms, new HashSet<>());
        }

        Set<String> result = Collections.unmodifiableSet(effectivePerms);
        permissionCache.put(userId, result);
        return result;
    }

    private void collectRolePermissions(String roleId, Set<String> target, Set<String> visited) {
        if (roleId == null || visited.contains(roleId)) return;
        visited.add(roleId);

        Role role = roles.get(roleId);
        if (role == null) return;

        // 自身权限
        target.addAll(role.getPermissionIds());

        // 父角色权限（继承）
        for (String parentId : role.getParentRoleIds()) {
            collectRolePermissions(parentId, target, visited);
        }
    }

    /** 清除用户权限缓存 */
    public void clearCache(String userId) {
        permissionCache.remove(userId);
    }

    public void clearAllCache() {
        permissionCache.clear();
    }

    // ── 查询 ──

    public Permission getRegisteredPermission(String id) {
        return permissionRegistry.get(id);
    }

    public List<Permission> getAllRegisteredPermissions() {
        return List.copyOf(permissionRegistry.values());
    }

    public List<Permission> getPermissionsByCategory(String category) {
        return permissionRegistry.values().stream()
            .filter(p -> category.equals(p.getCategory()))
            .collect(Collectors.toList());
    }

    public int getUserCount() { return users.size(); }
    public int getRoleCount() { return roles.size(); }
    public int getPermissionCount() { return permissionRegistry.size(); }
}
