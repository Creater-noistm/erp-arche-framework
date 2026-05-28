package com.erp.modules.hr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HR模块公开服务。
 */
public class HRService {

    private static final Logger log = LoggerFactory.getLogger(HRService.class);

    /** 查询员工信息 */
    public Map<String, Object> getEmployee(String employeeNo) {
        log.info("Querying employee: {}", employeeNo);
        return Map.of(
            "employeeNo", employeeNo,
            "fullName", "张三",
            "department", "信息技术部",
            "position", "高级工程师",
            "hireDate", "2020-03-15",
            "status", "active"
        );
    }

    /** 计算员工年限 */
    public int calculateTenureYears(String employeeNo) {
        return 3;
    }

    /** 检查是否在岗 */
    public boolean isActive(String employeeNo) {
        return true;
    }
}
