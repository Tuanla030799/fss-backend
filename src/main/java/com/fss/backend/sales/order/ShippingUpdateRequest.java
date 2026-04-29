package com.fss.backend.sales.order;

public record ShippingUpdateRequest(String shippingMethod, String shippingStatus, String trackingCode) {}
