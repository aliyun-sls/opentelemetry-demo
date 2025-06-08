package com.otel.demo.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InventoryItemDto {

    @NotBlank(message = "商品ID不能为空")
    private String productId;

    @NotNull(message = "可用库存数量不能为空")
    @Min(value = 0, message = "可用库存数量不能为负数")
    private Integer availableQuantity;

    @NotNull(message = "预留库存数量不能为空")
    @Min(value = 0, message = "预留库存数量不能为负数")
    private Integer reservedQuantity;

    @NotNull(message = "总库存数量不能为空")
    @Min(value = 0, message = "总库存数量不能为负数")
    private Integer totalQuantity;

    @NotBlank(message = "仓库位置不能为空")
    private String warehouseLocation;

    private Long lastUpdatedTimestamp;

    // 默认构造函数
    public InventoryItemDto() {}

    // 构造函数
    public InventoryItemDto(String productId, Integer availableQuantity, Integer reservedQuantity,
                           Integer totalQuantity, String warehouseLocation, Long lastUpdatedTimestamp) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.totalQuantity = totalQuantity;
        this.warehouseLocation = warehouseLocation;
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    // Getters and Setters
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public String getWarehouseLocation() {
        return warehouseLocation;
    }

    public void setWarehouseLocation(String warehouseLocation) {
        this.warehouseLocation = warehouseLocation;
    }

    public Long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp(Long lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    @Override
    public String toString() {
        return "InventoryItemDto{" +
                "productId='" + productId + '\'' +
                ", availableQuantity=" + availableQuantity +
                ", reservedQuantity=" + reservedQuantity +
                ", totalQuantity=" + totalQuantity +
                ", warehouseLocation='" + warehouseLocation + '\'' +
                ", lastUpdatedTimestamp=" + lastUpdatedTimestamp +
                '}';
    }
} 