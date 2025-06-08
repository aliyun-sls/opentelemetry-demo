package com.otel.demo.inventory.service.impl;

import com.otel.demo.inventory.dto.CartItemDto;
import com.otel.demo.inventory.dto.InventoryItemDto;
import com.otel.demo.inventory.entity.InventoryItem;
import com.otel.demo.inventory.repository.InventoryRepository;
import com.otel.demo.inventory.service.ChaosEngineeringService;
import com.otel.demo.inventory.service.InventoryService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceImpl.class);

    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private ChaosEngineeringService chaosEngineeringService;

    @Override
    @WithSpan("getInventory")
    public InventoryItemDto getInventory(String productId) {
        logger.info("获取商品库存信息: {}", productId);
        
        // Flagd故障注入
        chaosEngineeringService.injectChaos("getInventory");
        
        Span.current().setAttribute("inventory.product_id", productId);
        
        // 如果启用了慢SQL注入，执行复杂的分析查询
        if (chaosEngineeringService.shouldExecuteSlowQuery()) {
            logger.warn("执行慢SQL查询 - 库存分析: {}", productId);
            long startTime = System.currentTimeMillis();
            
            try {
                // 执行复杂的库存分析查询
                List<Object[]> analysisResults = inventoryRepository.executeSlowInventoryAnalysis(productId);
                long queryTime = System.currentTimeMillis() - startTime;
                
                Span.current().setAttribute("chaos.slow_query_executed", true);
                Span.current().setAttribute("chaos.slow_query_duration_ms", queryTime);
                Span.current().setAttribute("chaos.slow_query_results_count", analysisResults.size());
                
                logger.warn("慢SQL查询完成 - 耗时: {}ms, 结果数: {}", queryTime, analysisResults.size());
                
            } catch (Exception e) {
                logger.error("慢SQL查询执行失败: {}", e.getMessage(), e);
                Span.current().setAttribute("chaos.slow_query_error", e.getMessage());
            }
        }
        
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在: " + productId));
        
        return convertToDto(item);
    }

    @Override
    @WithSpan("checkAvailability")
    public Map<String, Boolean> checkAvailability(List<CartItemDto> items) {
        logger.info("检查商品可用性，商品数量: {}", items.size());
        
        // Flagd故障注入
        chaosEngineeringService.injectChaos("checkAvailability");
        
        Span.current().setAttribute("inventory.items_count", items.size());
        
        Map<String, Boolean> availability = new HashMap<>();
        
        for (CartItemDto item : items) {
            Boolean hasStock = inventoryRepository.hasAvailableStock(item.getProductId(), item.getQuantity());
            availability.put(item.getProductId(), hasStock != null && hasStock);
        }
        
        long availableCount = availability.values().stream().mapToLong(available -> available ? 1 : 0).sum();
        Span.current().setAttribute("inventory.available_items", availableCount);
        
        return availability;
    }

    @Override
    @WithSpan("updateInventory")
    public InventoryItemDto updateInventory(String productId, Integer quantityChange, String operationType, String reason) {
        logger.info("更新库存: 商品ID={}, 数量变化={}, 操作类型={}", productId, quantityChange, operationType);
        
        // Flagd故障注入
        chaosEngineeringService.injectChaos("updateInventory");
        
        Span.current().setAttribute("inventory.product_id", productId);
        Span.current().setAttribute("inventory.quantity_change", quantityChange);
        Span.current().setAttribute("inventory.operation_type", operationType);
        
        long timestamp = System.currentTimeMillis();
        int updatedRows = inventoryRepository.updateInventoryQuantity(productId, quantityChange, timestamp);
        
        if (updatedRows == 0) {
            throw new RuntimeException("商品不存在或更新失败: " + productId);
        }
        
        // 记录操作日志（这里可以扩展为单独的操作日志服务）
        logger.info("库存更新成功: 商品ID={}, 原因={}", productId, reason);
        
        return getInventory(productId);
    }

    @Override
    @WithSpan("reserveInventory")
    public boolean reserveInventory(List<CartItemDto> items, String reservationId) {
        logger.info("预留库存: 预留ID={}, 商品数量={}", reservationId, items.size());
        
        Span.current().setAttribute("inventory.reservation_id", reservationId);
        Span.current().setAttribute("inventory.items_count", items.size());
        
        long timestamp = System.currentTimeMillis();
        
        // 检查所有商品是否有足够库存
        for (CartItemDto item : items) {
            Boolean hasStock = inventoryRepository.hasAvailableStock(item.getProductId(), item.getQuantity());
            if (hasStock == null || !hasStock) {
                logger.warn("库存不足: 商品ID={}, 需要数量={}", item.getProductId(), item.getQuantity());
                return false;
            }
        }
        
        // 预留库存
        for (CartItemDto item : items) {
            int reservedRows = inventoryRepository.reserveInventory(item.getProductId(), item.getQuantity(), timestamp);
            if (reservedRows == 0) {
                logger.error("预留库存失败: 商品ID={}", item.getProductId());
                // 这里应该回滚之前的预留操作，但为了简化示例，暂时不实现
                return false;
            }
        }
        
        logger.info("库存预留成功: 预留ID={}", reservationId);
        return true;
    }

    @Override
    @WithSpan("releaseInventory")
    public boolean releaseInventory(String reservationId) {
        logger.info("释放库存: 预留ID={}", reservationId);
        
        Span.current().setAttribute("inventory.reservation_id", reservationId);
        
        // 这里需要从预留记录表中查询预留信息，然后释放库存
        // 为了简化示例，这里只是记录日志
        logger.info("库存释放成功: 预留ID={}", reservationId);
        return true;
    }

    @Override
    @WithSpan("getAllInventory")
    public List<InventoryItemDto> getAllInventory() {
        logger.info("获取所有库存信息");
        
        // 如果启用了慢SQL注入，执行复杂的趋势分析查询
        if (chaosEngineeringService.shouldExecuteSlowQuery()) {
            logger.warn("执行慢SQL查询 - 库存趋势分析");
            long startTime = System.currentTimeMillis();
            
            try {
                // 对前几个商品执行复杂的趋势分析
                List<InventoryItem> sampleItems = inventoryRepository.findAll().stream().limit(3).toList();
                for (InventoryItem item : sampleItems) {
                    List<Object[]> trendResults = inventoryRepository.executeSlowInventoryTrendAnalysis(item.getProductId());
                    logger.debug("商品 {} 的趋势分析结果数: {}", item.getProductId(), trendResults.size());
                }
                
                long queryTime = System.currentTimeMillis() - startTime;
                Span.current().setAttribute("chaos.slow_query_executed", true);
                Span.current().setAttribute("chaos.slow_query_duration_ms", queryTime);
                
                logger.warn("慢SQL趋势分析完成 - 耗时: {}ms", queryTime);
                
            } catch (Exception e) {
                logger.error("慢SQL趋势分析执行失败: {}", e.getMessage(), e);
                Span.current().setAttribute("chaos.slow_query_error", e.getMessage());
            }
        }
        
        List<InventoryItem> items = inventoryRepository.findAll();
        
        Span.current().setAttribute("inventory.total_items", items.size());
        
        return items.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @WithSpan("getInventoryByWarehouse")
    public List<InventoryItemDto> getInventoryByWarehouse(String warehouseLocation) {
        logger.info("根据仓库位置获取库存: {}", warehouseLocation);
        
        Span.current().setAttribute("inventory.warehouse_location", warehouseLocation);
        
        List<InventoryItem> items = inventoryRepository.findByWarehouseLocation(warehouseLocation);
        
        return items.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @WithSpan("getLowStockItems")
    public List<InventoryItemDto> getLowStockItems(Integer threshold) {
        logger.info("获取库存不足的商品，阈值: {}", threshold);
        
        Span.current().setAttribute("inventory.threshold", threshold);
        
        // 如果启用了慢SQL注入，执行复杂的深度分析查询
        if (chaosEngineeringService.shouldExecuteSlowQuery()) {
            logger.warn("执行慢SQL查询 - 库存深度分析");
            long startTime = System.currentTimeMillis();
            
            try {
                // 对低库存商品执行深度分析
                List<InventoryItem> lowStockSample = inventoryRepository.findLowStockItems(threshold).stream().limit(2).toList();
                for (InventoryItem item : lowStockSample) {
                    List<Object[]> deepAnalysisResults = inventoryRepository.executeSlowInventoryDeepAnalysis(item.getProductId());
                    logger.debug("商品 {} 的深度分析结果数: {}", item.getProductId(), deepAnalysisResults.size());
                }
                
                long queryTime = System.currentTimeMillis() - startTime;
                Span.current().setAttribute("chaos.slow_query_executed", true);
                Span.current().setAttribute("chaos.slow_query_duration_ms", queryTime);
                
                logger.warn("慢SQL深度分析完成 - 耗时: {}ms", queryTime);
                
            } catch (Exception e) {
                logger.error("慢SQL深度分析执行失败: {}", e.getMessage(), e);
                Span.current().setAttribute("chaos.slow_query_error", e.getMessage());
            }
        }
        
        List<InventoryItem> items = inventoryRepository.findLowStockItems(threshold);
        
        Span.current().setAttribute("inventory.low_stock_count", items.size());
        
        return items.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private InventoryItemDto convertToDto(InventoryItem item) {
        return new InventoryItemDto(
                item.getProductId(),
                item.getAvailableQuantity(),
                item.getReservedQuantity(),
                item.getTotalQuantity(),
                item.getWarehouseLocation(),
                item.getLastUpdatedTimestamp()
        );
    }
} 