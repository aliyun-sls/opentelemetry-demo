package com.otel.demo.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class CheckAvailabilityRequest {

    @NotEmpty(message = "商品列表不能为空")
    @Valid
    private List<CartItemDto> items;

    public CheckAvailabilityRequest() {}

    public CheckAvailabilityRequest(List<CartItemDto> items) {
        this.items = items;
    }

    public List<CartItemDto> getItems() {
        return items;
    }

    public void setItems(List<CartItemDto> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "CheckAvailabilityRequest{" +
                "items=" + items +
                '}';
    }
} 