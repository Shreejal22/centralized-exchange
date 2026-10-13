
package com.shreejal.exchange.matching;

import com.shreejal.exchange.model.Order;

import java.math.BigDecimal;

public final class OrderMatchingMapper {

    private OrderMatchingMapper() {
    }

    public static MatchingOrder toMatchingOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException(
                    "Order cannot be null"
            );
        }

        if (order.getId() == null) {
            throw new IllegalArgumentException(
                    "Order must be persisted before mapping"
            );
        }

        if (order.getMatchingId() == null) {
            throw new IllegalArgumentException(
                    "Order matching ID cannot be null"
            );
        }

        if (order.getUser() == null
                || order.getUser().getId() == null) {
            throw new IllegalArgumentException(
                    "Order must have a persisted user"
            );
        }

        if (order.getTradingPair() == null
                || order.getTradingPair().getSymbol() == null) {
            throw new IllegalArgumentException(
                    "Order must have a trading pair"
            );
        }

        if (order.getFilledQuantity() == null
                || order.getFilledQuantity()
                .compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "This mapper only supports new, unfilled orders"
            );
        }

        return new MatchingOrder(
                order.getUser().getId(),
                order.getMatchingId(),
                order.getTradingPair().getSymbol(),
                OrderSide.valueOf(order.getSide()),
                OrderType.valueOf(order.getOrderType()),
                order.getPrice(),
                order.getQuantity(),
                TimeInForce.valueOf(order.getTimeInForce()),
                order.getId()
        );
    }

    public static MatchingOrder toRestingMatchingOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted order is required"
            );
        }

        if (order.getMatchingId() == null
                || order.getUser() == null
                || order.getTradingPair() == null) {
            throw new IllegalArgumentException(
                    "Order identity, user, and trading pair are required"
            );
        }

        BigDecimal remaining =
                order.getQuantity().subtract(order.getFilledQuantity());

        if (remaining.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A fully filled order cannot be restored"
            );
        }

        return new MatchingOrder(
                order.getUser().getId(),
                order.getMatchingId(),
                order.getTradingPair().getSymbol(),
                OrderSide.valueOf(order.getSide()),
                OrderType.valueOf(order.getOrderType()),
                order.getPrice(),
                order.getQuantity(),
                remaining,
                TimeInForce.valueOf(order.getTimeInForce()),
                order.getId()
        );
    }

}
