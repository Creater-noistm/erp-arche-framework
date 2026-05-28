# 始祖ERP框架 — 数据库表设计说明书

## 设计哲学

### 核心挑战：「动态实体」的持久化

框架的 `DynamicEntity` 允许任意实体类型（Invoice、Product、Employee...），且每个实体类型可在运行时添加自定义字段。传统的关系建模（每实体一张表）不可行，因为：

1. **实体类型数量不确定** — 由模块动态注册
2. **每个实体的字段可扩展** — 用户/实施方随时添加字段
3. **字段类型不同** — String、Decimal、Date、JSON...

### 选型：Hybrid JSONB 方案（推荐）

| 方案 | 灵活度 | 查询性能 | 约束校验 | 框架适配度 |
|------|--------|---------|---------|-----------|
| 纯 EAV | ★★★★★ | ★ | ★★★ | 高 |
| 每实体一张表 | ★★ | ★★★★★ | ★★★★★ | 低 |
| **JSONB 混合** | ★★★★ | ★★★★ | ★★★ | **最佳** |

**选择 JSONB 混合的理由：**
- 标准字段（code, name, status...）→ 固定列 → 强类型 + 索引
- 自定义字段 → JSONB 列 → 无限扩展
- 常用自定义字段可在必要时"提升"为固定列
- PostgreSQL 的 JSONB 支持索引（GIN/btree）和部分索引

---

## 完整表结构（PostgreSQL DDL）

### 1. 租户与系统配置

```sql
-- ============================================================
-- 租户表 — 多租户架构的核心
-- ============================================================
CREATE TABLE sys_tenants (
    id              VARCHAR(64)     PRIMARY KEY,            -- 租户ID (如 "tenant_001")
    name            VARCHAR(255)    NOT NULL,               -- 企业名称
    domain          VARCHAR(255),                           -- 企业域名
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(64),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|SUSPENDED|TRIAL|EXPIRED
    settings_json   JSONB           NOT NULL DEFAULT '{}',  -- {timezone, currency, language...}
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tenants_status ON sys_tenants(status);
COMMENT ON TABLE sys_tenants IS '企业租户 —— 每行一个独立企业';

-- ============================================================
-- 内核配置表（全局级 + 租户级覆盖）
-- ============================================================
CREATE TABLE sys_configs (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(64)     REFERENCES sys_tenants(id) ON DELETE CASCADE,
    config_key      VARCHAR(192)    NOT NULL,
    config_value    TEXT,
    config_type     VARCHAR(32)     DEFAULT 'string',       -- string|int|boolean|json
    description     VARCHAR(512),
    UNIQUE(tenant_id, config_key)
);
CREATE INDEX idx_configs_tenant ON sys_configs(tenant_id);
```

### 2. 模块注册表

```sql
-- ============================================================
-- 模块注册表 — 已安装/已部署的模块
-- ============================================================
CREATE TABLE sys_modules (
    id              VARCHAR(128)    PRIMARY KEY,            -- "erp.finance"
    name            VARCHAR(255)    NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    vendor          VARCHAR(255),
    description     TEXT,
    state           VARCHAR(32)     NOT NULL DEFAULT 'STOPPED', -- STOPPED|STARTED|ERROR
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    start_order     INT             NOT NULL DEFAULT 0,     -- 启动顺序
    config_json     JSONB           NOT NULL DEFAULT '{}',  -- 模块级配置
    installed_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 租户级模块开关（哪些租户启用了哪些模块）
CREATE TABLE sys_tenant_modules (
    tenant_id       VARCHAR(64)     REFERENCES sys_tenants(id) ON DELETE CASCADE,
    module_id       VARCHAR(128)    REFERENCES sys_modules(id) ON DELETE CASCADE,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    config_overrides JSONB          DEFAULT '{}',
    PRIMARY KEY (tenant_id, module_id)
);
```

### 3. 数据模型定义（动态实体的"元数据"）

