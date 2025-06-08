package com.otel.demo.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    @NotBlank(message = "商品ID不能为空")
    private String productId;

    @Column(name = "available_quantity")
    @NotNull(message = "可用库存数量不能为空")
    @Min(value = 0, message = "可用库存数量不能为负数")
    private Integer availableQuantity;

    @Column(name = "reserved_quantity")
    @NotNull(message = "预留库存数量不能为空")
    @Min(value = 0, message = "预留库存数量不能为负数")
    private Integer reservedQuantity;

    @Column(name = "total_quantity")
    @NotNull(message = "总库存数量不能为空")
    @Min(value = 0, message = "总库存数量不能为负数")
    private Integer totalQuantity;

    @Column(name = "warehouse_location")
    @NotBlank(message = "仓库位置不能为空")
    private String warehouseLocation;

    @Column(name = "last_updated_timestamp")
    private Long lastUpdatedTimestamp;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 默认构造函数
    public InventoryItem() {}

    // 构造函数
    public InventoryItem(String productId, Integer availableQuantity, Integer reservedQuantity, 
                        Integer totalQuantity, String warehouseLocation) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.totalQuantity = totalQuantity;
        this.warehouseLocation = warehouseLocation;
        this.lastUpdatedTimestamp = System.currentTimeMillis();
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
        this.lastUpdatedTimestamp = System.currentTimeMillis();
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
        this.lastUpdatedTimestamp = System.currentTimeMillis();
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
        this.lastUpdatedTimestamp = System.currentTimeMillis();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastUpdatedTimestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "InventoryItem{" +
                "productId='" + productId + '\'' +
                ", availableQuantity=" + availableQuantity +
                ", reservedQuantity=" + reservedQuantity +
                ", totalQuantity=" + totalQuantity +
                ", warehouseLocation='" + warehouseLocation + '\'' +
                ", lastUpdatedTimestamp=" + lastUpdatedTimestamp +
                '}';
    }
} 