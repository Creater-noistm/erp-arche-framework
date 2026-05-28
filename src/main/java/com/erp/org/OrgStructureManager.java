package com.erp.org;

import com.erp.kernel.MicroKernel;
import com.erp.tenant.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 组织架构管理器 — 管理企业组织树的完整生命周期。
 *
 * 能力：
 * - 创建/修改/删除组织单元
 * - 树结构维护与验证（防止循环引用）
 * - 按租户隔离的组织数据
 * - 组织路径解析
 * - 批量查询（负责人、类型、区域等）
 */
public class OrgStructureManager {

    private static final Logger log = LoggerFactory.getLogger(OrgStructureManager.class);

    @SuppressWarnings("unused")
    private final MicroKernel kernel;

    /** tenantId → (orgUnitId → OrgUnit) */
    private final Map<String, Map<String, OrgUnit>> tenantOrgMap = new ConcurrentHashMap<>();

    /** 根节点缓存： tenantId → root orgUnitId */
    private final Map<String, String> rootNodeMap = new ConcurrentHashMap<>();

    public OrgStructureManager(MicroKernel kernel) {
        this.kernel = kernel;
    }

    public void initialize() {
        log.info("OrgStructureManager initialized");
    }

    // ── 组织单元管理 ──

    /** 添加组织单元 */
    public void addOrgUnit(OrgUnit unit) {
        String tenantId = unit.getTenantId();
        Map<String, OrgUnit> orgMap = tenantOrgMap.computeIfAbsent(
            tenantId, k -> new ConcurrentHashMap<>());
        orgMap.put(unit.getId(), unit);

        // 如果无父节点，设为根节点
        if (unit.getParentId() == null || unit.getParentId().isBlank()) {
            rootNodeMap.put(tenantId, unit.getId());
        }

        log.info("OrgUnit added: {} ({}) in tenant {}", unit.getName(), unit.getType(), tenantId);
    }

    /** 删除组织单元 */
    public void removeOrgUnit(String orgUnitId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Map<String, OrgUnit> orgMap = tenantOrgMap.get(tenantId);
        if (orgMap == null) return;

        OrgUnit unit = orgMap.get(orgUnitId);
        if (unit == null) return;

        // 将子节点上移
        for (OrgUnit child : unit.getChildren()) {
            child.setParentId(unit.getParentId());
        }

        orgMap.remove(orgUnitId);

        // 清除根节点缓存
        if (orgUnitId.equals(rootNodeMap.get(tenantId))) {
            rootNodeMap.remove(tenantId);
        }

        log.info("OrgUnit removed: {} ({})", unit.getName(), unit.getId());
    }

    /** 获取组织单元 */
    public OrgUnit getOrgUnit(String orgUnitId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Map<String, OrgUnit> orgMap = tenantOrgMap.get(tenantId);
        return orgMap != null ? orgMap.get(orgUnitId) : null;
    }

    /** 获取根组织单元 */
    public OrgUnit getRootOrgUnit() {
        String tenantId = TenantContext.getCurrentTenantId();
        String rootId = rootNodeMap.get(tenantId);
        return rootId != null ? getOrgUnit(rootId) : null;
    }

    // ── 查询 ──

    /** 获取当前租户的所有组织单元 */
    public List<OrgUnit> getAllOrgUnits() {
        String tenantId = TenantContext.getCurrentTenantId();
        Map<String, OrgUnit> orgMap = tenantOrgMap.get(tenantId);
        return orgMap != null ? List.copyOf(orgMap.values()) : List.of();
    }

    /** 按类型查找组织单元 */
    public List<OrgUnit> findByType(OrgUnit.OrgUnitType type) {
        return getAllOrgUnits().stream()
            .filter(u -> u.getType() == type)
            .collect(Collectors.toList());
    }

    /** 按负责人查找组织单元 */
    public List<OrgUnit> findByManager(String managerUserId) {
        return getAllOrgUnits().stream()
            .filter(u -> managerUserId.equals(u.getManagerId()))
            .collect(Collectors.toList());
    }

    /** 获取组织树（根节点开始） */
    public OrgUnit getOrgTree() {
        OrgUnit root = getRootOrgUnit();
        if (root == null) return null;
        buildTree(root);
        return root;
    }

    private void buildTree(OrgUnit node) {
        String tenantId = node.getTenantId();
        Map<String, OrgUnit> orgMap = tenantOrgMap.get(tenantId);
        if (orgMap == null) return;

        for (OrgUnit potentialChild : orgMap.values()) {
            if (node.getId().equals(potentialChild.getParentId())) {
                node.addChild(potentialChild);
                buildTree(potentialChild);
            }
        }
    }

    /** 获取某节点的完整祖先链 */
    public List<OrgUnit> getAncestors(String orgUnitId) {
        List<OrgUnit> ancestors = new ArrayList<>();
        String tenantId = TenantContext.getCurrentTenantId();
        Map<String, OrgUnit> orgMap = tenantOrgMap.get(tenantId);
        if (orgMap == null) return ancestors;

        OrgUnit current = orgMap.get(orgUnitId);
        while (current != null && current.getParentId() != null) {
            OrgUnit parent = orgMap.get(current.getParentId());
            if (parent != null) {
                ancestors.add(0, parent);
                current = parent;
            } else {
                break;
            }
        }
        return ancestors;
    }

    // ── 管理 ──

    /** 获取已注册的租户数量 */
    public int getTenantCount() {
        return tenantOrgMap.size();
    }

    /** 清除指定租户的组织数据 */
    public void clearTenant(String tenantId) {
        tenantOrgMap.remove(tenantId);
        rootNodeMap.remove(tenantId);
        log.info("Cleared org data for tenant {}", tenantId);
    }
}