```sql
-- ============================================================
-- 实体类型定义 — 每行定义一个实体类型（Invoice、Product...）
-- ============================================================
CREATE TABLE meta_entity_types (
    entity_type     VARCHAR(128)    PRIMARY KEY,            -- "Invoice"
    display_name    VARCHAR(255)    NOT NULL,
    module_id       VARCHAR(128)    REFERENCES sys_modules(id),
    description     TEXT,
    table_suffix    VARCHAR(64),                            -- 可选：独立后缀表名
    max_custom_fields INT          NOT NULL DEFAULT 200,
    enable_audit    BOOLEAN         NOT NULL DEFAULT TRUE,
    enable_versioning BOOLEAN       NOT NULL DEFAULT FALSE,  -- 是否启用数据版本管理
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE meta_entity_types IS '实体类型元数据 —— 每行定义一种业务对象的"形状"';

-- ============================================================
-- 标准字段定义 — 实体类型的固定字段描述
-- ============================================================
CREATE TABLE meta_entity_fields (
    id              BIGSERIAL       PRIMARY KEY,
    entity_type     VARCHAR(128)    NOT NULL REFERENCES meta_entity_types(entity_type) ON DELETE CASCADE,
    field_name      VARCHAR(128)    NOT NULL,               -- "invoiceNo"
    display_name    VARCHAR(255)    NOT NULL,
    field_type      VARCHAR(32)     NOT NULL,               -- STRING|DECIMAL|DATE|ENUM|BOOLEAN...
    is_required     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_unique       BOOLEAN         NOT NULL DEFAULT FALSE,
    is_indexed      BOOLEAN         NOT NULL DEFAULT FALSE,
    max_length      INT             DEFAULT 255,
    default_value   VARCHAR(1024),
    validation_pattern VARCHAR(512),
    ref_entity_type VARCHAR(128),                           -- 引用其他实体类型
    sort_order      INT             DEFAULT 0,
    UNIQUE(entity_type, field_name)
);
CREATE INDEX idx_entity_fields_type ON meta_entity_fields(entity_type, sort_order);
COMMENT ON TABLE meta_entity_fields IS '实体类型的标准字段定义 —— 模块注册时写入';

-- ============================================================
-- 自定义字段定义 — 运行时由用户/实施方动态添加
-- ============================================================
CREATE TABLE meta_custom_fields (
    id              BIGSERIAL       PRIMARY KEY,
    entity_type     VARCHAR(128)    NOT NULL REFERENCES meta_entity_types(entity_type) ON DELETE CASCADE,
    field_name      VARCHAR(128)    NOT NULL,               -- "vatRate"
    display_name    VARCHAR(255)    NOT NULL,
    field_type      VARCHAR(32)     NOT NULL,
    is_required     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_unique       BOOLEAN         NOT NULL DEFAULT FALSE,
    is_indexed      BOOLEAN         NOT NULL DEFAULT FALSE,
    max_length      INT             DEFAULT 255,
    default_value   VARCHAR(1024),
    validation_pattern VARCHAR(512),
    allowed_values  JSONB,                                  -- 枚举值列表 ["option1","option2"]
    group_name      VARCHAR(128)    DEFAULT '基本属性',
    sort_order      INT             DEFAULT 0,
    is_visible      BOOLEAN         NOT NULL DEFAULT TRUE,
    is_readonly     BOOLEAN         NOT NULL DEFAULT FALSE,
    tenant_id       VARCHAR(64),                            -- NULL=全局字段，有值=仅某租户自定义
    created_by      VARCHAR(128),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE(entity_type, field_name, COALESCE(tenant_id, '__global__'))
);
CREATE INDEX idx_custom_fields_entity ON meta_custom_fields(entity_type, sort_order);
COMMENT ON TABLE meta_custom_fields IS '自定义字段定义 —— 运行时动态添加，零停机';
```

### 4. 动态实体数据存储（核心！）

