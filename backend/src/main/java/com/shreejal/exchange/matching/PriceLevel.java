package com.shreejal.exchange.matching;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PriceLevel {

    private final BigDecimal price;
    private final ArrayDeque<MatchingOrder> orders = new ArrayDeque<>();

    public PriceLevel(BigDecimal price) {
        this.price = price;
    }

    public void addOrder(MatchingOrder order) {
        orders.addLast(order);
    }

    public MatchingOrder peekFirstOrder() {
        return orders.peekFirst();
    }

    public MatchingOrder removeFirstOrder() {
        return orders.removeFirst();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public BigDecimal getPrice()     {
        return price;
    }
    public BigDecimal getTotalRemainingQuantity() {

        BigDecimal total = BigDecimal.ZERO;

        for (MatchingOrder order : orders) {
            total = total.add(
                    order.getRemainingQuantity()
            );
        }

        return total;
    }
    public boolean removeOrder(MatchingOrder order) {
        return orders.remove(order);
    }
}