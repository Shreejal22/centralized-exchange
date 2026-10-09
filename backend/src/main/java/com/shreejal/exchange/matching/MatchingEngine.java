package com.shreejal.exchange.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public class MatchingEngine {

    private final OrderBook orderBook;

    private final Map<UUID, MatchingOrder> orderIndex =
            new HashMap<>();

    private final Set<UUID> seenOrderIds =
            new HashSet<>();

    public MatchingEngine(String symbol) {
        this.orderBook = new OrderBook(symbol);
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    public MatchingResult submit(MatchingOrder order) {

        validateOrder(order);

        if (!seenOrderIds.add(order.getOrderId())) {
            throw new IllegalArgumentException(
                    "Duplicate order ID: " + order.getOrderId()
            );
        }

        List<Trade> trades =
                new ArrayList<>();

        if (order.getTimeInForce() == TimeInForce.POST_ONLY) {

            if (wouldCross(order)) {
                order.setStatus(OrderStatus.REJECTED);

                throw new IllegalArgumentException(
                        "POST_ONLY order would take liquidity"
                );
            }

            addToOrderBook(order);

            return new MatchingResult(
                    trades,
                    order
            );
        }

        if (order.getTimeInForce() == TimeInForce.FOK) {

            boolean canFill;

            if (order.getSide() == OrderSide.BUY) {
                canFill = canFullyMatchBuy(order);
            } else {
                canFill = canFullyMatchSell(order);
            }

            if (!canFill) {
                order.setStatus(OrderStatus.REJECTED);

                return new MatchingResult(
                        trades,
                        order
                );
            }
        }

        if (order.getSide() == OrderSide.BUY) {
            matchBuyOrder(order, trades);
        }

        if (order.getSide() == OrderSide.SELL) {
            matchSellOrder(order, trades);
        }

        if (order.getRemainingQuantity()
                .compareTo(BigDecimal.ZERO) > 0) {

            if (order.getTimeInForce() == TimeInForce.GTC) {

                if (order.getType() == OrderType.LIMIT) {
                    addToOrderBook(order);
                }

            } else if (order.getTimeInForce() == TimeInForce.IOC) {

                order.cancelRemainingQuantity();
                order.setStatus(OrderStatus.CANCELLED);

            } else if (order.getTimeInForce() == TimeInForce.FOK) {

                throw new IllegalStateException(
                        "FOK order was not completely filled"
                );
            }
        }

        return new MatchingResult(
                trades,
                order
        );
    }

    private void matchBuyOrder(
            MatchingOrder incomingOrder,
            List<Trade> trades
    ) {

        while (
                incomingOrder.getRemainingQuantity()
                        .compareTo(BigDecimal.ZERO) > 0
        ) {

            PriceLevel bestAsk =
                    orderBook.getBestAsk();

            if (bestAsk == null) {
                break;
            }

            if (incomingOrder.getType() == OrderType.LIMIT &&
                    incomingOrder.getPrice()
                            .compareTo(bestAsk.getPrice()) < 0) {

                break;
            }

            MatchingOrder restingOrder =
                    bestAsk.peekFirstOrder();

            BigDecimal tradeQuantity =
                    incomingOrder.getRemainingQuantity()
                            .min(
                                    restingOrder.getRemainingQuantity()
                            );

            BigDecimal tradePrice =
                    restingOrder.getPrice();

            Trade trade = new Trade(
                    UUID.randomUUID(),
                    incomingOrder.getOrderId(),
                    restingOrder.getOrderId(),
                    tradePrice,
                    tradeQuantity,
                    Instant.now()
            );

            trades.add(trade);

            incomingOrder.reduceRemainingQuantity(
                    tradeQuantity
            );

            restingOrder.reduceRemainingQuantity(
                    tradeQuantity
            );

            updateStatus(incomingOrder);
            updateStatus(restingOrder);

            if (restingOrder.getRemainingQuantity()
                    .compareTo(BigDecimal.ZERO) == 0) {

                bestAsk.removeFirstOrder();

                orderIndex.remove(
                        restingOrder.getOrderId()
                );

                orderBook.removeEmptyAskLevel();
            }
        }

        updateStatus(incomingOrder);
    }

    private void matchSellOrder(
            MatchingOrder incomingOrder,
            List<Trade> trades
    ) {

        while (
                incomingOrder.getRemainingQuantity()
                        .compareTo(BigDecimal.ZERO) > 0
        ) {

            PriceLevel bestBid =
                    orderBook.getBestBid();

            if (bestBid == null) {
                break;
            }

            if (incomingOrder.getType() == OrderType.LIMIT &&
                    incomingOrder.getPrice()
                            .compareTo(bestBid.getPrice()) > 0) {

                break;
            }

            MatchingOrder restingOrder =
                    bestBid.peekFirstOrder();

            BigDecimal tradeQuantity =
                    incomingOrder.getRemainingQuantity()
                            .min(
                                    restingOrder.getRemainingQuantity()
                            );

            BigDecimal tradePrice =
                    restingOrder.getPrice();

            Trade trade = new Trade(
                    UUID.randomUUID(),
                    restingOrder.getOrderId(),
                    incomingOrder.getOrderId(),
                    tradePrice,
                    tradeQuantity,
                    Instant.now()
            );

            trades.add(trade);

            incomingOrder.reduceRemainingQuantity(
                    tradeQuantity
            );

            restingOrder.reduceRemainingQuantity(
                    tradeQuantity
            );

            updateStatus(incomingOrder);
            updateStatus(restingOrder);

            if (restingOrder.getRemainingQuantity()
                    .compareTo(BigDecimal.ZERO) == 0) {

                bestBid.removeFirstOrder();

                orderIndex.remove(
                        restingOrder.getOrderId()
                );

                orderBook.removeEmptyBidLevel();
            }
        }

        updateStatus(incomingOrder);
    }

    private boolean canFullyMatchBuy(
            MatchingOrder order
    ) {

        BigDecimal required =
                order.getRemainingQuantity();

        for (var entry : orderBook.getAsks().entrySet()) {

            BigDecimal price =
                    entry.getKey();

            if (order.getType() == OrderType.LIMIT &&
                    order.getPrice()
                            .compareTo(price) < 0) {

                break;
            }

            BigDecimal available =
                    entry.getValue()
                            .getTotalRemainingQuantity();

            required =
                    required.subtract(available);

            if (required.compareTo(BigDecimal.ZERO) <= 0) {
                return true;
            }
        }

        return false;
    }

    private boolean canFullyMatchSell(
            MatchingOrder order
    ) {

        BigDecimal required =
                order.getRemainingQuantity();

        for (var entry : orderBook.getBids().entrySet()) {

            BigDecimal price =
                    entry.getKey();

            if (order.getType() == OrderType.LIMIT &&
                    order.getPrice()
                            .compareTo(price) > 0) {

                break;
            }

            BigDecimal available =
                    entry.getValue()
                            .getTotalRemainingQuantity();

            required =
                    required.subtract(available);

            if (required.compareTo(BigDecimal.ZERO) <= 0) {
                return true;
            }
        }

        return false;
    }

    private boolean wouldCross(
            MatchingOrder order
    ) {

        if (order.getSide() == OrderSide.BUY) {

            PriceLevel bestAsk =
                    orderBook.getBestAsk();

            if (bestAsk == null) {
                return false;
            }

            return order.getPrice()
                    .compareTo(bestAsk.getPrice()) >= 0;
        }

        PriceLevel bestBid =
                orderBook.getBestBid();

        if (bestBid == null) {
            return false;
        }

        return order.getPrice()
                .compareTo(bestBid.getPrice()) <= 0;
    }

    private void addToOrderBook(
            MatchingOrder order
    ) {

        orderBook.addOrder(order);

        orderIndex.put(
                order.getOrderId(),
                order
        );
    }

    public void cancel(UUID orderId) {

        MatchingOrder order =
                orderIndex.get(orderId);

        if (order == null) {
            throw new IllegalArgumentException(
                    "Order not found: " + orderId
            );
        }

        boolean removed =
                orderBook.removeOrder(order);

        if (!removed) {
            throw new IllegalStateException(
                    "Order was not present in order book"
            );
        }

        order.cancelRemainingQuantity();
        order.setStatus(OrderStatus.CANCELLED);

        orderIndex.remove(orderId);
    }

    private void validateOrder(MatchingOrder order) {

        if (order == null) {
            throw new IllegalArgumentException(
                    "Order cannot be null"
            );
        }

        if (order.getOrderId() == null) {
            throw new IllegalArgumentException(
                    "Order ID cannot be null"
            );
        }

        if (order.getUserId() == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        if (order.getSymbol() == null ||
                !order.getSymbol().equals(orderBook.getSymbol())) {

            throw new IllegalArgumentException(
                    "Order symbol does not match order book symbol"
            );
        }

        if (order.getSide() == null) {
            throw new IllegalArgumentException(
                    "Order side cannot be null"
            );
        }

        if (order.getType() == null) {
            throw new IllegalArgumentException(
                    "Order type cannot be null"
            );
        }

        if (order.getTimeInForce() == null) {
            throw new IllegalArgumentException(
                    "Time in force cannot be null"
            );
        }

        if (order.getQuantity() == null ||
                order.getQuantity()
                        .compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        if (order.getType() == OrderType.LIMIT) {

            if (order.getPrice() == null ||
                    order.getPrice()
                            .compareTo(BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException(
                        "LIMIT order price must be greater than zero"
                );
            }

        } else if (order.getType() == OrderType.MARKET) {

            if (order.getPrice() != null) {
                throw new IllegalArgumentException(
                        "MARKET order cannot have a price"
                );
            }
        }

        if (order.getTimeInForce() == TimeInForce.POST_ONLY &&
                order.getType() != OrderType.LIMIT) {

            throw new IllegalArgumentException(
                    "POST_ONLY orders must be LIMIT orders"
            );
        }
    }

    private void updateStatus(MatchingOrder order) {

        if (order.getRemainingQuantity()
                .compareTo(BigDecimal.ZERO) == 0) {

            order.setStatus(OrderStatus.FILLED);

        } else if (order.getRemainingQuantity()
                .compareTo(order.getQuantity()) < 0) {

            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }
}