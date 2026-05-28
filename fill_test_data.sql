-- ============================================================
-- 填充所有表到 100 条测试数据
-- 依赖 _nums 表（1..100）
-- ============================================================

-- 1. erp_tenants — 公司（已有 4 条，补 96）
INSERT IGNORE INTO erp_tenants (id, name, code, status, settings_json)
SELECT
    CONCAT('t-test-', LPAD(n, 3, '0')),
    CONCAT('测试公司', n),
    CONCAT('TEST', LPAD(n, 3, '0')),
    ELT(1 + FLOOR(RAND()*3), 'ACTIVE', 'ACTIVE', 'TRIAL'),
    '{"timezone":"Asia/Shanghai","currency":"CNY"}'
FROM _nums WHERE n <= 96;

-- 2. erp_roles — 角色（已有 5 条，补 95）
INSERT IGNORE INTO erp_roles (id, name, description)
SELECT
    CONCAT('test-role-', LPAD(n, 3, '0')),
    CONCAT('测试角色', n),
    CONCAT('测试用角色 #', n)
FROM _nums WHERE n <= 95;

-- 3. erp_org_units — 组织架构（已有 2，补 98）
INSERT IGNORE INTO erp_org_units (id, tenant_id, parent_id, code, name, unit_type, depth, path_ids, path_names)
SELECT
    CONCAT('org-test-', LPAD(n, 3, '0')),
    ELT(1+FLOOR(RAND()*10),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004','t-test-005','t-test-006'),
    NULL,
    CONCAT('DEPT', LPAD(n, 3, '0')),
    CONCAT('测试部门', n),
    ELT(1+FLOOR(RAND()*3),'GROUP','DEPARTMENT','TEAM'),
    0,
    CONCAT('/org-test-',LPAD(n,3,'0')),
    CONCAT('/测试部门',n)
FROM _nums WHERE n <= 98;

-- 4. erp_users — 用户（已有 26，补 74）
INSERT IGNORE INTO erp_users (id, tenant_id, username, display_name, password_hash, role_id, is_system_admin, status)
SELECT
    CONCAT('u-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*10),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004','t-test-005','t-test-006'),
    CONCAT('testuser',LPAD(n,3,'0')),
    CONCAT('测试用户',n),
    'test123',
    ELT(1+FLOOR(RAND()*10),'tenant_admin','finance_mgr','warehouse_mgr','hr_mgr','purchase_mgr','sales_mgr','tech_staff','test-role-001','test-role-002','test-role-003'),
    false,'ACTIVE'
FROM _nums WHERE n <= 74;

-- 5. erp_entities — 动态实体（0→100）
INSERT IGNORE INTO erp_entities (id, tenant_id, entity_type, code, name, status, data_json)
SELECT
    CONCAT('e-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*10),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004','t-test-005','t-test-006'),
    ELT(1+FLOOR(RAND()*5),'Product','AccountSubject','Employee','Invoice','Project'),
    CONCAT('ETEST-',LPAD(n,3,'0')),
    CONCAT('测试实体',n),'ACTIVE',
    CONCAT('{"type":"test","id":',n,'}')
FROM _nums;

-- 6. erp_login_log — 登录日志（已有 22，补 78）
INSERT IGNORE INTO erp_login_log (user_id, tenant_id, username, login_result, ip_address)
SELECT
    ELT(1+FLOOR(RAND()*10),'u-sysadmin','u-arche-admin','u-east-admin','u-south-admin','u-demo-admin','u-test-001','u-test-010','u-test-020','u-test-030','u-test-040'),
    ELT(1+FLOOR(RAND()*10),'SYSTEM','t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004','t-test-005'),
    CONCAT('user',LPAD(n,2,'0')),
    ELT(1+FLOOR(RAND()*3),'SUCCESS','SUCCESS','FAILURE'),
    CONCAT('192.168.',1+FLOOR(RAND()*254),'.',1+FLOOR(RAND()*254))
FROM _nums WHERE n <= 78;

-- 7. erp_audit_log — 审计日志（0→100）
INSERT IGNORE INTO erp_audit_log (tenant_id, user_id, action, entity_type, entity_id, details_json)
SELECT
    ELT(1+FLOOR(RAND()*10),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004','t-test-005','t-test-006'),
    ELT(1+FLOOR(RAND()*10),'u-sysadmin','u-arche-admin','u-east-admin','u-south-admin','u-demo-admin','u-test-001','u-test-005','u-test-010','u-test-015','u-test-020'),
    ELT(1+FLOOR(RAND()*6),'CREATE','UPDATE','DELETE','LOGIN','APPROVE','EXPORT'),
    ELT(1+FLOOR(RAND()*5),'Product','Invoice','Employee','Voucher','User'),
    CONCAT('entity-',n),
    CONCAT('{"action":"test","seq":',n,'}')
FROM _nums;

-- 8. hr_employees — 员工（已有 24，补 76）
INSERT IGNORE INTO hr_employees (id, tenant_id, emp_code, emp_name, gender, department, position, hire_date, status)
SELECT
    CONCAT('emp-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    CONCAT('E',LPAD(n,4,'0')),
    ELT(1+FLOOR(RAND()*6),'张伟','王芳','李强','刘静','陈明','赵丽'),
    ELT(1+FLOOR(RAND()*2),'男','女'),
    ELT(1+FLOOR(RAND()*6),'财务部','人事部','技术部','销售部','仓储部','采购部'),
    ELT(1+FLOOR(RAND()*6),'主管','专员','经理','工程师','助理','总监'),
    DATE_ADD('2023-01-01',INTERVAL FLOOR(RAND()*800) DAY),
    ELT(1+FLOOR(RAND()*3),'ACTIVE','ACTIVE','RESIGNED')
FROM _nums WHERE n <= 76;

-- 9. hr_attendance — 考勤（已有 11，补 89）
INSERT IGNORE INTO hr_attendance (tenant_id, emp_id, att_date, check_in, check_out, status)
SELECT
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    ELT(1+FLOOR(RAND()*12),'emp-test-001','emp-test-010','emp-test-020','emp-test-030','emp-test-040','emp-test-050','emp-test-060','emp-test-070','u-arche-admin','u-east-admin','emp-test-005','emp-test-015'),
    DATE_ADD('2025-01-01',INTERVAL FLOOR(RAND()*365) DAY),
    DATE_ADD('08:00:00',INTERVAL FLOOR(RAND()*60) MINUTE),
    DATE_ADD('17:00:00',INTERVAL FLOOR(RAND()*60) MINUTE),
    ELT(1+FLOOR(RAND()*5),'NORMAL','NORMAL','NORMAL','LATE','ABSENT')
FROM _nums WHERE n <= 89;

-- 10. fin_account_subjects — 会计科目（已有 19，补 81）
INSERT IGNORE INTO fin_account_subjects (id, tenant_id, subject_code, subject_name, subject_type, balance_direction, is_active)
SELECT
    CONCAT('subj-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    CONCAT('5',LPAD(100+n,3,'0')),
    CONCAT('测试科目',n),
    ELT(1+FLOOR(RAND()*4),'ASSET','LIABILITY','PROFIT','COST'),
    ELT(1+FLOOR(RAND()*2),'DEBIT','CREDIT'),
    true
FROM _nums WHERE n <= 81;

-- 11. fin_vouchers — 凭证（已有 5，补 95）
INSERT IGNORE INTO fin_vouchers (id, tenant_id, voucher_no, voucher_date, description, status)
SELECT
    CONCAT('v-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    CONCAT('V-test-',LPAD(n,4,'0')),
    DATE_ADD('2025-01-01',INTERVAL FLOOR(RAND()*365) DAY),
    CONCAT('测试凭证 #',n),
    ELT(1+FLOOR(RAND()*3),'POSTED','DRAFT','POSTED')
FROM _nums WHERE n <= 95;

-- 12. fin_voucher_items — 凭证分录（已有 11，补 89）
INSERT IGNORE INTO fin_voucher_items (voucher_id, subject_id, summary, debit_amount, credit_amount)
SELECT
    ELT(1+FLOOR(RAND()*30),'v-test-001','v-test-002','v-test-005','v-test-010','v-test-015','v-test-020','v-test-025','v-test-030','v-test-035','v-test-040','v-test-045','v-test-050','v-test-055','v-test-060','v-test-065','v-test-070','v-test-075','v-test-080','v-test-085','v-test-090','v-test-095','v-1','v-2','v-3','v-4','v-5','v-6','v-7','v-8','v-9'),
    ELT(1+FLOOR(RAND()*20),'subj-test-001','subj-test-010','subj-test-020','subj-test-030','subj-test-040','subj-test-050','subj-test-060','subj-test-070','subj-test-080','acc-1001','acc-1002','acc-1122','acc-2001','acc-5001','acc-6001','acc-100101','acc-100201','acc-112201','acc-200101','acc-500101'),
    CONCAT('测试摘要 #',n),
    ROUND(RAND()*100000,2),
    ROUND(RAND()*100000,2)
FROM _nums WHERE n <= 89;

-- 13. fin_invoices — 发票（已有 3，补 97）
INSERT IGNORE INTO fin_invoices (id, tenant_id, invoice_no, invoice_type, customer_name, amount, tax_amount, total_amount, invoice_date, due_date, status)
SELECT
    CONCAT('inv-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    CONCAT('INV-TEST-',LPAD(n,4,'0')),
    ELT(1+FLOOR(RAND()*2),'SALES','PURCHASE'),
    CONCAT('测试客户',n),
    ROUND(RAND()*50000+1000,2),
    ROUND(RAND()*50000*0.13,2),
    ROUND(RAND()*50000*1.13+1000,2),
    DATE_ADD('2025-01-01',INTERVAL FLOOR(RAND()*365) DAY),
    DATE_ADD('2025-02-01',INTERVAL FLOOR(RAND()*365) DAY),
    ELT(1+FLOOR(RAND()*4),'PENDING','APPROVED','PAID','CANCELLED')
FROM _nums WHERE n <= 97;

-- 14. inv_products — 产品（已有 9，补 91）
INSERT IGNORE INTO inv_products (id, tenant_id, product_code, product_name, category, unit, unit_price, min_stock, is_active)
SELECT
    CONCAT('prod-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    CONCAT('P-TEST-',LPAD(n,4,'0')),
    CONCAT('测试产品',n),
    ELT(1+FLOOR(RAND()*5),'原材料','半成品','成品','外购件','包装材料'),
    ELT(1+FLOOR(RAND()*5),'个','件','kg','m','箱'),
    ROUND(RAND()*5000+10,2),
    FLOOR(RAND()*50),
    true
FROM _nums WHERE n <= 91;

-- 15. inv_stock_in — 入库单（已有 9，补 91）
INSERT IGNORE INTO inv_stock_in (tenant_id, product_id, quantity, unit_price, supplier, in_date, operator)
SELECT
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    ELT(1+FLOOR(RAND()*10),'prod-test-001','prod-test-010','prod-test-020','prod-test-030','prod-test-040','prod-test-050','prod-test-060','prod-test-070','prod-test-080','prod-test-090'),
    FLOOR(RAND()*500+10),
    ROUND(RAND()*1000+5,2),
    CONCAT('测试供应商',n),
    DATE_ADD('2025-01-01',INTERVAL FLOOR(RAND()*365) DAY),
    CONCAT('操作员',LPAD(n,2,'0'))
FROM _nums WHERE n <= 91;

-- 16. inv_stock_out — 出库单（已有 3，补 97）
INSERT IGNORE INTO inv_stock_out (tenant_id, product_id, quantity, unit_price, customer, out_date, operator)
SELECT
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    ELT(1+FLOOR(RAND()*10),'prod-test-001','prod-test-005','prod-test-010','prod-test-015','prod-test-020','prod-test-025','prod-test-030','prod-test-035','prod-test-040','prod-test-045'),
    FLOOR(RAND()*200+1),
    ROUND(RAND()*1000+5,2),
    CONCAT('测试客户',n),
    DATE_ADD('2025-01-01',INTERVAL FLOOR(RAND()*365) DAY),
    CONCAT('操作员',LPAD(n,2,'0'))
FROM _nums WHERE n <= 97;

-- 17. biz_sales_orders — 销售订单（已有 1，补 99）
INSERT IGNORE INTO biz_sales_orders (id, tenant_id, order_no, customer_name, order_date, total_amount, status, created_by)
SELECT
    CONCAT('so-test-',LPAD(n,3,'0')),
    ELT(1+FLOOR(RAND()*8),'t-arche','t-east','t-south','t-demo','t-test-001','t-test-002','t-test-003','t-test-004'),
    CONCAT('SO-TEST-',LPAD(n,4,'0')),
    CONCAT('测试客户',n),
    DATE_ADD('2025-01-01',INTERVAL FLOOR(RAND()*365) DAY),
    ROUND(RAND()*100000,2),
    ELT(1+FLOOR(RAND()*4),'DRAFT','CONFIRMED','SHIPPED','PAID'),
    CONCAT('testuser',LPAD(1+FLOOR(RAND()*30),3,'0'))
FROM _nums WHERE n <= 99;

-- 18. biz_sales_order_items — 销售订单明细（已有 2，补 98）
INSERT IGNORE INTO biz_sales_order_items (order_id, product_id, quantity, unit_price, line_total)
SELECT
    ELT(1+FLOOR(RAND()*20),'so-test-001','so-test-005','so-test-010','so-test-015','so-test-020','so-test-025','so-test-030','so-test-035','so-test-040','so-test-045','so-test-050','so-test-055','so-test-060','so-test-065','so-test-070','so-test-075','so-test-080','so-test-085','so-test-090','so-test-095'),
    ELT(1+FLOOR(RAND()*10),'prod-test-001','prod-test-010','prod-test-020','prod-test-030','prod-test-040','prod-test-050','prod-test-060','prod-test-070','prod-test-080','prod-test-090'),
    FLOOR(RAND()*50+1),
    ROUND(RAND()*5000+10,2),
    ROUND(RAND()*50000+100,2)
FROM _nums WHERE n <= 98;
