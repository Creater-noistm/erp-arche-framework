package com.erp.modules.inventory;

import com.erp.kernel.MicroKernel;
import com.erp.model.DynamicEntity;
import com.erp.router.DataRouter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 库存移动实体的 CRUD 处理器。
 */
public class MovementCrudHandler implements DataRouter.CrudHandler {

    private static final Logger log = LoggerFactory.getLogger(MovementCrudHandler.class);
    private final MicroKernel kernel;

    public MovementCrudHandler(MicroKernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public Object create(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        DynamicEntity entity = req.getPayload();
        log.info("Recording inventory movement: {} of {}",
            entity.get("quantity"), entity.get("productCode"));

        // 发布库存移动事件 — 财务模块会监听此事件
        kernel.getEventBus().publish(new com.erp.event.ErpEvent(
            "data.entity.created",
            Map.of(
                "entityType", "InventoryMovement",
                "movementType", entity.get("movementType"),
                "productCode", entity.get("productCode"),
                "quantity", entity.get("quantity"),
                "totalAmount", entity.get("totalAmount")
            ),
            com.erp.event.ErpEvent.Priority.HIGH
        ));

        return DataRouter.RoutingResponse.success(Map.of("movementId", "MOV-" + System.currentTimeMillis()));
    }

    @Override
    public Object query(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        return DataRouter.RoutingResponse.success(List.of(
            Map.of("movementId", "MOV-001", "type", "采购入库", "product", "碳钢圆管", "qty", "100", "date", "2024-06-15"),
            Map.of("movementId", "MOV-002", "type", "销售出库", "product", "不锈钢板", "qty", "50", "date", "2024-06-16")
        ));
    }
}