```sql
-- ============================================================
-- ★ 实体主表 — 所有 DynamicEntity 的统一存储
--
-- 设计要点：
-- ├─ 所有实体类型共享此表（但每个实体类型也可选择独立分表）
-- ├─ 标准列：id / entity_type / code / name / status / 时间戳
-- ├─ JSONB列 data_json：存储所有字段（标准字段冗余 + 自定义字段）
-- ├─ JSONB列 search_json：提取需要索引的字段值用于查询
-- └─ 按 entity_type + tenant_id 分区
-- ============================================================
CREATE TABLE ent_entities (
    -- ── 核心标识 ──
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(128)    NOT NULL,               -- "Invoice"
    tenant_id       VARCHAR(64)     NOT NULL,               -- 多租户隔离

    -- ── 标准字段（冗余存储以便建索引和约束） ──
    code            VARCHAR(255),                           -- 业务编码
    name            VARCHAR(512),                           -- 名称/标题
    description     TEXT,
    status          VARCHAR(32)     DEFAULT 'DRAFT',        -- DRAFT|PENDING|APPROVED|ACTIVE|...

    -- ── 元数据 ──
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    version         BIGINT          NOT NULL DEFAULT 1,     -- 乐观锁

    -- ── 全字段存储（JSONB） ──
    data_json       JSONB           NOT NULL DEFAULT '{}',  -- 所有字段的完整存储
    search_json     JSONB           NOT NULL DEFAULT '{}',  -- 提取的可索引字段（用于高效查询）

    -- ── 软删除 ──
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP
);
COMMENT ON TABLE ent_entities IS '★★★ 实体主表 —— 所有业务对象的统一存储容器 ★★★';
COMMENT ON COLUMN ent_entities.data_json IS '完整数据：{"invoiceNo":"INV-001", "amount":1000, "vatRate":0.13}';
COMMENT ON COLUMN ent_entities.search_json IS '可查询字段的提取副本：{"vatRate":0.13}';

-- ── 核心索引 ──
CREATE INDEX idx_entities_type     ON ent_entities(entity_type, tenant_id);
CREATE INDEX idx_entities_tenant   ON ent_entities(tenant_id);
CREATE INDEX idx_entities_code     ON ent_entities(entity_type, tenant_id, code) WHERE code IS NOT NULL;
CREATE INDEX idx_entities_status   ON ent_entities(entity_type, tenant_id, status);
CREATE INDEX idx_entities_created  ON ent_entities(entity_type, tenant_id, created_at DESC);
CREATE INDEX idx_entities_updated  ON ent_entities(entity_type, tenant_id, updated_at DESC);

-- ── JSONB 索引 —— 允许在 data_json 内的字段上加速查询 ──
CREATE INDEX idx_entities_data_gin ON ent_entities USING GIN (data_json jsonb_path_ops);
CREATE INDEX idx_entities_search_gin ON ent_entities USING GIN (search_json jsonb_path_ops);

-- ── 分区声明（按实体类型分表，用于超大规模部署） ──
-- CREATE TABLE ent_invoices PARTITION OF ent_entities FOR VALUES IN ('Invoice');
-- CREATE TABLE ent_products PARTITION OF ent_entities FOR VALUES IN ('Product');
-- CREATE TABLE ent_employees PARTITION OF ent_entities FOR VALUES IN ('Employee');
```

### 5. 实体版本历史（可选）

```sql
-- ============================================================
-- 实体变更历史 — 每次更新保留快照（审计和回滚用）
-- ============================================================
CREATE TABLE ent_entity_versions (
    id              BIGSERIAL       PRIMARY KEY,
    entity_id       UUID            NOT NULL REFERENCES ent_entities(id) ON DELETE CASCADE,
    entity_type     VARCHAR(128)    NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    version         BIGINT          NOT NULL,                -- 版本号
    data_snapshot   JSONB           NOT NULL,                -- 该版本的数据快照
    changed_by      VARCHAR(128),
    change_type     VARCHAR(32)     NOT NULL,                -- CREATE|UPDATE|DELETE
    change_reason   VARCHAR(1024),
    changed_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE(entity_id, version)
);
CREATE INDEX idx_entity_versions_eid ON ent_entity_versions(entity_id, version DESC);
```

### 6. 组织架构

