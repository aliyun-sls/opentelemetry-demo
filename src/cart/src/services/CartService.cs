// Copyright The OpenTelemetry Authors
// SPDX-License-Identifier: Apache-2.0
using System.Diagnostics;
using System.Threading.Tasks;
using System;
using Grpc.Core;
using cart.cartstore;
using OpenFeature;
using Oteldemo;
using Microsoft.Extensions.Logging;
using System.Collections.Generic;
using System.Linq;

namespace cart.services;

public class CartService : Oteldemo.CartService.CartServiceBase
{
    private static readonly Empty Empty = new();
    private readonly Random random = new Random();
    private readonly ICartStore _badCartStore;
    private readonly ICartStore _cartStore;
    private readonly IFeatureClient _featureFlagHelper;
    private readonly InventoryServiceClient _inventoryServiceClient;
    private readonly ILogger<CartService> _logger;

    public CartService(ICartStore cartStore, ICartStore badCartStore, IFeatureClient featureFlagService, 
                      InventoryServiceClient inventoryServiceClient, ILogger<CartService> logger)
    {
        _badCartStore = badCartStore;
        _cartStore = cartStore;
        _featureFlagHelper = featureFlagService;
        _inventoryServiceClient = inventoryServiceClient;
        _logger = logger;
    }

