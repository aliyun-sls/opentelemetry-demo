package com.otel.demo.inventory.service;

import com.otel.demo.inventory.entity.InventoryItem;
import com.otel.demo.inventory.repository.InventoryRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
public class InventoryMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryMonitoringService.class);
    private final Random random = new Random();

    @Autowired
    private InventoryRepository inventoryRepository;

    @Value("${inventory.monitoring.low-stock-threshold:10}")
    private Integer lowStockThreshold;

    @Value("${inventory.monitoring.restock-quantity-min:20}")
    private Integer restockQuantityMin;

    @Value("${inventory.monitoring.restock-quantity-max:50}")
    private Integer restockQuantityMax;

    @Value("${inventory.monitoring.enabled:true}")
    private Boolean monitoringEnabled;

    /**
     * 定时检查库存并自动补货
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300,000毫秒
    @WithSpan("checkAndRestockInventory")
    @Transactional
    public void checkAndRestockInventory() {
        if (!monitoringEnabled) {
            logger.debug("库存监控已禁用，跳过检查");
            return;
        }

        logger.info("开始执行库存检查和自动补货任务");
        
        Span.current().setAttribute("inventory.monitoring.threshold", lowStockThreshold);
        
        try {
            // 查找库存不足的商品
            List<InventoryItem> lowStockItems = inventoryRepository.findLowStockItems(lowStockThreshold);
            
            if (lowStockItems.isEmpty()) {
                logger.info("所有商品库存充足，无需补货");
                Span.current().setAttribute("inventory.monitoring.low_stock_count", 0);
                return;
            }

            logger.info("发现 {} 个商品库存不足，开始自动补货", lowStockItems.size());
            Span.current().setAttribute("inventory.monitoring.low_stock_count", lowStockItems.size());

            int restockedCount = 0;
            for (InventoryItem item : lowStockItems) {
                try {
                    restockItem(item);
                    restockedCount++;
                } catch (Exception e) {
                    logger.error("补货失败: 商品ID={}, 错误={}", item.getProductId(), e.getMessage());
                }
            }

            logger.info("自动补货任务完成，成功补货 {} 个商品", restockedCount);
            Span.current().setAttribute("inventory.monitoring.restocked_count", restockedCount);

        } catch (Exception e) {
            logger.error("库存检查和补货任务执行失败", e);
            Span.current().setAttribute("inventory.monitoring.error", e.getMessage());
        }
    }

    /**
     * 为单个商品补货
     */
    @WithSpan("restockItem")
    private void restockItem(InventoryItem item) {
        // 计算补货数量（随机数量，模拟真实场景）
        int restockQuantity = random.nextInt(restockQuantityMax - restockQuantityMin + 1) + restockQuantityMin;
        
        logger.info("正在为商品 {} 补货，当前库存: {}，补货数量: {}", 
                   item.getProductId(), item.getAvailableQuantity(), restockQuantity);

        Span.current().setAttribute("inventory.product_id", item.getProductId());
        Span.current().setAttribute("inventory.current_quantity", item.getAvailableQuantity());
        Span.current().setAttribute("inventory.restock_quantity", restockQuantity);

        // 更新库存
        long timestamp = System.currentTimeMillis();
        int updatedRows = inventoryRepository.updateInventoryQuantity(
                item.getProductId(), restockQuantity, timestamp);

        if (updatedRows > 0) {
            logger.info("商品 {} 补货成功，补货数量: {}", item.getProductId(), restockQuantity);
            
            // 记录补货操作（这里可以扩展为操作日志表）
            logRestockOperation(item.getProductId(), restockQuantity, "自动补货");
        } else {
            logger.warn("商品 {} 补货失败", item.getProductId());
        }
    }

    /**
     * 记录补货操作日志
     */
    private void logRestockOperation(String productId, int quantity, String reason) {
        // 这里可以扩展为写入操作日志表
        logger.info("补货操作记录: 商品ID={}, 数量={}, 原因={}, 时间={}", 
                   productId, quantity, reason, System.currentTimeMillis());
    }

    /**
     * 手动触发库存检查（用于测试或紧急情况）
     */
    @WithSpan("manualInventoryCheck")
    public void triggerManualInventoryCheck() {
        logger.info("手动触发库存检查和补货任务");
        checkAndRestockInventory();
    }

    /**
     * 获取库存监控统计信息
     */
    @WithSpan("getInventoryMonitoringStats")
    public InventoryMonitoringStats getMonitoringStats() {
        List<InventoryItem> allItems = inventoryRepository.findAll();
        List<InventoryItem> lowStockItems = inventoryRepository.findLowStockItems(lowStockThreshold);
        
        long totalItems = allItems.size();
        long lowStockCount = lowStockItems.size();
        long totalAvailableQuantity = allItems.stream()
                .mapToLong(InventoryItem::getAvailableQuantity)
                .sum();
        long totalReservedQuantity = allItems.stream()
                .mapToLong(InventoryItem::getReservedQuantity)
                .sum();

        return new InventoryMonitoringStats(
                totalItems,
                lowStockCount,
                totalAvailableQuantity,
                totalReservedQuantity,
                lowStockThreshold,
                monitoringEnabled
        );
    }

    /**
     * 库存监控统计信息DTO
     */
    public static class InventoryMonitoringStats {
        private final long totalItems;
        private final long lowStockCount;
        private final long totalAvailableQuantity;
        private final long totalReservedQuantity;
        private final int lowStockThreshold;
        private final boolean monitoringEnabled;

        public InventoryMonitoringStats(long totalItems, long lowStockCount, 
                                      long totalAvailableQuantity, long totalReservedQuantity,
                                      int lowStockThreshold, boolean monitoringEnabled) {
            this.totalItems = totalItems;
            this.lowStockCount = lowStockCount;
            this.totalAvailableQuantity = totalAvailableQuantity;
            this.totalReservedQuantity = totalReservedQuantity;
            this.lowStockThreshold = lowStockThreshold;
            this.monitoringEnabled = monitoringEnabled;
        }

        // Getters
        public long getTotalItems() { return totalItems; }
        public long getLowStockCount() { return lowStockCount; }
        public long getTotalAvailableQuantity() { return totalAvailableQuantity; }
        public long getTotalReservedQuantity() { return totalReservedQuantity; }
        public int getLowStockThreshold() { return lowStockThreshold; }
        public boolean isMonitoringEnabled() { return monitoringEnabled; }
    }
} 