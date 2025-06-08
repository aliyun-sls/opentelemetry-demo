-- 创建库存表
CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(255) PRIMARY KEY,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    total_quantity INT NOT NULL DEFAULT 0,
    warehouse_location VARCHAR(255) NOT NULL DEFAULT 'main-warehouse',
    last_updated_timestamp BIGINT NOT NULL DEFAULT (UNIX_TIMESTAMP() * 1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建库存预留表
CREATE TABLE IF NOT EXISTS inventory_reservations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    reserved_quantity INT NOT NULL,
    expiration_timestamp BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES inventory(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建库存操作日志表
CREATE TABLE IF NOT EXISTS inventory_operations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    operation_type VARCHAR(50) NOT NULL COMMENT 'restock, adjustment, damage, reserve, release',
    quantity_change INT NOT NULL,
    reason TEXT,
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES inventory(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建索引
CREATE INDEX idx_inventory_reservations_reservation_id ON inventory_reservations(reservation_id);
CREATE INDEX idx_inventory_reservations_product_id ON inventory_reservations(product_id);
CREATE INDEX idx_inventory_reservations_expiration ON inventory_reservations(expiration_timestamp);
CREATE INDEX idx_inventory_operations_product_id ON inventory_operations(product_id);
CREATE INDEX idx_inventory_operations_performed_at ON inventory_operations(performed_at);

-- 创建触发器来自动更新 last_updated_timestamp 字段
DELIMITER $$
CREATE TRIGGER update_inventory_timestamp 
BEFORE UPDATE ON inventory
FOR EACH ROW
BEGIN
    SET NEW.last_updated_timestamp = UNIX_TIMESTAMP() * 1000;
END$$
DELIMITER ; 