```sql
-- ============================================================
-- 组织单元（支持无限层级树）
-- ============================================================
CREATE TABLE org_units (
    id              VARCHAR(64)     PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL REFERENCES sys_tenants(id) ON DELETE CASCADE,
    parent_id       VARCHAR(64)     REFERENCES org_units(id) ON DELETE SET NULL,
    code            VARCHAR(128)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(512),                           -- 完整路径名
    unit_type       VARCHAR(32)     NOT NULL,               -- GROUP|COMPANY|DIVISION|DEPARTMENT|TEAM
    manager_id      VARCHAR(128),                           -- 负责人用户ID
    cost_center_code VARCHAR(64),
    region          VARCHAR(128),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    sort_order      INT             DEFAULT 0,
    path_ids        VARCHAR(1024),                          -- 物化路径 "/root_id/parent_id/this_id"
    path_names      VARCHAR(2048),                          -- 物化路径名 "/集团/公司/部门"
    depth           INT             NOT NULL DEFAULT 0,      -- 树深度
    attributes_json JSONB           DEFAULT '{}',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_org_tenant       ON org_units(tenant_id);
CREATE INDEX idx_org_parent       ON org_units(parent_id);
CREATE INDEX idx_org_type         ON org_units(tenant_id, unit_type);
CREATE INDEX idx_org_path_ids     ON org_units USING GIN (string_to_array(path_ids, '/') _ops);
COMMENT ON TABLE org_units IS '组织树 —— 使用物化路径加速祖先/后代查询';
```

### 7. 权限系统

```sql
-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE auth_users (
    id              VARCHAR(128)    PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL REFERENCES sys_tenants(id) ON DELETE CASCADE,
    username        VARCHAR(128)    NOT NULL,
    display_name    VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(64),
    password_hash   VARCHAR(256)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    is_system_admin BOOLEAN         NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMP,
    attributes_json JSONB           DEFAULT '{}',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, username)
);
CREATE INDEX idx_users_tenant ON auth_users(tenant_id);

-- ============================================================
-- 角色表（支持角色继承）
-- ============================================================
CREATE TABLE auth_roles (
    id              VARCHAR(128)    PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL REFERENCES sys_tenants(id) ON DELETE CASCADE,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    role_type       VARCHAR(32)     DEFAULT 'CUSTOM',       -- BUILT_IN|TEMPLATE|CUSTOM
    is_system       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

-- ============================================================
-- 角色继承关系（角色树）
-- ============================================================
CREATE TABLE auth_role_hierarchy (
    parent_role_id  VARCHAR(128)    NOT NULL REFERENCES auth_roles(id) ON DELETE CASCADE,
    child_role_id   VARCHAR(128)    NOT NULL REFERENCES auth_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id)
);
CREATE INDEX idx_role_hierarchy_child ON auth_role_hierarchy(child_role_id);

-- ============================================================
-- 用户-角色 关联
-- ============================================================
CREATE TABLE auth_user_roles (
    user_id         VARCHAR(128)    NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role_id         VARCHAR(128)    NOT NULL REFERENCES auth_roles(id) ON DELETE CASCADE,
    assigned_by     VARCHAR(128),
    assigned_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role ON auth_user_roles(role_id);

-- ============================================================
-- 权限定义表
-- ============================================================
CREATE TABLE auth_permissions (
    id              VARCHAR(256)    PRIMARY KEY,             -- "finance:invoice:create"
    display_name    VARCHAR(255)    NOT NULL,
    description     TEXT,
    category        VARCHAR(128),                           -- "财务"、"库存"、"HR"...
    is_built_in     BOOLEAN         NOT NULL DEFAULT FALSE,
    module_id       VARCHAR(128)    REFERENCES sys_modules(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 角色-权限 关联
-- ============================================================
CREATE TABLE auth_role_permissions (
    role_id         VARCHAR(128)    NOT NULL REFERENCES auth_roles(id) ON DELETE CASCADE,
    permission_id   VARCHAR(256)    NOT NULL REFERENCES auth_permissions(id) ON DELETE CASCADE,
    granted_by      VARCHAR(128),
    granted_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================
-- 用户直接权限（绕过角色体系）
-- ============================================================
CREATE TABLE auth_user_permissions (
    user_id         VARCHAR(128)    NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    permission_id   VARCHAR(256)    NOT NULL REFERENCES auth_permissions(id) ON DELETE CASCADE,
    is_grant        BOOLEAN         NOT NULL DEFAULT TRUE,   -- TRUE=允许 FALSE=拒绝（黑名单）
    PRIMARY KEY (user_id, permission_id)
);
```

