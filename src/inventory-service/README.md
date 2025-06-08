# 库存服务 (Inventory Service)

库存服务是OpenTelemetry天文商店演示应用的一部分，负责管理商品库存数据。

## 功能特性

- **库存查询**: 查询特定商品的库存信息
- **库存可用性检查**: 检查购物车中商品的库存可用性
- **库存预留**: 为订单预留库存
- **库存释放**: 释放已预留的库存
- **库存更新**: 更新商品库存数量
- **操作日志**: 记录所有库存操作的历史记录

## 业务场景

### 1. 购物车验证
当用户将商品添加到购物车时，前端会调用库存服务检查商品是否有足够的库存。

### 2. 订单处理
在结账过程中，库存服务会：
1. 预留订单中的商品库存
2. 如果支付成功，确认库存扣减
3. 如果支付失败，释放预留的库存

### 3. 库存管理
管理员可以通过库存服务：
- 补充商品库存
- 调整库存数量
- 查看库存操作历史

## 数据库设计

### 库存表 (inventory)
- `product_id`: 商品ID（主键）
- `available_quantity`: 可用库存数量
- `reserved_quantity`: 已预留库存数量
- `total_quantity`: 总库存数量
- `warehouse_location`: 仓库位置
- `last_updated_timestamp`: 最后更新时间戳

### 库存预留表 (inventory_reservations)
- `reservation_id`: 预留ID
- `product_id`: 商品ID
- `reserved_quantity`: 预留数量
- `expiration_timestamp`: 过期时间戳

### 库存操作日志表 (inventory_operations)
- `product_id`: 商品ID
- `operation_type`: 操作类型（restock, adjustment, damage, reserve, release）
- `quantity_change`: 数量变化
- `reason`: 操作原因

## API接口

### gRPC服务

1. **GetInventory**: 获取商品库存信息
2. **CheckAvailability**: 检查商品可用性
3. **ReserveInventory**: 预留库存
4. **ReleaseInventory**: 释放库存
5. **UpdateInventory**: 更新库存

## 环境变量

- `INVENTORY_PORT`: 服务端口（默认: 9090）
- `POSTGRES_HOST`: PostgreSQL主机地址
- `POSTGRES_PORT`: PostgreSQL端口
- `POSTGRES_DB`: 数据库名称
- `POSTGRES_USER`: 数据库用户名
- `POSTGRES_PASSWORD`: 数据库密码

## 监控和可观测性

库存服务集成了OpenTelemetry，提供：
- **分布式追踪**: 跟踪每个库存操作的完整链路
- **指标监控**: 统计库存操作次数和类型
- **日志记录**: 详细的操作日志

## 与其他服务的集成

- **购物车服务**: 验证商品库存可用性
- **结账服务**: 预留和确认库存
- **产品目录服务**: 获取商品信息
- **支付服务**: 根据支付结果确认或释放库存

## 本地开发

```bash
# 构建服务
./gradlew build

# 运行服务
./gradlew run

# 运行测试
./gradlew test
```

## Docker构建

```bash
# 构建Docker镜像
docker build -t inventory-service .

# 运行容器
docker run -p 9090:9090 inventory-service
``` 