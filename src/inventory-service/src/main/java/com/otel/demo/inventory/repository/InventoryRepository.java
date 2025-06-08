package com.otel.demo.inventory.repository;

import com.otel.demo.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    /**
     * 根据商品ID查找库存
     */
    Optional<InventoryItem> findByProductId(String productId);

    /**
     * 根据仓库位置查找库存
     */
    List<InventoryItem> findByWarehouseLocation(String warehouseLocation);

    /**
     * 查找可用库存大于指定数量的商品
     */
    @Query("SELECT i FROM InventoryItem i WHERE i.availableQuantity >= :minQuantity")
    List<InventoryItem> findByAvailableQuantityGreaterThanEqual(@Param("minQuantity") Integer minQuantity);

    /**
     * 查找库存不足的商品（可用库存小于指定阈值）
     */
    @Query("SELECT i FROM InventoryItem i WHERE i.availableQuantity < :threshold")
    List<InventoryItem> findLowStockItems(@Param("threshold") Integer threshold);

    /**
     * 更新库存数量
     */
    @Modifying
    @Query("UPDATE InventoryItem i SET i.availableQuantity = i.availableQuantity + :quantityChange, " +
           "i.totalQuantity = i.totalQuantity + :quantityChange, " +
           "i.lastUpdatedTimestamp = :timestamp WHERE i.productId = :productId")
    int updateInventoryQuantity(@Param("productId") String productId, 
                               @Param("quantityChange") Integer quantityChange,
                               @Param("timestamp") Long timestamp);

    /**
     * 预留库存
     */
    @Modifying
    @Query("UPDATE InventoryItem i SET i.availableQuantity = i.availableQuantity - :quantity, " +
           "i.reservedQuantity = i.reservedQuantity + :quantity, " +
           "i.lastUpdatedTimestamp = :timestamp WHERE i.productId = :productId AND i.availableQuantity >= :quantity")
    int reserveInventory(@Param("productId") String productId, 
                        @Param("quantity") Integer quantity,
                        @Param("timestamp") Long timestamp);

    /**
     * 释放库存
     */
    @Modifying
    @Query("UPDATE InventoryItem i SET i.availableQuantity = i.availableQuantity + :quantity, " +
           "i.reservedQuantity = i.reservedQuantity - :quantity, " +
           "i.lastUpdatedTimestamp = :timestamp WHERE i.productId = :productId AND i.reservedQuantity >= :quantity")
    int releaseInventory(@Param("productId") String productId, 
                        @Param("quantity") Integer quantity,
                        @Param("timestamp") Long timestamp);

    /**
     * 检查商品是否有足够的可用库存
     */
    @Query("SELECT CASE WHEN i.availableQuantity >= :requiredQuantity THEN true ELSE false END " +
           "FROM InventoryItem i WHERE i.productId = :productId")
    Boolean hasAvailableStock(@Param("productId") String productId, @Param("requiredQuantity") Integer requiredQuantity);

    /**
     * 批量查询商品的可用库存
     */
    @Query("SELECT i FROM InventoryItem i WHERE i.productId IN :productIds")
    List<InventoryItem> findByProductIdIn(@Param("productIds") List<String> productIds);

    /**
     * 慢SQL查询 - 复杂的库存统计查询（用于故障注入）
     * 这个查询会执行多个子查询和复杂的计算，导致查询变慢
     */
    @Query(value = """
        SELECT 
            i1.product_id,
            i1.available_quantity,
            i1.reserved_quantity,
            i1.total_quantity,
            i1.warehouse_location,
            (SELECT COUNT(*) FROM inventory i2 WHERE i2.warehouse_location = i1.warehouse_location) as warehouse_item_count,
            (SELECT AVG(i3.available_quantity) FROM inventory i3 WHERE i3.warehouse_location = i1.warehouse_location) as avg_warehouse_stock,
            (SELECT SUM(i4.total_quantity) FROM inventory i4 WHERE i4.warehouse_location = i1.warehouse_location) as total_warehouse_stock,
            (SELECT COUNT(*) FROM inventory i5 WHERE i5.available_quantity < 10) as low_stock_count,
            (SELECT MAX(i6.last_updated_timestamp) FROM inventory i6) as latest_update,
            CASE 
                WHEN i1.available_quantity > (SELECT AVG(i7.available_quantity) FROM inventory i7) THEN 'HIGH'
                WHEN i1.available_quantity < (SELECT AVG(i8.available_quantity) FROM inventory i8) * 0.5 THEN 'LOW'
                ELSE 'MEDIUM'
            END as stock_level
        FROM inventory i1
        WHERE i1.product_id = :productId
        ORDER BY (
            SELECT COUNT(*) 
            FROM inventory i9 
            WHERE i9.warehouse_location = i1.warehouse_location 
            AND i9.available_quantity > i1.available_quantity
        )
        """, nativeQuery = true)
    List<Object[]> executeSlowInventoryAnalysis(@Param("productId") String productId);

    /**
     * 慢SQL查询 - 复杂的库存趋势分析（用于故障注入）
     * 使用递归CTE和复杂的窗口函数
     */
    @Query(value = """
        WITH RECURSIVE warehouse_hierarchy AS (
            SELECT 
                warehouse_location,
                warehouse_location as root_location,
                0 as level
            FROM inventory 
            WHERE warehouse_location IS NOT NULL
            GROUP BY warehouse_location
            
            UNION ALL
            
            SELECT 
                i.warehouse_location,
                wh.root_location,
                wh.level + 1
            FROM inventory i
            JOIN warehouse_hierarchy wh ON i.warehouse_location != wh.warehouse_location
            WHERE wh.level < 3
        ),
        stock_analysis AS (
            SELECT 
                i.product_id,
                i.warehouse_location,
                i.available_quantity,
                i.total_quantity,
                ROW_NUMBER() OVER (PARTITION BY i.warehouse_location ORDER BY i.available_quantity DESC) as stock_rank,
                LAG(i.available_quantity, 1, 0) OVER (PARTITION BY i.warehouse_location ORDER BY i.last_updated_timestamp) as prev_quantity,
                LEAD(i.available_quantity, 1, 0) OVER (PARTITION BY i.warehouse_location ORDER BY i.last_updated_timestamp) as next_quantity,
                AVG(i.available_quantity) OVER (PARTITION BY i.warehouse_location) as avg_warehouse_stock,
                SUM(i.total_quantity) OVER (PARTITION BY i.warehouse_location) as total_warehouse_stock,
                COUNT(*) OVER (PARTITION BY i.warehouse_location) as warehouse_item_count
            FROM inventory i
        )
        SELECT 
            sa.product_id,
            sa.warehouse_location,
            sa.available_quantity,
            sa.stock_rank,
            sa.prev_quantity,
            sa.next_quantity,
            sa.avg_warehouse_stock,
            sa.total_warehouse_stock,
            sa.warehouse_item_count,
            wh.level as hierarchy_level,
            (sa.available_quantity - sa.avg_warehouse_stock) as stock_deviation,
            CASE 
                WHEN sa.stock_rank <= sa.warehouse_item_count * 0.2 THEN 'TOP_20_PERCENT'
                WHEN sa.stock_rank <= sa.warehouse_item_count * 0.5 THEN 'TOP_50_PERCENT'
                ELSE 'BOTTOM_50_PERCENT'
            END as stock_category
        FROM stock_analysis sa
        JOIN warehouse_hierarchy wh ON sa.warehouse_location = wh.warehouse_location
        WHERE sa.product_id = :productId
        ORDER BY sa.stock_rank, wh.level DESC
        """, nativeQuery = true)
    List<Object[]> executeSlowInventoryTrendAnalysis(@Param("productId") String productId);

    /**
     * 慢SQL查询 - 全库存深度分析（用于故障注入）
     * 执行多个复杂的聚合查询和自连接
     */
    @Query(value = """
        SELECT 
            main.product_id,
            main.available_quantity,
            main.warehouse_location,
            stats.total_items,
            stats.avg_stock,
            stats.min_stock,
            stats.max_stock,
            stats.std_dev_stock,
            percentiles.p25,
            percentiles.p50,
            percentiles.p75,
            percentiles.p90,
            correlations.location_correlation,
            trends.stock_trend
        FROM inventory main
        CROSS JOIN (
            SELECT 
                COUNT(*) as total_items,
                AVG(available_quantity) as avg_stock,
                MIN(available_quantity) as min_stock,
                MAX(available_quantity) as max_stock,
                STDDEV(available_quantity) as std_dev_stock
            FROM inventory
        ) stats
        CROSS JOIN (
            SELECT 
                (SELECT available_quantity FROM inventory ORDER BY available_quantity LIMIT 1 OFFSET (SELECT COUNT(*) * 0.25 FROM inventory)) as p25,
                (SELECT available_quantity FROM inventory ORDER BY available_quantity LIMIT 1 OFFSET (SELECT COUNT(*) * 0.50 FROM inventory)) as p50,
                (SELECT available_quantity FROM inventory ORDER BY available_quantity LIMIT 1 OFFSET (SELECT COUNT(*) * 0.75 FROM inventory)) as p75,
                (SELECT available_quantity FROM inventory ORDER BY available_quantity LIMIT 1 OFFSET (SELECT COUNT(*) * 0.90 FROM inventory)) as p90
        ) percentiles
        CROSS JOIN (
            SELECT 
                COALESCE(
                    (SELECT AVG(i1.available_quantity * i2.available_quantity) 
                     FROM inventory i1 
                     JOIN inventory i2 ON i1.warehouse_location = i2.warehouse_location 
                     WHERE i1.product_id != i2.product_id), 0
                ) as location_correlation
        ) correlations
        CROSS JOIN (
            SELECT 
                CASE 
                    WHEN (SELECT COUNT(*) FROM inventory WHERE available_quantity > (SELECT AVG(available_quantity) FROM inventory)) > 
                         (SELECT COUNT(*) FROM inventory WHERE available_quantity < (SELECT AVG(available_quantity) FROM inventory))
                    THEN 'INCREASING'
                    ELSE 'DECREASING'
                END as stock_trend
        ) trends
        WHERE main.product_id = :productId
        """, nativeQuery = true)
    List<Object[]> executeSlowInventoryDeepAnalysis(@Param("productId") String productId);
} 