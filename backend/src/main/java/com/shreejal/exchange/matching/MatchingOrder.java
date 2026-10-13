
package com.shreejal.exchange.matching;

import java.math.BigDecimal;
import java.util.UUID;

public class MatchingOrder {

    private final Long userId;
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


    public MatchingOrder(
            Long userId,
            UUID orderId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            TimeInForce timeInForce,
            long sequenceNumber
    ) {
        this(
                userId,
                orderId,
                symbol,
                side,
                type,
                price,
                quantity,
                quantity,
                timeInForce,
                sequenceNumber
        );
    }

    public MatchingOrder(
            Long userId,
            UUID orderId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal remainingQuantity,
            TimeInForce timeInForce,
            long sequenceNumber
    ) {
        this.userId = userId;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.timeInForce = timeInForce;
        this.sequenceNumber = sequenceNumber;
        this.status = OrderStatus.NEW;


    }


    public Long getUserId() {
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

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void reduceRemainingQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException(
                    "Reduction quantity cannot be null"
            );
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Reduction quantity must be greater than zero"
            );
        }

        if (remainingQuantity == null) {
            throw new IllegalStateException(
                    "Remaining quantity has not been initialized"
            );
        }

        if (quantity.compareTo(remainingQuantity) > 0) {
            throw new IllegalArgumentException(
                    "Reduction quantity cannot exceed remaining quantity"
            );
        }

        remainingQuantity = remainingQuantity.subtract(quantity);
    }

    public void cancelRemainingQuantity() {
        remainingQuantity = BigDecimal.ZERO;
    }
    public MatchingOrder copy() {
        MatchingOrder copy = new MatchingOrder(
                userId,
                orderId,
                symbol,
                side,
                type,
                price,
                quantity,
                timeInForce,
                sequenceNumber
        );

        BigDecimal filledQuantity =
                quantity.subtract(remainingQuantity);

        if (filledQuantity.signum() > 0) {
            copy.reduceRemainingQuantity(filledQuantity);
        }

        copy.setStatus(status);

        return copy;
    }
}
