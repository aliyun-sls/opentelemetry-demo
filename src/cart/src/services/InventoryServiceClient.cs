// Copyright The OpenTelemetry Authors
// SPDX-License-Identifier: Apache-2.0
using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using System.Diagnostics;

namespace cart.services;

public class InventoryServiceClient
{
    private readonly HttpClient _httpClient;
    private readonly ILogger<InventoryServiceClient> _logger;
    private readonly string _inventoryServiceUrl;

    public InventoryServiceClient(HttpClient httpClient, ILogger<InventoryServiceClient> logger)
    {
        _httpClient = httpClient;
        _logger = logger;
        _inventoryServiceUrl = Environment.GetEnvironmentVariable("INVENTORY_ADDR") ?? "http://inventory:8080";
    }

    public async Task<InventoryItemResponse?> GetInventoryAsync(string productId)
    {
        using var activity = Activity.Current?.Source.StartActivity("GetInventory");
        activity?.SetTag("inventory.product_id", productId);

        try
        {
            var url = $"{_inventoryServiceUrl}/api/v1/inventory/{productId}";
            _logger.LogInformation("调用库存服务获取商品信息: {ProductId}", productId);

            var response = await _httpClient.GetAsync(url);
            
            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();
                var inventory = JsonSerializer.Deserialize<InventoryItemResponse>(content, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });
                
                _logger.LogInformation("成功获取商品 {ProductId} 的库存信息", productId);
                return inventory;
            }
            else
            {
                _logger.LogWarning("获取商品 {ProductId} 库存信息失败，状态码: {StatusCode}", productId, response.StatusCode);
                return null;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "调用库存服务失败: {ProductId}", productId);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return null;
        }
    }

    public async Task<Dictionary<string, bool>> CheckAvailabilityAsync(List<CartItemDto> items)
    {
        using var activity = Activity.Current?.Source.StartActivity("CheckAvailability");
        activity?.SetTag("inventory.items_count", items.Count);

        try
        {
            var url = $"{_inventoryServiceUrl}/api/v1/inventory/check-availability";
            _logger.LogInformation("检查 {Count} 个商品的可用性", items.Count);

            var request = new CheckAvailabilityRequest { Items = items };
            var json = JsonSerializer.Serialize(request, new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase
            });
            var content = new StringContent(json, Encoding.UTF8, "application/json");

            var response = await _httpClient.PostAsync(url, content);

            if (response.IsSuccessStatusCode)
            {
                var responseContent = await response.Content.ReadAsStringAsync();
                var result = JsonSerializer.Deserialize<CheckAvailabilityResponse>(responseContent, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                return result?.Availability ?? new Dictionary<string, bool>();
            }
            else
            {
                _logger.LogWarning("检查商品可用性失败，状态码: {StatusCode}", response.StatusCode);
                return new Dictionary<string, bool>();
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "检查商品可用性失败");
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return new Dictionary<string, bool>();
        }
    }

    public async Task<bool> ReserveInventoryAsync(List<CartItemDto> items, string reservationId)
    {
        using var activity = Activity.Current?.Source.StartActivity("ReserveInventory");
        activity?.SetTag("inventory.reservation_id", reservationId);
        activity?.SetTag("inventory.items_count", items.Count);

        try
        {
            var url = $"{_inventoryServiceUrl}/api/v1/inventory/reserve?reservationId={reservationId}";
            _logger.LogInformation("预留库存: 预留ID={ReservationId}, 商品数量={Count}", reservationId, items.Count);

            var json = JsonSerializer.Serialize(items, new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase
            });
            var content = new StringContent(json, Encoding.UTF8, "application/json");

            var response = await _httpClient.PostAsync(url, content);

            if (response.IsSuccessStatusCode)
            {
                var responseContent = await response.Content.ReadAsStringAsync();
                var result = JsonSerializer.Deserialize<ReservationResponse>(responseContent, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                bool success = result?.Success ?? false;
                _logger.LogInformation("库存预留结果: {Success}", success ? "成功" : "失败");
                return success;
            }
            else
            {
                _logger.LogWarning("预留库存失败，状态码: {StatusCode}", response.StatusCode);
                return false;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "预留库存失败: {ReservationId}", reservationId);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return false;
        }
    }

    public async Task<bool> ReleaseInventoryAsync(string reservationId)
    {
        using var activity = Activity.Current?.Source.StartActivity("ReleaseInventory");
        activity?.SetTag("inventory.reservation_id", reservationId);

        try
        {
            var url = $"{_inventoryServiceUrl}/api/v1/inventory/release?reservationId={reservationId}";
            _logger.LogInformation("释放库存: 预留ID={ReservationId}", reservationId);

            var response = await _httpClient.PostAsync(url, null);

            if (response.IsSuccessStatusCode)
            {
                var responseContent = await response.Content.ReadAsStringAsync();
                var result = JsonSerializer.Deserialize<ReservationResponse>(responseContent, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                bool success = result?.Success ?? false;
                _logger.LogInformation("库存释放结果: {Success}", success ? "成功" : "失败");
                return success;
            }
            else
            {
                _logger.LogWarning("释放库存失败，状态码: {StatusCode}", response.StatusCode);
                return false;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "释放库存失败: {ReservationId}", reservationId);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return false;
        }
    }
}

// DTO类定义
public class InventoryItemResponse
{
    public string ProductId { get; set; } = string.Empty;
    public int AvailableQuantity { get; set; }
    public int ReservedQuantity { get; set; }
    public int TotalQuantity { get; set; }
    public string WarehouseLocation { get; set; } = string.Empty;
    public long LastUpdatedTimestamp { get; set; }
}

public class CartItemDto
{
    public string ProductId { get; set; } = string.Empty;
    public int Quantity { get; set; }
}

public class CheckAvailabilityRequest
{
    public List<CartItemDto> Items { get; set; } = new();
}

public class CheckAvailabilityResponse
{
    public Dictionary<string, bool> Availability { get; set; } = new();
    public bool AllAvailable { get; set; }
}

public class ReservationResponse
{
    public bool Success { get; set; }
    public string ReservationId { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
} 