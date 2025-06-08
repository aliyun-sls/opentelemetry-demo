# 库存服务 API 文档

库存服务提供HTTP REST API来管理天文商店的商品库存。

## 基础信息

- **服务名称**: inventory-service
- **端口**: 9090
- **基础URL**: `http://localhost:9090/api/v1/inventory`

## API 端点

### 1. 获取商品库存信息

**GET** `/api/v1/inventory/{productId}`

获取指定商品的库存详细信息。

**路径参数:**
- `productId` (string): 商品ID

**响应示例:**
```json
{
  "productId": "OLJCESPC7Z",
  "availableQuantity": 25,
  "reservedQuantity": 0,
  "totalQuantity": 25,
  "warehouseLocation": "main-warehouse",
  "lastUpdatedTimestamp": 1703123456789
}
```

### 2. 检查商品可用性

**POST** `/api/v1/inventory/check-availability`

检查购物车中商品的库存可用性。

**请求体:**
```json
{
  "items": [
    {
      "productId": "OLJCESPC7Z",
      "quantity": 2
    },
    {
      "productId": "66VCHSJNUP",
      "quantity": 1
    }
  ]
}
```

**响应示例:**
```json
{
  "availability": {
    "OLJCESPC7Z": true,
    "66VCHSJNUP": true
  },
  "allAvailable": true
}
```

### 3. 更新库存数量

**PUT** `/api/v1/inventory/{productId}`

更新指定商品的库存数量。

**路径参数:**
- `productId` (string): 商品ID

**查询参数:**
- `quantityChange` (integer): 库存变化数量（正数为增加，负数为减少）
- `operationType` (string, 可选): 操作类型，默认为 "manual"
- `reason` (string, 可选): 操作原因，默认为 "Manual adjustment"

**示例请求:**
```
PUT /api/v1/inventory/OLJCESPC7Z?quantityChange=10&operationType=restock&reason=New shipment arrived
```

**响应示例:**
```json
{
  "productId": "OLJCESPC7Z",
  "availableQuantity": 35,
  "reservedQuantity": 0,
  "totalQuantity": 35,
  "warehouseLocation": "main-warehouse",
  "lastUpdatedTimestamp": 1703123456789
}
```

### 4. 预留库存

**POST** `/api/v1/inventory/reserve`

为订单预留库存。

**查询参数:**
- `reservationId` (string): 预留ID

**请求体:**
```json
[
  {
    "productId": "OLJCESPC7Z",
    "quantity": 2
  },
  {
    "productId": "66VCHSJNUP",
    "quantity": 1
  }
]
```

**响应示例:**
```json
{
  "success": true,
  "reservationId": "reservation-123",
  "message": "库存预留成功"
}
```

### 5. 释放库存

**POST** `/api/v1/inventory/release`

释放已预留的库存。

**查询参数:**
- `reservationId` (string): 预留ID

**响应示例:**
```json
{
  "success": true,
  "reservationId": "reservation-123",
  "message": "库存释放成功"
}
```

### 6. 获取所有库存信息

**GET** `/api/v1/inventory`

获取所有商品的库存信息。

**响应示例:**
```json
[
  {
    "productId": "OLJCESPC7Z",
    "availableQuantity": 25,
    "reservedQuantity": 0,
    "totalQuantity": 25,
    "warehouseLocation": "main-warehouse",
    "lastUpdatedTimestamp": 1703123456789
  },
  {
    "productId": "66VCHSJNUP",
    "availableQuantity": 15,
    "reservedQuantity": 0,
    "totalQuantity": 15,
    "warehouseLocation": "main-warehouse",
    "lastUpdatedTimestamp": 1703123456789
  }
]
```

### 7. 根据仓库位置获取库存

**GET** `/api/v1/inventory/warehouse/{warehouseLocation}`

获取指定仓库的所有库存信息。

**路径参数:**
- `warehouseLocation` (string): 仓库位置

**响应示例:**
```json
[
  {
    "productId": "TELESCOPE-001",
    "availableQuantity": 12,
    "reservedQuantity": 0,
    "totalQuantity": 12,
    "warehouseLocation": "astronomy-warehouse",
    "lastUpdatedTimestamp": 1703123456789
  }
]
```

### 8. 获取库存不足的商品

**GET** `/api/v1/inventory/low-stock`

获取库存不足的商品列表。

**查询参数:**
- `threshold` (integer, 可选): 库存阈值，默认为 10

**响应示例:**
```json
[
  {
    "productId": "TELESCOPE-002",
    "availableQuantity": 8,
    "reservedQuantity": 0,
    "totalQuantity": 8,
    "warehouseLocation": "astronomy-warehouse",
    "lastUpdatedTimestamp": 1703123456789
  }
]
```

### 9. 健康检查

**GET** `/api/v1/inventory/health`

检查服务健康状态。

**响应示例:**
```json
{
  "status": "UP",
  "service": "inventory-service",
  "timestamp": "1703123456789"
}
```

## 错误响应

当请求失败时，API会返回相应的HTTP状态码和错误信息：

- `400 Bad Request`: 请求参数错误
- `404 Not Found`: 商品不存在
- `500 Internal Server Error`: 服务器内部错误

**错误响应示例:**
```json
{
  "timestamp": "2023-12-21T10:30:45.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "商品不存在: INVALID_PRODUCT_ID",
  "path": "/api/v1/inventory/INVALID_PRODUCT_ID"
}
```

## 与其他服务的集成

### 购物车服务集成示例

```javascript
// 检查购物车商品可用性
const checkCartAvailability = async (cartItems) => {
  const response = await fetch('http://inventory:9090/api/v1/inventory/check-availability', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ items: cartItems })
  });
  
  const result = await response.json();
  return result.allAvailable;
};
```

### 结账服务集成示例

```javascript
// 预留库存
const reserveInventory = async (cartItems, orderId) => {
  const response = await fetch(`http://inventory:9090/api/v1/inventory/reserve?reservationId=${orderId}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(cartItems)
  });
  
  const result = await response.json();
  return result.success;
};
```

## OpenTelemetry 追踪

所有API端点都集成了OpenTelemetry分布式追踪，可以在Jaeger UI中查看完整的请求链路。

追踪属性包括：
- `http.route`: HTTP路由
- `inventory.product_id`: 商品ID
- `inventory.items_count`: 商品数量
- `inventory.operation_type`: 操作类型
- `inventory.warehouse_location`: 仓库位置 