package com.fss.backend.sales.order;

import java.util.List;

public record OrderDetail(Order order, List<OrderItem> items) {}
