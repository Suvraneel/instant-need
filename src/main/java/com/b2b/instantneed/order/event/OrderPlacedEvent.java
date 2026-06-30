package com.b2b.instantneed.order.event;

import java.util.UUID;

public record OrderPlacedEvent(UUID orderId) {}
