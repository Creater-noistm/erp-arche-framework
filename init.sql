-- ============================================================
-- 始祖ERP框架 — H2 数据库初始化脚本
-- 架构对齐：鉴权(租户/用户) + 业务(entity存储)
-- ============================================================

-- ── 1. 租户表（对齐「公司IP信息表」） ──
CREATE TABLE IF NOT EXISTS erp_tenants (
    id              VARCHAR(64)     PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    code            VARCHAR(64)     NOT NULL UNIQUE,        -- 公司简称
    db_url          VARCHAR(512),                           -- 业务库地址（扩展用）
    contact_name    VARCHAR(128),
    contact_phone   VARCHAR(64),
    status          VARCHAR(32)     DEFAULT 'ACTIVE',       -- ACTIVE|SUSPENDED|TRIAL
    settings_json   VARCHAR(2048)   DEFAULT '{}',
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ── 2. 角色表 ──
CREATE TABLE IF NOT EXISTS erp_roles (
    id              VARCHAR(128)    PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    tenant_id       VARCHAR(64),                            -- NULL=全局角色
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ── 3. 用户表（对齐「公司网页用户表」） ──
CREATE TABLE IF NOT EXISTS erp_users (
    id              VARCHAR(128)    PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,               -- 所属公司
    username        VARCHAR(128)    NOT NULL,
    display_name    VARCHAR(255),
    email           VARCHAR(255),
    password_hash   VARCHAR(256)    NOT NULL,
    role_id         VARCHAR(128),
    is_system_admin BOOLEAN         DEFAULT FALSE,          -- 超级管理员(可见租户管理)
    status          VARCHAR(32)     DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, username)
);

-- ── 4. 登录日志（对齐「网页登陆记录表」） ──
CREATE TABLE IF NOT EXISTS erp_login_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(128)    NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    username        VARCHAR(128),
    login_result    VARCHAR(32)     DEFAULT 'SUCCESS',       -- SUCCESS|FAILURE|LOCKED
    fail_reason     VARCHAR(255),
    ip_address      VARCHAR(64),
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ── 5. 组织架构 ──
CREATE TABLE IF NOT EXISTS erp_org_units (
    id              VARCHAR(64)     PRIMARY KEY,
    tenant_id       VARCHAR(64),
    parent_id       VARCHAR(64),
    code            VARCHAR(128)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    unit_type       VARCHAR(32)     NOT NULL,
    depth           INT             DEFAULT 0,
    path_ids        VARCHAR(1024),
    path_names      VARCHAR(2048),
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ── 6. 动态实体存储（业务数据） ──
CREATE TABLE IF NOT EXISTS erp_entities (
    id              VARCHAR(255)    PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    entity_type     VARCHAR(128)    NOT NULL,
    code            VARCHAR(255),
    name            VARCHAR(512),
    status          VARCHAR(32)     DEFAULT 'ACTIVE',
    data_json       CLOB,
    created_by      VARCHAR(128),
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_erp_entities_type ON erp_entities(tenant_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_erp_entities_code ON erp_entities(tenant_id, entity_type, code);

-- ── 7. 审计日志 ──
CREATE TABLE IF NOT EXISTS erp_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       VARCHAR(64),
    user_id         VARCHAR(128),
    action          VARCHAR(64)     NOT NULL,
    entity_type     VARCHAR(128),
    entity_id       VARCHAR(255),
    details_json    CLOB,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 种子数据
-- ============================================================

-- 3家预置公司（按你的架构每家一个租户）
MERGE INTO erp_tenants (id, name, code, status, settings_json) KEY(id) VALUES
    ('t-arche', '始祖集团', 'ARCHE', 'ACTIVE', '{"timezone":"Asia/Shanghai","currency":"CNY"}'),
    ('t-east', '华东分公司', 'EAST', 'ACTIVE', '{"timezone":"Asia/Shanghai","currency":"CNY"}'),
    ('t-south', '华南分公司', 'SOUTH', 'ACTIVE', '{"timezone":"Asia/Shanghai","currency":"CNY"}');

-- 角色
MERGE INTO erp_roles (id, name, description) KEY(id) VALUES
    ('admin', '超级管理员', '拥有所有权限'),
    ('tenant_admin', '公司管理员', '管理本公司业务'),
    ('finance_mgr', '财务主管', '财务管理'),
    ('warehouse_mgr', '仓库主管', '仓库管理'),
    ('hr_mgr', '人事主管', '人力资源管理');

-- 系统管理员（不归属任何公司，可管理所有租户）
MERGE INTO erp_users (id, tenant_id, username, display_name, password_hash, role_id, is_system_admin) KEY(id) VALUES
    ('u-sysadmin', 'SYSTEM', 'sysadmin', '系统管理员', 'sysadmin123', 'admin', TRUE);

-- 用户（每家公司的管理员账号）
MERGE INTO erp_users (id, tenant_id, username, display_name, password_hash, role_id) KEY(id) VALUES
    -- 始祖集团
    ('u-arche-admin', 't-arche', 'admin', '始祖管理员', 'admin123', 'tenant_admin'),
    ('u-arche-demo', 't-arche', 'demo', '始祖演示', 'demo123', 'finance_mgr'),
    -- 华东分公司
    ('u-east-admin', 't-east', 'admin', '华东管理员', 'admin123', 'tenant_admin'),
    ('u-east-demo', 't-east', 'demo', '华东演示', 'demo123', 'warehouse_mgr'),
    -- 华南分公司
    ('u-south-admin', 't-south', 'admin', '华南管理员', 'admin123', 'tenant_admin'),
    ('u-south-demo', 't-south', 'demo', '华南演示', 'demo123', 'hr_mgr');

-- 组织架构
MERGE INTO erp_org_units (id, tenant_id, parent_id, code, name, unit_type, depth, path_ids, path_names) KEY(id) VALUES
    ('org-arche', 't-arche', NULL, 'HQ', '始祖集团总部', 'GROUP', 0, '/org-arche', '/始祖集团总部'),
    ('org-arche-fin', 't-arche', 'org-arche', 'FIN', '财务部', 'DEPARTMENT', 1, '/org-arche/org-arche-fin', '/始祖集团总部/财务部'),
    ('org-arche-wh', 't-arche', 'org-arche', 'WH', '仓储部', 'DEPARTMENT', 1, '/org-arche/org-arche-wh', '/始祖集团总部/仓储部'),
    ('org-east', 't-east', NULL, 'HQ', '华东分公司', 'GROUP', 0, '/org-east', '/华东分公司'),
    ('org-east-wh', 't-east', 'org-east', 'WH', '仓储部', 'DEPARTMENT', 1, '/org-east/org-east-wh', '/华东分公司/仓储部'),
    ('org-south', 't-south', NULL, 'HQ', '华南分公司', 'GROUP', 0, '/org-south', '/华南分公司');

-- 演示业务数据（按租户隔离）
MERGE INTO erp_entities (id, tenant_id, entity_type, code, name, status, data_json) KEY(id) VALUES
    -- 始祖集团 - 会计科目
    ('e-acc-001', 't-arche', 'AccountSubject', '1001', '库存现金', 'ACTIVE', '{"accountCode":"1001","accountName":"库存现金","accountType":"资产"}'),
    ('e-acc-002', 't-arche', 'AccountSubject', '1002', '银行存款', 'ACTIVE', '{"accountCode":"1002","accountName":"银行存款","accountType":"资产"}'),
    ('e-acc-003', 't-arche', 'AccountSubject', '1122', '应收账款', 'ACTIVE', '{"accountCode":"1122","accountName":"应收账款","accountType":"资产"}'),
    ('e-acc-004', 't-arche', 'AccountSubject', '2001', '应付账款', 'ACTIVE', '{"accountCode":"2001","accountName":"应付账款","accountType":"负债"}'),
    -- 始祖集团 - 产品
    ('e-prod-001', 't-arche', 'Product', 'P-1001', '碳钢圆管 φ20', 'ACTIVE', '{"productCode":"P-1001","productName":"碳钢圆管 φ20","category":"原材料","stockQuantity":"500","price":"45.00"}'),
    ('e-prod-002', 't-arche', 'Product', 'P-1002', '不锈钢板 304 2mm', 'ACTIVE', '{"productCode":"P-1002","productName":"不锈钢板 304 2mm","category":"原材料","stockQuantity":"300","price":"320.00"}'),
    -- 始祖集团 - 员工
    ('e-emp-001', 't-arche', 'Employee', 'EMP-001', '张三', 'ACTIVE', '{"employeeCode":"EMP-001","employeeName":"张三","department":"财务部","position":"财务主管"}'),
    ('e-emp-002', 't-arche', 'Employee', 'EMP-002', '李四', 'ACTIVE', '{"employeeCode":"EMP-002","employeeName":"李四","department":"仓储部","position":"仓库主管"}'),
    -- 始祖集团 - 发票
    ('e-inv-001', 't-arche', 'Invoice', 'INV-2024-001', '客户A-6月销售', 'PENDING', '{"invoiceNo":"INV-2024-001","customerName":"客户A","amount":"10000.00","totalAmount":"11300.00"}'),

    -- 华东分公司 - 产品（独立数据）
    ('e-prod-003', 't-east', 'Product', 'P-2001', '电机 M3-750W', 'ACTIVE', '{"productCode":"P-2001","productName":"电机 M3-750W","category":"半成品","stockQuantity":"50","price":"2800.00"}'),
    ('e-prod-004', 't-east', 'Product', 'P-2002', 'PLC控制器 FX3U', 'ACTIVE', '{"productCode":"P-2002","productName":"PLC控制器 FX3U","category":"半成品","stockQuantity":"35","price":"4500.00"}'),
    -- 华东分公司 - 发票
    ('e-inv-002', 't-east', 'Invoice', 'INV-2024-002', '客户B-6月销售', 'APPROVED', '{"invoiceNo":"INV-2024-002","customerName":"客户B","amount":"25000.00","totalAmount":"28250.00"}'),

    -- 华南分公司 - 产品（独立数据）
    ('e-prod-005', 't-south', 'Product', 'P-3001', '减速机 RV40', 'ACTIVE', '{"productCode":"P-3001","productName":"减速机 RV40","category":"外购件","stockQuantity":"80","price":"1200.00"}'),
    -- 华南分公司 - 员工
    ('e-emp-003', 't-south', 'Employee', 'EMP-003', '赵六', 'ACTIVE', '{"employeeCode":"EMP-003","employeeName":"赵六","department":"销售部","position":"销售经理"}');