    public override async Task<Empty> AddItem(AddItemRequest request, ServerCallContext context)
    {
        var activity = Activity.Current;
        activity?.SetTag("app.user.id", request.UserId);
        activity?.SetTag("app.product.id", request.Item.ProductId);
        activity?.SetTag("app.product.quantity", request.Item.Quantity);

        try
        {
            // 1. 检查库存是否充足
            var inventory = await _inventoryServiceClient.GetInventoryAsync(request.Item.ProductId);
            if (inventory == null)
            {
                _logger.LogWarning("商品 {ProductId} 不存在", request.Item.ProductId);
                throw new RpcException(new Status(StatusCode.NotFound, $"商品 {request.Item.ProductId} 不存在"));
            }

            if (inventory.AvailableQuantity < request.Item.Quantity)
            {
                _logger.LogWarning("商品 {ProductId} 库存不足，可用: {Available}, 需要: {Required}", 
                                 request.Item.ProductId, inventory.AvailableQuantity, request.Item.Quantity);
                throw new RpcException(new Status(StatusCode.FailedPrecondition, 
                    $"商品 {request.Item.ProductId} 库存不足，可用库存: {inventory.AvailableQuantity}"));
            }

            // 2. 库存充足，添加到购物车
            await _cartStore.AddItemAsync(request.UserId, request.Item.ProductId, request.Item.Quantity);
            
            _logger.LogInformation("商品 {ProductId} 成功添加到用户 {UserId} 的购物车", 
                                 request.Item.ProductId, request.UserId);

            return Empty;
        }
        catch (RpcException ex)
        {
            activity?.AddException(ex);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "添加商品到购物车失败: UserId={UserId}, ProductId={ProductId}", 
                           request.UserId, request.Item.ProductId);
            activity?.AddException(ex);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            throw new RpcException(new Status(StatusCode.Internal, "添加商品到购物车失败"));
        }
    }

    public override async Task<Cart> GetCart(GetCartRequest request, ServerCallContext context)
    {
        var activity = Activity.Current;
        activity?.SetTag("app.user.id", request.UserId);
        activity?.AddEvent(new("Fetch cart"));

        try
        {
            var cart = await _cartStore.GetCartAsync(request.UserId);
            var totalCart = 0;
            foreach (var item in cart.Items)
            {
                totalCart += item.Quantity;
            }
            activity?.SetTag("app.cart.items.count", totalCart);

            return cart;
        }
        catch (RpcException ex)
        {
            activity?.AddException(ex);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            throw;
        }
    }

    public override async Task<Empty> EmptyCart(EmptyCartRequest request, ServerCallContext context)
    {
        var activity = Activity.Current;
        activity?.SetTag("app.user.id", request.UserId);
        activity?.AddEvent(new("Empty cart"));

        try
        {
            if (await _featureFlagHelper.GetBooleanValueAsync("cartFailure", false))
            {
                await _badCartStore.EmptyCartAsync(request.UserId);
            }
            else
            {
                await _cartStore.EmptyCartAsync(request.UserId);
            }
        }
        catch (RpcException ex)
        {
            Activity.Current?.AddException(ex);
            Activity.Current?.SetStatus(ActivityStatusCode.Error, ex.Message);
            throw;
        }

        return Empty;
    }

    /// <summary>
    /// 检查购物车中所有商品的库存可用性
    /// </summary>
    public async Task<bool> CheckCartInventoryAvailabilityAsync(string userId)
    {
        var activity = Activity.Current;
        activity?.SetTag("app.user.id", userId);
        activity?.AddEvent(new("Check cart inventory availability"));

        try
        {
            // 1. 获取购物车
            var cart = await _cartStore.GetCartAsync(userId);
            if (cart.Items.Count == 0)
            {
                _logger.LogInformation("用户 {UserId} 的购物车为空", userId);
                return true;
            }

            // 2. 转换为库存服务需要的格式
            var cartItems = cart.Items.Select(item => new CartItemDto
            {
                ProductId = item.ProductId,
                Quantity = item.Quantity
            }).ToList();

            // 3. 检查库存可用性
            var availability = await _inventoryServiceClient.CheckAvailabilityAsync(cartItems);
            
            bool allAvailable = availability.Values.All(available => available);
            
            if (!allAvailable)
            {
                _logger.LogWarning("用户 {UserId} 的购物车中有商品库存不足", userId);
                
                // 记录哪些商品不可用
                foreach (var item in availability.Where(kv => !kv.Value))
                {
                    _logger.LogWarning("商品 {ProductId} 库存不足", item.Key);
                }
            }
            else
            {
                _logger.LogInformation("用户 {UserId} 的购物车中所有商品库存充足", userId);
            }

            activity?.SetTag("app.cart.all_available", allAvailable);
            return allAvailable;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "检查购物车库存可用性失败: UserId={UserId}", userId);
            activity?.AddException(ex);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return false;
        }
    }

    /// <summary>
    /// 为购物车预留库存
    /// </summary>
    public async Task<string?> ReserveCartInventoryAsync(string userId)
    {
        var activity = Activity.Current;
        activity?.SetTag("app.user.id", userId);
        activity?.AddEvent(new("Reserve cart inventory"));

        try
        {
            // 1. 获取购物车
            var cart = await _cartStore.GetCartAsync(userId);
            if (cart.Items.Count == 0)
            {
                _logger.LogWarning("用户 {UserId} 的购物车为空，无法预留库存", userId);
                return null;
            }

            // 2. 转换为库存服务需要的格式
            var cartItems = cart.Items.Select(item => new CartItemDto
            {
                ProductId = item.ProductId,
                Quantity = item.Quantity
            }).ToList();

            // 3. 生成预留ID
            var reservationId = $"cart_{userId}_{Guid.NewGuid():N}";

            // 4. 预留库存
            bool success = await _inventoryServiceClient.ReserveInventoryAsync(cartItems, reservationId);
            
            if (success)
            {
                _logger.LogInformation("用户 {UserId} 的购物车库存预留成功，预留ID: {ReservationId}", userId, reservationId);
                activity?.SetTag("app.reservation.id", reservationId);
                return reservationId;
            }
            else
            {
                _logger.LogWarning("用户 {UserId} 的购物车库存预留失败", userId);
                return null;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "预留购物车库存失败: UserId={UserId}", userId);
            activity?.AddException(ex);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return null;
        }
    }

    /// <summary>
    /// 释放库存预留
    /// </summary>
    public async Task<bool> ReleaseInventoryReservationAsync(string reservationId)
    {
        var activity = Activity.Current;
        activity?.SetTag("app.reservation.id", reservationId);
        activity?.AddEvent(new("Release inventory reservation"));

        try
        {
            bool success = await _inventoryServiceClient.ReleaseInventoryAsync(reservationId);
            
            if (success)
            {
                _logger.LogInformation("库存预留 {ReservationId} 释放成功", reservationId);
            }
            else
            {
                _logger.LogWarning("库存预留 {ReservationId} 释放失败", reservationId);
            }

            return success;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "释放库存预留失败: ReservationId={ReservationId}", reservationId);
            activity?.AddException(ex);
            activity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            return false;
        }
    }
}
