package com.otel.demo.inventory.controller;

import com.otel.demo.inventory.dto.CartItemDto;
import com.otel.demo.inventory.dto.CheckAvailabilityRequest;
import com.otel.demo.inventory.dto.InventoryItemDto;
import com.otel.demo.inventory.service.InventoryService;
import com.otel.demo.inventory.service.InventoryMonitoringService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryMonitoringService inventoryMonitoringService;

    /**
     * 获取商品库存信息
     */
    @GetMapping("/{productId}")
    @WithSpan("getInventoryEndpoint")
    public ResponseEntity<InventoryItemDto> getInventory(@PathVariable String productId) {
        logger.info("REST API: 获取商品库存信息 - {}", productId);
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/{productId}");
        Span.current().setAttribute("inventory.product_id", productId);
        
        try {
            InventoryItemDto inventory = inventoryService.getInventory(productId);
            return ResponseEntity.ok(inventory);
        } catch (RuntimeException e) {
            logger.error("获取库存信息失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 检查商品可用性
     */
    @PostMapping("/check-availability")
    @WithSpan("checkAvailabilityEndpoint")
    public ResponseEntity<Map<String, Object>> checkAvailability(@Valid @RequestBody CheckAvailabilityRequest request) {
        logger.info("REST API: 检查商品可用性 - 商品数量: {}", request.getItems().size());
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/check-availability");
        Span.current().setAttribute("inventory.items_count", request.getItems().size());
        
        try {
            Map<String, Boolean> availability = inventoryService.checkAvailability(request.getItems());
            
            Map<String, Object> response = new HashMap<>();
            response.put("availability", availability);
            response.put("allAvailable", availability.values().stream().allMatch(Boolean::booleanValue));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("检查可用性失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新库存数量
     */
    @PutMapping("/{productId}")
    @WithSpan("updateInventoryEndpoint")
    public ResponseEntity<InventoryItemDto> updateInventory(
            @PathVariable String productId,
            @RequestParam Integer quantityChange,
            @RequestParam(defaultValue = "manual") String operationType,
            @RequestParam(defaultValue = "Manual adjustment") String reason) {
        
        logger.info("REST API: 更新库存 - 商品ID: {}, 数量变化: {}", productId, quantityChange);
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/{productId}");
        Span.current().setAttribute("inventory.product_id", productId);
        Span.current().setAttribute("inventory.quantity_change", quantityChange);
        
        try {
            InventoryItemDto updatedInventory = inventoryService.updateInventory(productId, quantityChange, operationType, reason);
            return ResponseEntity.ok(updatedInventory);
        } catch (RuntimeException e) {
            logger.error("更新库存失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 预留库存
     */
    @PostMapping("/reserve")
    @WithSpan("reserveInventoryEndpoint")
    public ResponseEntity<Map<String, Object>> reserveInventory(
            @Valid @RequestBody List<CartItemDto> items,
            @RequestParam String reservationId) {
        
        logger.info("REST API: 预留库存 - 预留ID: {}, 商品数量: {}", reservationId, items.size());
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/reserve");
        Span.current().setAttribute("inventory.reservation_id", reservationId);
        Span.current().setAttribute("inventory.items_count", items.size());
        
        try {
            boolean success = inventoryService.reserveInventory(items, reservationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("reservationId", reservationId);
            response.put("message", success ? "库存预留成功" : "库存预留失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("预留库存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 释放库存
     */
    @PostMapping("/release")
    @WithSpan("releaseInventoryEndpoint")
    public ResponseEntity<Map<String, Object>> releaseInventory(@RequestParam String reservationId) {
        logger.info("REST API: 释放库存 - 预留ID: {}", reservationId);
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/release");
        Span.current().setAttribute("inventory.reservation_id", reservationId);
        
        try {
            boolean success = inventoryService.releaseInventory(reservationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("reservationId", reservationId);
            response.put("message", success ? "库存释放成功" : "库存释放失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("释放库存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有库存信息
     */
    @GetMapping
    @WithSpan("getAllInventoryEndpoint")
    public ResponseEntity<List<InventoryItemDto>> getAllInventory() {
        logger.info("REST API: 获取所有库存信息");
        
        Span.current().setAttribute("http.route", "/api/v1/inventory");
        
        try {
            List<InventoryItemDto> inventoryList = inventoryService.getAllInventory();
            return ResponseEntity.ok(inventoryList);
        } catch (Exception e) {
            logger.error("获取所有库存信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据仓库位置获取库存
     */
    @GetMapping("/warehouse/{warehouseLocation}")
    @WithSpan("getInventoryByWarehouseEndpoint")
    public ResponseEntity<List<InventoryItemDto>> getInventoryByWarehouse(@PathVariable String warehouseLocation) {
        logger.info("REST API: 根据仓库位置获取库存 - {}", warehouseLocation);
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/warehouse/{warehouseLocation}");
        Span.current().setAttribute("inventory.warehouse_location", warehouseLocation);
        
        try {
            List<InventoryItemDto> inventoryList = inventoryService.getInventoryByWarehouse(warehouseLocation);
            return ResponseEntity.ok(inventoryList);
        } catch (Exception e) {
            logger.error("根据仓库位置获取库存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取库存不足的商品
     */
    @GetMapping("/low-stock")
    @WithSpan("getLowStockItemsEndpoint")
    public ResponseEntity<List<InventoryItemDto>> getLowStockItems(@RequestParam(defaultValue = "10") Integer threshold) {
        logger.info("REST API: 获取库存不足的商品 - 阈值: {}", threshold);
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/low-stock");
        Span.current().setAttribute("inventory.threshold", threshold);
        
        try {
            List<InventoryItemDto> lowStockItems = inventoryService.getLowStockItems(threshold);
            return ResponseEntity.ok(lowStockItems);
        } catch (Exception e) {
            logger.error("获取库存不足商品失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "inventory-service");
        status.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(status);
    }

    /**
     * 手动触发库存检查和补货
     */
    @PostMapping("/monitoring/trigger-check")
    @WithSpan("triggerInventoryCheckEndpoint")
    public ResponseEntity<Map<String, Object>> triggerInventoryCheck() {
        logger.info("REST API: 手动触发库存检查和补货");
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/monitoring/trigger-check");
        
        try {
            inventoryMonitoringService.triggerManualInventoryCheck();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "库存检查和补货任务已触发");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("触发库存检查失败: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "触发库存检查失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取库存监控统计信息
     */
    @GetMapping("/monitoring/stats")
    @WithSpan("getMonitoringStatsEndpoint")
    public ResponseEntity<InventoryMonitoringService.InventoryMonitoringStats> getMonitoringStats() {
        logger.info("REST API: 获取库存监控统计信息");
        
        Span.current().setAttribute("http.route", "/api/v1/inventory/monitoring/stats");
        
        try {
            InventoryMonitoringService.InventoryMonitoringStats stats = inventoryMonitoringService.getMonitoringStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("获取监控统计信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
} 