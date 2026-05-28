package com.erp.org;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 组织单元 — 企业组织架构的基本节点。
 *
 * 支持任意深度的树形结构，可表示：
 * - 集团 (Group)
 * - 公司 (Company)
 * - 事业部 (Division)
 * - 部门 (Department)
 * - 小组 (Team)
 *
 * 每个单元可拥有负责人、成本中心、区域等属性。
 */
public class OrgUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String tenantId;
    private String parentId;
    private final String code;
    private String name;
    private String fullName;
    private OrgUnitType type;
    private String managerId;       // 负责人用户ID
    private String costCenterCode;  // 成本中心编码
    private String region;          // 区域
    private OrgUnitStatus status;
    private int sortOrder;
    private final List<OrgUnit> children;
    private final Map<String, Object> attributes;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OrgUnit(String id, String tenantId, String code, String name, OrgUnitType type) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.fullName = name;
        this.type = type;
        this.status = OrgUnitStatus.ACTIVE;
        this.children = new ArrayList<>();
        this.attributes = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public enum OrgUnitType {
        GROUP,      // 集团
        COMPANY,    // 公司
        DIVISION,   // 事业部
        DEPARTMENT, // 部门
        TEAM        // 小组
    }

    public enum OrgUnitStatus {
        ACTIVE,
        INACTIVE,
        DISSOLVED
    }

    // ── 树结构操作 ──

    public void addChild(OrgUnit child) {
        child.setParentId(this.id);
        this.children.add(child);
    }

    public void removeChild(String childId) {
        children.removeIf(c -> c.id.equals(childId));
    }

    public Optional<OrgUnit> findChild(String childId) {
        for (OrgUnit child : children) {
            if (child.id.equals(childId)) return Optional.of(child);
            Optional<OrgUnit> found = child.findChild(childId);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** 获取扁平化的所有后代列表 */
    public List<OrgUnit> flatten() {
        List<OrgUnit> result = new ArrayList<>();
        flattenRecursive(result);
        return result;
    }

    private void flattenRecursive(List<OrgUnit> list) {
        list.add(this);
        for (OrgUnit child : children) {
            child.flattenRecursive(list);
        }
    }

    /** 获取从根到当前节点的路径 */
    public List<String> getPath(Map<String, OrgUnit> allUnits) {
        List<String> path = new ArrayList<>();
        OrgUnit current = this;
        while (current != null) {
            path.add(0, current.name);
            current = current.parentId != null ? allUnits.get(current.parentId) : null;
        }
        return path;
    }

    // ── Getters / Setters ──

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; this.updatedAt = LocalDateTime.now(); }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = LocalDateTime.now(); }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; this.updatedAt = LocalDateTime.now(); }
    public OrgUnitType getType() { return type; }
    public void setType(OrgUnitType type) { this.type = type; this.updatedAt = LocalDateTime.now(); }
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; this.updatedAt = LocalDateTime.now(); }
    public String getCostCenterCode() { return costCenterCode; }
    public void setCostCenterCode(String code) { this.costCenterCode = code; this.updatedAt = LocalDateTime.now(); }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; this.updatedAt = LocalDateTime.now(); }
    public OrgUnitStatus getStatus() { return status; }
    public void setStatus(OrgUnitStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public List<OrgUnit> getChildren() { return Collections.unmodifiableList(children); }
    public Map<String, Object> getAttributes() { return Collections.unmodifiableMap(attributes); }
    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "OrgUnit{id='" + id + "', name='" + name + "', type=" + type + "}";
    }
}
