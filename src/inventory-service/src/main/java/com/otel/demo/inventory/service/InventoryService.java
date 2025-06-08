package com.otel.demo.inventory.service;

import com.otel.demo.inventory.dto.CartItemDto;
import com.otel.demo.inventory.dto.InventoryItemDto;
import java.util.List;
import java.util.Map;

public interface InventoryService {

    /**
     * 根据商品ID获取库存信息
     */
    InventoryItemDto getInventory(String productId);

    /**
     * 检查商品可用性
     */
    Map<String, Boolean> checkAvailability(List<CartItemDto> items);

    /**
     * 更新库存数量
     */
    InventoryItemDto updateInventory(String productId, Integer quantityChange, String operationType, String reason);

    /**
     * 预留库存
     */
    boolean reserveInventory(List<CartItemDto> items, String reservationId);

    /**
     * 释放库存
     */
    boolean releaseInventory(String reservationId);

    /**
     * 获取所有库存信息
     */
    List<InventoryItemDto> getAllInventory();

    /**
     * 根据仓库位置获取库存
     */
    List<InventoryItemDto> getInventoryByWarehouse(String warehouseLocation);

    /**
     * 获取库存不足的商品
     */
    List<InventoryItemDto> getLowStockItems(Integer threshold);
} 