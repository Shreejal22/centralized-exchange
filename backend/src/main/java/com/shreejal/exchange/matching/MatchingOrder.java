package com.shreejal.exchange.matching;

import java.util.UUID;

import java.math.BigDecimal;

public class MatchingOrder {
    private final UUID userId;
    private final UUID orderId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType type;
    private final BigDecimal price;
    private final BigDecimal quantity;



    private BigDecimal remainingQuantity;
    private final TimeInForce timeInForce;

    private final long sequenceNumber;
    private OrderStatus status;

    public MatchingOrder(UUID userId, UUID orderId, String symbol, OrderSide side, OrderType type, BigDecimal price, BigDecimal quantity, TimeInForce timeInForce, long sequenceNumber) {
        this.userId = userId;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timeInForce = timeInForce;
        this.sequenceNumber = sequenceNumber;
        this.status = OrderStatus.NEW;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void reduceRemainingQuantity(BigDecimal quantity) {
        this.remainingQuantity = remainingQuantity.subtract(quantity);
    }
    public void cancelRemainingQuantity() {
        this.remainingQuantity = BigDecimal.ZERO;
    }
    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
