// Copyright The OpenTelemetry Authors
// SPDX-License-Identifier: Apache-2.0
package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
)

type InventoryServiceClient struct {
	httpClient *http.Client
	baseURL    string
}

type InventoryItem struct {
	ProductID            string `json:"productId"`
	AvailableQuantity    int32  `json:"availableQuantity"`
	ReservedQuantity     int32  `json:"reservedQuantity"`
	TotalQuantity        int32  `json:"totalQuantity"`
	WarehouseLocation    string `json:"warehouseLocation"`
	LastUpdatedTimestamp int64  `json:"lastUpdatedTimestamp"`
}

type CartItemForInventory struct {
	ProductID string `json:"productId"`
	Quantity  int32  `json:"quantity"`
}

type CheckAvailabilityRequest struct {
	Items []CartItemForInventory `json:"items"`
}

type CheckAvailabilityResponse struct {
	Availability map[string]bool `json:"availability"`
	AllAvailable bool            `json:"allAvailable"`
}

type ReservationResponse struct {
	Success       bool   `json:"success"`
	ReservationID string `json:"reservationId"`
	Message       string `json:"message"`
}

func NewInventoryServiceClient() *InventoryServiceClient {
	baseURL := os.Getenv("INVENTORY_ADDR")
	if baseURL == "" {
		baseURL = "http://inventory:8080"
	}

	return &InventoryServiceClient{
		httpClient: &http.Client{
			Timeout:   30 * time.Second,
			Transport: otelhttp.NewTransport(http.DefaultTransport),
		},
		baseURL: baseURL,
	}
}

func (c *InventoryServiceClient) GetInventory(ctx context.Context, productID string) (*InventoryItem, error) {
	span := trace.SpanFromContext(ctx)
	span.SetAttributes(attribute.String("inventory.product_id", productID))

	url := fmt.Sprintf("%s/api/v1/inventory/%s", c.baseURL, productID)
	log.Infof("调用库存服务获取商品信息: %s", productID)

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		log.Errorf("调用库存服务失败: %v", err)
		span.SetAttributes(attribute.String("error", err.Error()))
		return nil, fmt.Errorf("调用库存服务失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Warnf("获取商品 %s 库存信息失败，状态码: %d", productID, resp.StatusCode)
		return nil, fmt.Errorf("获取库存信息失败，状态码: %d", resp.StatusCode)
	}

	var inventory InventoryItem
	if err := json.NewDecoder(resp.Body).Decode(&inventory); err != nil {
		return nil, fmt.Errorf("解析响应失败: %w", err)
	}

	log.Infof("成功获取商品 %s 的库存信息", productID)
	return &inventory, nil
}

func (c *InventoryServiceClient) CheckAvailability(ctx context.Context, items []CartItemForInventory) (map[string]bool, error) {
	span := trace.SpanFromContext(ctx)
	span.SetAttributes(attribute.Int("inventory.items_count", len(items)))

	url := fmt.Sprintf("%s/api/v1/inventory/check-availability", c.baseURL)
	log.Infof("检查 %d 个商品的可用性", len(items))

	request := CheckAvailabilityRequest{Items: items}
	jsonData, err := json.Marshal(request)
	if err != nil {
		return nil, fmt.Errorf("序列化请求失败: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		log.Errorf("检查商品可用性失败: %v", err)
		span.SetAttributes(attribute.String("error", err.Error()))
		return nil, fmt.Errorf("检查商品可用性失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Warnf("检查商品可用性失败，状态码: %d", resp.StatusCode)
		return nil, fmt.Errorf("检查商品可用性失败，状态码: %d", resp.StatusCode)
	}

	var response CheckAvailabilityResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, fmt.Errorf("解析响应失败: %w", err)
	}

	return response.Availability, nil
}

func (c *InventoryServiceClient) ReserveInventory(ctx context.Context, items []CartItemForInventory, reservationID string) (bool, error) {
	span := trace.SpanFromContext(ctx)
	span.SetAttributes(
		attribute.String("inventory.reservation_id", reservationID),
		attribute.Int("inventory.items_count", len(items)),
	)

	url := fmt.Sprintf("%s/api/v1/inventory/reserve?reservationId=%s", c.baseURL, reservationID)
	log.Infof("预留库存: 预留ID=%s, 商品数量=%d", reservationID, len(items))

	jsonData, err := json.Marshal(items)
	if err != nil {
		return false, fmt.Errorf("序列化请求失败: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return false, fmt.Errorf("创建请求失败: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		log.Errorf("预留库存失败: %v", err)
		span.SetAttributes(attribute.String("error", err.Error()))
		return false, fmt.Errorf("预留库存失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Warnf("预留库存失败，状态码: %d", resp.StatusCode)
		return false, fmt.Errorf("预留库存失败，状态码: %d", resp.StatusCode)
	}

	var response ReservationResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return false, fmt.Errorf("解析响应失败: %w", err)
	}

	log.Infof("库存预留结果: %s", map[bool]string{true: "成功", false: "失败"}[response.Success])
	return response.Success, nil
}

func (c *InventoryServiceClient) ReleaseInventory(ctx context.Context, reservationID string) (bool, error) {
	span := trace.SpanFromContext(ctx)
	span.SetAttributes(attribute.String("inventory.reservation_id", reservationID))

	url := fmt.Sprintf("%s/api/v1/inventory/release?reservationId=%s", c.baseURL, reservationID)
	log.Infof("释放库存: 预留ID=%s", reservationID)

	req, err := http.NewRequestWithContext(ctx, "POST", url, nil)
	if err != nil {
		return false, fmt.Errorf("创建请求失败: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		log.Errorf("释放库存失败: %v", err)
		span.SetAttributes(attribute.String("error", err.Error()))
		return false, fmt.Errorf("释放库存失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Warnf("释放库存失败，状态码: %d", resp.StatusCode)
		return false, fmt.Errorf("释放库存失败，状态码: %d", resp.StatusCode)
	}

	var response ReservationResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return false, fmt.Errorf("解析响应失败: %w", err)
	}

	log.Infof("库存释放结果: %s", map[bool]string{true: "成功", false: "失败"}[response.Success])
	return response.Success, nil
}