### 8. 审计与事件日志

```sql
-- ============================================================
-- 审计日志 — 所有敏感操作的不可变记录
-- ============================================================
CREATE TABLE sys_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    user_id         VARCHAR(128),
    action          VARCHAR(64)     NOT NULL,                -- CREATE|UPDATE|DELETE|LOGIN|EXPORT
    entity_type     VARCHAR(128),                            -- "Invoice"
    entity_id       VARCHAR(255),                            -- 操作对象的ID
    entity_key      VARCHAR(255),                            -- 业务编码
    details_json    JSONB,                                   -- 变更详情或请求参数
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    result          VARCHAR(32)     DEFAULT 'SUCCESS',       -- SUCCESS|FAILURE|DENIED
    error_message   TEXT,
    duration_ms     INT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant     ON sys_audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_user       ON sys_audit_log(tenant_id, user_id, created_at DESC);
CREATE INDEX idx_audit_entity     ON sys_audit_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_action     ON sys_audit_log(tenant_id, action);
-- 按时间分区（保留90天）
CREATE INDEX idx_audit_created    ON sys_audit_log(created_at);
COMMENT ON TABLE sys_audit_log IS '审计日志 —— 追加写入，禁止修改和删除';

-- ============================================================
-- 事件日志 — EventBus 的历史记录
-- ============================================================
CREATE TABLE sys_event_log (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        VARCHAR(64)     NOT NULL UNIQUE,         -- 全局唯一事件ID
    event_type      VARCHAR(255)    NOT NULL,                -- "module.started"
    source          VARCHAR(128),                            -- 来源模块ID
    priority        VARCHAR(32)     DEFAULT 'NORMAL',
    payload_json    JSONB,
    listener_count  INT             DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_event_type  ON sys_event_log(event_type, created_at DESC);
CREATE INDEX idx_event_source ON sys_event_log(source, created_at DESC);

-- ============================================================
-- 任务调度 / 后台作业
-- ============================================================
CREATE TABLE sys_jobs (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    job_type        VARCHAR(128)    NOT NULL,                -- "INVENTORY_COUNT"|"PAYROLL_RUN"
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING', -- PENDING|RUNNING|COMPLETED|FAILED
    priority        INT             DEFAULT 0,
    params_json     JSONB,
    result_json     JSONB,
    error_message   TEXT,
    scheduled_at    TIMESTAMP,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_by      VARCHAR(128),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_jobs_tenant ON sys_jobs(tenant_id, status, scheduled_at);
```

---

## 核心查询模式

### 1. 创建动态实体（以发票为例）

```sql
-- 框架层自动生成的 SQL
INSERT INTO ent_entities (
    entity_type, tenant_id, code, name, status,
    created_by, data_json, search_json
) VALUES (
    'Invoice',
    'tenant_001',
    'INV-2024-001',
    '销售发票-客户A',
    'PENDING',
    'user_admin',
    -- data_json: 所有字段（标准 + 自定义）
    '{
        "invoiceNo": "INV-2024-001",
        "amount": 10000.00,
        "taxAmount": 1300.00,
        "totalAmount": 11300.00,
        "currency": "CNY",
        "invoiceDate": "2024-06-15",
        "dueDate": "2024-07-15",
        "customerName": "客户A",
        "status": "PENDING",
        "remark": "正常销售",
        "vatRate": 0.13,          -- 自定义字段
        "projectCode": "PJ-2024"  -- 自定义字段
    }',
    -- search_json: 需要索引查询的字段
    '{
        "vatRate": 0.13,
        "projectCode": "PJ-2024"
    }'
);
```

### 2. 按自定义字段查询

```sql
-- 使用 data_json 的 JSONB 查询（函数索引加速）
SELECT id, code, name, data_json
FROM ent_entities
WHERE entity_type = 'Invoice'
  AND tenant_id = 'tenant_001'
  AND data_json->>'vatRate' = '0.13'
  AND (data_json->>'totalAmount')::decimal > 10000
ORDER BY created_at DESC
LIMIT 50;

-- 使用 search_json（更快，需要维护提取逻辑）
SELECT id, code, name, data_json
FROM ent_entities
WHERE entity_type = 'Invoice'
  AND tenant_id = 'tenant_001'
  AND search_json @> '{"vatRate": 0.13}'::jsonb;
```

