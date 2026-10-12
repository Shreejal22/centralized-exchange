
        package com.shreejal.exchange.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchingEngineTest {

    private static final String SYMBOL = "BTC/USDT";

    private MatchingEngine newEngine() {
        return new MatchingEngine(SYMBOL);
    }

    private MatchingOrder order(
            Long userId,
            UUID orderId,
            String symbol,
            OrderSide side,
            OrderType type,
            String price,
            String quantity,
            TimeInForce timeInForce,
            long sequenceNumber
    ) {
        return new MatchingOrder(
                userId,
                orderId,
                symbol,
                side,
                type,
                price == null ? null : new BigDecimal(price),
                new BigDecimal(quantity),
                timeInForce,
                sequenceNumber
        );
    }

    private MatchingOrder order(
            OrderSide side,
            OrderType type,
            String price,
            String quantity,
            TimeInForce timeInForce,
            long sequenceNumber
    ) {
        return order(
                1L,
                UUID.randomUUID(),
                SYMBOL,
                side,
                type,
                price,
                quantity,
                timeInForce,
                sequenceNumber
        );
    }

    private MatchingOrder limitOrder(
            OrderSide side,
            String price,
            String quantity,
            long sequenceNumber
    ) {
        return order(
                side,
                OrderType.LIMIT,
                price,
                quantity,
                TimeInForce.GTC,
                sequenceNumber
        );
    }

    @Test
    void engineShouldStartWithCorrectOrderBook() {
        MatchingEngine engine = newEngine();

        assertEquals(SYMBOL, engine.getOrderBook().getSymbol());
    }

    @Test
    void limitOrderShouldBeAddedToOrderBook() {
        MatchingEngine engine = newEngine();
        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "100", "1", 1
        );

        engine.submit(buy);

        assertNotNull(engine.getOrderBook().getBestBid());
        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook().getBestBid().getPrice()
        );
        assertEquals(OrderStatus.NEW, buy.getStatus());
    }

    @Test
    void buyOrderShouldMatchExistingSellOrder() {
        MatchingEngine engine = newEngine();
        MatchingOrder sell = limitOrder(
                OrderSide.SELL, "100", "1", 1
        );
        engine.getOrderBook().addOrder(sell);

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "100", "1", 2
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(1, result.getTrades().size());

        Trade trade = result.getTrades().get(0);
        assertEquals(new BigDecimal("100"), trade.getPrice());
        assertEquals(new BigDecimal("1"), trade.getQuantity());
        assertEquals(sell.getOrderId(), trade.getSellOrderId());
        assertEquals(buy.getOrderId(), trade.getBuyOrderId());

        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(BigDecimal.ZERO, sell.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(OrderStatus.FILLED, sell.getStatus());
    }

    @Test
    void buyOrderShouldPartiallyFillExistingSellOrder() {
        MatchingEngine engine = newEngine();
        MatchingOrder sell = limitOrder(
                OrderSide.SELL, "100", "4", 1
        );
        engine.getOrderBook().addOrder(sell);

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "100", "10", 2
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(1, result.getTrades().size());
        assertEquals(
                new BigDecimal("4"),
                result.getTrades().get(0).getQuantity()
        );
        assertEquals(
                new BigDecimal("6"),
                buy.getRemainingQuantity()
        );
        assertEquals(BigDecimal.ZERO, sell.getRemainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(OrderStatus.FILLED, sell.getStatus());
    }

    @Test
    void buyOrderShouldMatchAcrossMultiplePriceLevels() {
        MatchingEngine engine = newEngine();

        MatchingOrder sell100 = limitOrder(
                OrderSide.SELL, "100", "2", 1
        );
        MatchingOrder sell101 = limitOrder(
                OrderSide.SELL, "101", "3", 2
        );

        engine.getOrderBook().addOrder(sell100);
        engine.getOrderBook().addOrder(sell101);

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "101", "5", 3
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(2, result.getTrades().size());
        assertEquals(
                new BigDecimal("100"),
                result.getTrades().get(0).getPrice()
        );
        assertEquals(
                new BigDecimal("2"),
                result.getTrades().get(0).getQuantity()
        );
        assertEquals(
                new BigDecimal("101"),
                result.getTrades().get(1).getPrice()
        );
        assertEquals(
                new BigDecimal("3"),
                result.getTrades().get(1).getQuantity()
        );
        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
    }

    @Test
    void bestPriceShouldBeMatchedFirst() {
        MatchingEngine engine = newEngine();

        MatchingOrder sell105 = limitOrder(
                OrderSide.SELL, "105", "1", 1
        );
        MatchingOrder sell100 = limitOrder(
                OrderSide.SELL, "100", "1", 2
        );
        MatchingOrder sell103 = limitOrder(
                OrderSide.SELL, "103", "1", 3
        );

        engine.getOrderBook().addOrder(sell105);
        engine.getOrderBook().addOrder(sell100);
        engine.getOrderBook().addOrder(sell103);

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "105", "3", 4
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(3, result.getTrades().size());
        assertEquals(
                new BigDecimal("100"),
                result.getTrades().get(0).getPrice()
        );
        assertEquals(
                new BigDecimal("103"),
                result.getTrades().get(1).getPrice()
        );
        assertEquals(
                new BigDecimal("105"),
                result.getTrades().get(2).getPrice()
        );
    }

    @Test
    void olderOrderAtSamePriceShouldMatchFirst() {
        MatchingEngine engine = newEngine();

        MatchingOrder firstSell = limitOrder(
                OrderSide.SELL, "100", "1", 1
        );
        MatchingOrder secondSell = limitOrder(
                OrderSide.SELL, "100", "1", 2
        );

        engine.getOrderBook().addOrder(firstSell);
        engine.getOrderBook().addOrder(secondSell);

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "100", "1", 3
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(
                firstSell.getOrderId(),
                result.getTrades().get(0).getSellOrderId()
        );
    }

    @Test
    void limitBuyBelowBestAskShouldNotMatch() {
        MatchingEngine engine = newEngine();
        MatchingOrder sell = limitOrder(
                OrderSide.SELL, "100", "1", 1
        );
        engine.getOrderBook().addOrder(sell);

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "99", "1", 2
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(0, result.getTrades().size());
        assertEquals(
                new BigDecimal("99"),
                engine.getOrderBook().getBestBid().getPrice()
        );
        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook().getBestAsk().getPrice()
        );
    }

    @Test
    void sellOrderShouldMatchExistingBuyOrder() {
        MatchingEngine engine = newEngine();

        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "100", "2", 1
        );
        engine.getOrderBook().addOrder(buy);

        MatchingOrder sell = limitOrder(
                OrderSide.SELL, "100", "2", 2
        );

        MatchingResult result = engine.submit(sell);

        assertEquals(1, result.getTrades().size());
        Trade trade = result.getTrades().get(0);

        assertEquals(buy.getOrderId(), trade.getBuyOrderId());
        assertEquals(sell.getOrderId(), trade.getSellOrderId());
        assertEquals(new BigDecimal("100"), trade.getPrice());
        assertEquals(new BigDecimal("2"), trade.getQuantity());
        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(BigDecimal.ZERO, sell.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(OrderStatus.FILLED, sell.getStatus());
    }

    @Test
    void marketBuyShouldMatchAvailableLiquidity() {
        MatchingEngine engine = newEngine();

        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "100", "2", 1)
        );
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "101", "3", 2)
        );

        MatchingOrder marketBuy = order(
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                "5",
                TimeInForce.GTC,
                3
        );

        MatchingResult result = engine.submit(marketBuy);

        assertEquals(2, result.getTrades().size());
        assertEquals(
                new BigDecimal("100"),
                result.getTrades().get(0).getPrice()
        );
        assertEquals(
                new BigDecimal("2"),
                result.getTrades().get(0).getQuantity()
        );
        assertEquals(
                new BigDecimal("101"),
                result.getTrades().get(1).getPrice()
        );
        assertEquals(
                new BigDecimal("3"),
                result.getTrades().get(1).getQuantity()
        );
        assertEquals(BigDecimal.ZERO, marketBuy.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, marketBuy.getStatus());
    }

    @Test
    void marketBuyShouldStopWhenLiquidityRunsOut() {
        MatchingEngine engine = newEngine();
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "100", "2", 1)
        );

        MatchingOrder marketBuy = order(
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                "5",
                TimeInForce.GTC,
                2
        );

        MatchingResult result = engine.submit(marketBuy);

        assertEquals(1, result.getTrades().size());
        assertEquals(
                new BigDecimal("2"),
                result.getTrades().get(0).getQuantity()
        );
        assertEquals(
                new BigDecimal("3"),
                marketBuy.getRemainingQuantity()
        );
    }

    @Test
    void marketSellShouldMatchAvailableLiquidity() {
        MatchingEngine engine = newEngine();

        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.BUY, "101", "2", 1)
        );
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.BUY, "100", "3", 2)
        );

        MatchingOrder marketSell = order(
                OrderSide.SELL,
                OrderType.MARKET,
                null,
                "5",
                TimeInForce.GTC,
                3
        );

        MatchingResult result = engine.submit(marketSell);

        assertEquals(2, result.getTrades().size());
        assertEquals(
                new BigDecimal("101"),
                result.getTrades().get(0).getPrice()
        );
        assertEquals(
                new BigDecimal("2"),
                result.getTrades().get(0).getQuantity()
        );
        assertEquals(
                new BigDecimal("100"),
                result.getTrades().get(1).getPrice()
        );
        assertEquals(
                new BigDecimal("3"),
                result.getTrades().get(1).getQuantity()
        );
        assertEquals(BigDecimal.ZERO, marketSell.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, marketSell.getStatus());
    }

    @Test
    void marketSellShouldStopWhenLiquidityRunsOut() {
        MatchingEngine engine = newEngine();
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.BUY, "100", "2", 1)
        );

        MatchingOrder marketSell = order(
                OrderSide.SELL,
                OrderType.MARKET,
                null,
                "5",
                TimeInForce.GTC,
                2
        );

        MatchingResult result = engine.submit(marketSell);

        assertEquals(1, result.getTrades().size());
        assertEquals(
                new BigDecimal("2"),
                result.getTrades().get(0).getQuantity()
        );
        assertEquals(
                new BigDecimal("3"),
                marketSell.getRemainingQuantity()
        );
    }

    @Test
    void iocOrderShouldCancelUnfilledRemainder() {
        MatchingEngine engine = newEngine();
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "100", "2", 1)
        );

        MatchingOrder buy = order(
                OrderSide.BUY,
                OrderType.LIMIT,
                "100",
                "5",
                TimeInForce.IOC,
                2
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(1, result.getTrades().size());
        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(OrderStatus.CANCELLED, buy.getStatus());
        assertNull(engine.getOrderBook().getBestBid());
    }

    @Test
    void iocOrderWithNoLiquidityShouldBeCanceled() {
        MatchingEngine engine = newEngine();

        MatchingOrder buy = order(
                OrderSide.BUY,
                OrderType.LIMIT,
                "100",
                "10",
                TimeInForce.IOC,
                1
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(0, result.getTrades().size());
        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(OrderStatus.CANCELLED, buy.getStatus());
        assertNull(engine.getOrderBook().getBestBid());
    }

    @Test
    void fokOrderShouldExecuteWhenEnoughLiquidityExists() {
        MatchingEngine engine = newEngine();
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "100", "5", 1)
        );
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "101", "5", 2)
        );

        MatchingOrder buy = order(
                OrderSide.BUY,
                OrderType.LIMIT,
                "101",
                "10",
                TimeInForce.FOK,
                3
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(2, result.getTrades().size());
        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
    }

    @Test
    void fokOrderShouldNotExecuteWhenInsufficientLiquidityExists() {
        MatchingEngine engine = newEngine();
        MatchingOrder sell = limitOrder(
                OrderSide.SELL, "100", "5", 1
        );
        engine.getOrderBook().addOrder(sell);

        MatchingOrder buy = order(
                OrderSide.BUY,
                OrderType.LIMIT,
                "105",
                "10",
                TimeInForce.FOK,
                2
        );

        MatchingResult result = engine.submit(buy);

        assertEquals(0, result.getTrades().size());
        assertEquals(new BigDecimal("10"), buy.getRemainingQuantity());
        assertEquals(OrderStatus.REJECTED, buy.getStatus());
        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook().getBestAsk().getPrice()
        );
        assertEquals(
                new BigDecimal("5"),
                engine.getOrderBook().getBestAsk().getTotalRemainingQuantity()
        );
        assertEquals(OrderStatus.NEW, sell.getStatus());
    }

    @Test
    void postOnlyOrderShouldBeRejectedIfItWouldCross() {
        MatchingEngine engine = newEngine();
        MatchingOrder sell = limitOrder(
                OrderSide.SELL, "100", "1", 1
        );
        engine.getOrderBook().addOrder(sell);

        MatchingOrder postOnlyBuy = order(
                OrderSide.BUY,
                OrderType.LIMIT,
                "101",
                "1",
                TimeInForce.POST_ONLY,
                2
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(postOnlyBuy)
        );

        assertEquals(OrderStatus.REJECTED, postOnlyBuy.getStatus());
        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook().getBestAsk().getPrice()
        );
    }

    @Test
    void postOnlyOrderShouldRestWhenItDoesNotCross() {
        MatchingEngine engine = newEngine();
        engine.getOrderBook().addOrder(
                limitOrder(OrderSide.SELL, "100", "1", 1)
        );

        MatchingOrder postOnlyBuy = order(
                OrderSide.BUY,
                OrderType.LIMIT,
                "99",
                "1",
                TimeInForce.POST_ONLY,
                2
        );

        MatchingResult result = engine.submit(postOnlyBuy);

        assertEquals(0, result.getTrades().size());
        assertEquals(OrderStatus.NEW, postOnlyBuy.getStatus());
        assertEquals(
                new BigDecimal("99"),
                engine.getOrderBook().getBestBid().getPrice()
        );
    }

    @Test
    void cancelShouldRemoveOrderFromOrderBook() {
        MatchingEngine engine = newEngine();
        MatchingOrder buy = limitOrder(
                OrderSide.BUY, "100", "1", 1
        );

        engine.submit(buy);
        engine.cancel(buy.getOrderId());

        assertNull(engine.getOrderBook().getBestBid());
        assertEquals(BigDecimal.ZERO, buy.getRemainingQuantity());
        assertEquals(OrderStatus.CANCELLED, buy.getStatus());
    }

    @Test
    void cancelShouldRemoveOnlyRequestedOrder() {
        MatchingEngine engine = newEngine();
        MatchingOrder first = limitOrder(
                OrderSide.BUY, "100", "1", 1
        );
        MatchingOrder second = limitOrder(
                OrderSide.BUY, "99", "1", 2
        );

        engine.submit(first);
        engine.submit(second);
        engine.cancel(first.getOrderId());

        assertEquals(OrderStatus.CANCELLED, first.getStatus());
        assertEquals(OrderStatus.NEW, second.getStatus());
        assertEquals(
                new BigDecimal("99"),
                engine.getOrderBook().getBestBid().getPrice()
        );
    }

    @Test
    void cancelUnknownOrderShouldFail() {
        MatchingEngine engine = newEngine();

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.cancel(UUID.randomUUID())
        );
    }

    @Test
    void partiallyFilledOrderCanBeCanceled() {
        MatchingEngine engine = newEngine();

        MatchingOrder sellOrder = order(
                1L,
                UUID.randomUUID(),
                SYMBOL,
                OrderSide.SELL,
                OrderType.LIMIT,
                "100",
                "5",
                TimeInForce.GTC,
                1
        );

        engine.submit(sellOrder);

        MatchingOrder buyOrder = order(
                2L,
                UUID.randomUUID(),
                SYMBOL,
                OrderSide.BUY,
                OrderType.LIMIT,
                "100",
                "2",
                TimeInForce.GTC,
                2
        );

        engine.submit(buyOrder);

        // The sell order was partially filled: 5 - 2 = 3 remaining.
        assertEquals(
                new BigDecimal("3"),
                sellOrder.getRemainingQuantity()
        );
        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                sellOrder.getStatus()
        );

        // Cancel the remaining quantity.
        engine.cancel(sellOrder.getOrderId());

        // Cancellation clears the unfilled quantity.
        assertEquals(
                BigDecimal.ZERO,
                sellOrder.getRemainingQuantity()
        );
        assertEquals(
                OrderStatus.CANCELLED,
                sellOrder.getStatus()
        );
        assertNull(engine.getOrderBook().getBestAsk());
    }

    @Test
    void nullOrderShouldFail() {
        MatchingEngine engine = newEngine();

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(null)
        );
    }

    @Test
    void nullOrderIdShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                1L, null, SYMBOL,
                OrderSide.BUY, OrderType.LIMIT,
                "100", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullUserIdShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                null, UUID.randomUUID(), SYMBOL,
                OrderSide.BUY, OrderType.LIMIT,
                "100", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void wrongSymbolShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                1L, UUID.randomUUID(), "ETH/USDT",
                OrderSide.BUY, OrderType.LIMIT,
                "100", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullSideShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                1L, UUID.randomUUID(), SYMBOL,
                null, OrderType.LIMIT,
                "100", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullOrderTypeShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                1L, UUID.randomUUID(), SYMBOL,
                OrderSide.BUY, null,
                "100", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullTimeInForceShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                1L, UUID.randomUUID(), SYMBOL,
                OrderSide.BUY, OrderType.LIMIT,
                "100", "1", null, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void zeroQuantityShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.LIMIT,
                "100", "0", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void negativeQuantityShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.LIMIT,
                "100", "-1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void limitOrderWithoutPriceShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.LIMIT,
                null, "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void limitOrderWithZeroPriceShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.LIMIT,
                "0", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void limitOrderWithNegativePriceShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.LIMIT,
                "-1", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void marketOrderWithPriceShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.MARKET,
                "100", "1", TimeInForce.GTC, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void postOnlyMarketOrderShouldFail() {
        MatchingEngine engine = newEngine();
        MatchingOrder order = order(
                OrderSide.BUY, OrderType.MARKET,
                null, "1", TimeInForce.POST_ONLY, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void duplicateOrderIdShouldFail() {
        MatchingEngine engine = newEngine();
        UUID sharedOrderId = UUID.randomUUID();

        MatchingOrder first = order(
                1L, sharedOrderId, SYMBOL,
                OrderSide.BUY, OrderType.LIMIT,
                "100", "1", TimeInForce.GTC, 1
        );

        MatchingOrder duplicate = order(
                2L, sharedOrderId, SYMBOL,
                OrderSide.BUY, OrderType.LIMIT,
                "99", "1", TimeInForce.GTC, 2
        );

        engine.submit(first);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(duplicate)
        );
    }

    @Test
    void reduceRemainingQuantityShouldSubtractValidQuantity() {
        MatchingOrder order = limitOrder(
                OrderSide.BUY, "100", "5", 1
        );

        order.reduceRemainingQuantity(new BigDecimal("2"));

        assertEquals(
                new BigDecimal("3"),
                order.getRemainingQuantity()
        );
    }

    @Test
    void reduceRemainingQuantityShouldAllowExactRemainingQuantity() {
        MatchingOrder order = limitOrder(
                OrderSide.BUY, "100", "5", 1
        );

        order.reduceRemainingQuantity(new BigDecimal("5"));

        assertEquals(BigDecimal.ZERO, order.getRemainingQuantity());
    }

    @Test
    void reduceRemainingQuantityShouldRejectNull() {
        MatchingOrder order = limitOrder(
                OrderSide.BUY, "100", "5", 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> order.reduceRemainingQuantity(null)
        );
    }

    @Test
    void reduceRemainingQuantityShouldRejectZero() {
        MatchingOrder order = limitOrder(
                OrderSide.BUY, "100", "5", 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> order.reduceRemainingQuantity(BigDecimal.ZERO)
        );
    }

    @Test
    void reduceRemainingQuantityShouldRejectNegativeQuantity() {
        MatchingOrder order = limitOrder(
                OrderSide.BUY, "100", "5", 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> order.reduceRemainingQuantity(
                        new BigDecimal("-1")
                )
        );
    }

    @Test
    void reduceRemainingQuantityShouldRejectQuantityAboveRemaining() {
        MatchingOrder order = limitOrder(
                OrderSide.BUY, "100", "5", 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> order.reduceRemainingQuantity(
                        new BigDecimal("6")
                )
        );

        assertEquals(
                new BigDecimal("5"),
                order.getRemainingQuantity()
        );
    }
}
