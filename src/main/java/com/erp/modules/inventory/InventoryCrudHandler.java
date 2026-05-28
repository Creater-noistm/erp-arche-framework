package com.erp.modules.inventory;

import com.erp.model.DynamicEntity;
import com.erp.router.DataRouter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 产品实体的 CRUD 处理器。
 */
public class InventoryCrudHandler implements DataRouter.CrudHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryCrudHandler.class);

    @Override
    public Object create(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        DynamicEntity entity = req.getPayload();
        log.info("Creating product: {}", entity.getName());
        ctx.getKernel().getEventBus().publish(new com.erp.event.ErpEvent(
            "data.entity.created",
            Map.of("entityType", "Product", "entityId", entity.getId(), "name", entity.getName()),
            com.erp.event.ErpEvent.Priority.NORMAL
        ));
        return DataRouter.RoutingResponse.success(Map.of("productId", entity.getId()));
    }

    @Override
    public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        String productId = req.getPayload();
        log.info("Reading product: {}", productId);
        return DataRouter.RoutingResponse.success(Map.of(
            "id", productId,
            "productCode", "P-10001",
            "productName", "示例产品",
            "category", "原材料",
            "stockQuantity", "150.00"
        ));
    }

    @Override
    public Object update(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        DynamicEntity entity = req.getPayload();
        log.info("Updating product: {}", entity.getId());
        ctx.getKernel().getEventBus().publish(new com.erp.event.ErpEvent(
            "data.entity.updated",
            Map.of("entityType", "Product", "entityId", entity.getId()),
            com.erp.event.ErpEvent.Priority.NORMAL
        ));
        return DataRouter.RoutingResponse.success(true);
    }

    @Override
    public Object delete(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        String productId = req.getPayload();
        log.info("Deleting product: {}", productId);
        return DataRouter.RoutingResponse.success(true);
    }

    @Override
    public Object query(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
        log.info("Querying products");
        return DataRouter.RoutingResponse.success(List.of(
            Map.of("productCode", "P-10001", "productName", "碳钢圆管", "category", "原材料", "stock", "500"),
            Map.of("productCode", "P-10002", "productName", "不锈钢板", "category", "原材料", "stock", "300"),
            Map.of("productCode", "P-10003", "productName", "电机M3", "category", "半成品", "stock", "50")
        ));
    }
}