### 3. 动态获取字段定义（元数据驱动UI）

```sql
-- 获取 Invoice 实体的所有字段（标准 + 自定义）
SELECT f.field_name, f.display_name, f.field_type,
       f.is_required, f.max_length, f.validation_pattern,
       f.sort_order, FALSE as is_custom,
       NULL as allowed_values
FROM meta_entity_fields f
WHERE f.entity_type = 'Invoice'

UNION ALL

SELECT cf.field_name, cf.display_name, cf.field_type,
       cf.is_required, cf.max_length, cf.validation_pattern,
       cf.sort_order, TRUE as is_custom,
       cf.allowed_values
FROM meta_custom_fields cf
WHERE cf.entity_type = 'Invoice'
  AND (cf.tenant_id IS NULL OR cf.tenant_id = 'tenant_001')

ORDER BY sort_order, field_name;
```

### 4. 组织树查询（物化路径）

```sql
-- 获取某部门的所有子孙节点
SELECT * FROM org_units
WHERE path_ids LIKE '/root_id/parent_id/%'
  AND tenant_id = 'tenant_001';

-- 获取某节点的所有祖先
SELECT * FROM org_units
WHERE '/root_id/parent_id/child_id' LIKE path_ids || '/%'
  AND tenant_id = 'tenant_001';
```

### 5. 权限检查（含角色继承）

```sql
-- 获取用户的所有有效权限（含角色继承）
WITH RECURSIVE user_roles AS (
    -- 直接角色
    SELECT ur.user_id, ur.role_id, 0 as depth
    FROM auth_user_roles ur
    WHERE ur.user_id = 'user_001'
    UNION
    -- 角色继承链
    SELECT ur.user_id, rh.parent_role_id, ur.depth + 1
    FROM user_roles ur
    JOIN auth_role_hierarchy rh ON rh.child_role_id = ur.role_id
)
SELECT DISTINCT rp.permission_id
FROM user_roles ur
JOIN auth_role_permissions rp ON rp.role_id = ur.role_id
UNION
SELECT permission_id FROM auth_user_permissions
WHERE user_id = 'user_001' AND is_grant = TRUE
EXCEPT
SELECT permission_id FROM auth_user_permissions
WHERE user_id = 'user_001' AND is_grant = FALSE;
```

---

## 分库分表策略

### 按租户分片

```
# Shard Key = tenant_id
# Proxy: ShardingSphere / MyCat

shard_0:  tenant_001 ~ tenant_100
shard_1:  tenant_101 ~ tenant_200
shard_2:  tenant_201 ~ tenant_300
```

### ent_entities 分区

```sql
-- 按月分区（基于 created_at）
CREATE TABLE ent_entities_202406 PARTITION OF ent_entities
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE ent_entities_202407 PARTITION OF ent_entities
    FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');

-- 或按实体类型分区
CREATE TABLE ent_invoices PARTITION OF ent_entities
    FOR VALUES IN ('Invoice');
CREATE TABLE ent_products PARTITION OF ent_entities
    FOR VALUES IN ('Product');
```

---

## 框架代码 → 数据库的映射层设计

```
DynamicEntity (内存)
      ↓
  [DataRouter]
      ↓
  [EntityPersister]   ← 新组件，将 DynamicEntity 映射为 SQL
      ↓
  [DataSource] (JDBC / MyBatis / JPA)
      ↓
  ent_entities 表
```

新增一个 `EntityPersister` 类即可将现有内存存储替换为数据库存储，而 `DynamicEntity`、`DataRouter`、模块代码**完全不变**。

```java
// 伪代码 — 持久化器接口
public interface EntityPersister {
    void insert(DynamicEntity entity);
    DynamicEntity findById(String entityType, String id, String tenantId);
    List<DynamicEntity> query(String entityType, String tenantId,
                              Map<String, Object> criteria, Pageable page);
    void update(DynamicEntity entity);
    void delete(String entityType, String id, String tenantId);
}
```
