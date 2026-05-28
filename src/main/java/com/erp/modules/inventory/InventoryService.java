package com.erp.modules.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 库存模块公开服务。
 */
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** 查询库存 */
    public Map<String, Object> getStock(String productCode, String warehouse) {
        log.info("Querying stock: {} @ {}", productCode, warehouse);
        return Map.of(
            "productCode", productCode,
            "warehouse", warehouse,
            "quantity", new BigDecimal("150.00"),
            "reserved", new BigDecimal("20.00"),
            "available", new BigDecimal("130.00")
        );
    }

    /** 预留库存 */
    public boolean reserveStock(String productCode, String warehouse, BigDecimal quantity) {
        log.info("Reserving {} of {} @ {}", quantity, productCode, warehouse);
        return true;
    }

    /** 释放库存 */
    public boolean releaseStock(String productCode, String warehouse, BigDecimal quantity) {
        log.info("Releasing {} of {} @ {}", quantity, productCode, warehouse);
        return true;
    }

    /** 检查库存是否充足 */
    public boolean isStockAvailable(String productCode, String warehouse, BigDecimal quantity) {
        return true;
    }
}
