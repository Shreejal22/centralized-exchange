package com.shreejal.exchange.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchingEngineTest {

    @Test
    void engineShouldStartWithCorrectOrderBook() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        assertEquals(
                "BTC/USDT",
                engine.getOrderBook().getSymbol()
        );
    }

    @Test
    void limitOrderShouldBeAddedToOrderBook() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        engine.submit(order);

        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook()
                        .getBestBid()
                        .getPrice()
        );

        assertEquals(
                OrderStatus.NEW,
                order.getStatus()
        );
    }

    @Test
    void buyOrderShouldMatchExistingSellOrder() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        2
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                1,
                result.getTrades().size()
        );

        Trade trade =
                result.getTrades().get(0);

        assertEquals(
                new BigDecimal("100"),
                trade.getPrice()
        );

        assertEquals(
                new BigDecimal("1"),
                trade.getQuantity()
        );

        assertEquals(
                BigDecimal.ZERO,
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                buyOrder.getStatus()
        );

        assertEquals(
                BigDecimal.ZERO,
                sellOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                sellOrder.getStatus()
        );
    }

    @Test
    void buyOrderShouldPartiallyFillExistingSellOrder() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("4"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        TimeInForce.GTC,
                        2
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                1,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("4"),
                result.getTrades()
                        .get(0)
                        .getQuantity()
        );

        assertEquals(
                new BigDecimal("6"),
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                buyOrder.getStatus()
        );

        assertEquals(
                BigDecimal.ZERO,
                sellOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                sellOrder.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestAsk()
        );
    }

    @Test
    void buyOrderShouldMatchAcrossMultiplePriceLevels() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sell100 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder sell101 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("101"),
                        new BigDecimal("3"),
                        TimeInForce.GTC,
                        2
                );

        engine.getOrderBook().addOrder(sell100);
        engine.getOrderBook().addOrder(sell101);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("105"),
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        3
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                2,
                result.getTrades().size()
        );

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

        assertEquals(
                BigDecimal.ZERO,
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                buyOrder.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestAsk()
        );
    }

    @Test
    void bestPriceShouldBeMatchedFirst() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sell105 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("105"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder sell100 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        2
                );

        MatchingOrder sell103 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("103"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        3
                );

        engine.getOrderBook().addOrder(sell105);
        engine.getOrderBook().addOrder(sell100);
        engine.getOrderBook().addOrder(sell103);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("110"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        4
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                new BigDecimal("100"),
                result.getTrades()
                        .get(0)
                        .getPrice()
        );
    }

    @Test
    void olderOrderAtSamePriceShouldMatchFirst() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder firstSell =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder secondSell =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        2
                );

        engine.getOrderBook().addOrder(firstSell);
        engine.getOrderBook().addOrder(secondSell);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        3
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                firstSell.getOrderId(),
                result.getTrades()
                        .get(0)
                        .getSellOrderId()
        );
    }

    @Test
    void limitBuyBelowBestAskShouldNotMatch() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("105"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        2
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                0,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("1"),
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.NEW,
                buyOrder.getStatus()
        );

        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook()
                        .getBestBid()
                        .getPrice()
        );
    }

    @Test
    void sellOrderShouldMatchExistingBuyOrder() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(buyOrder);

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        TimeInForce.GTC,
                        2
                );

        MatchingResult result =
                engine.submit(sellOrder);

        assertEquals(
                1,
                result.getTrades().size()
        );

        Trade trade =
                result.getTrades().get(0);

        assertEquals(
                buyOrder.getOrderId(),
                trade.getBuyOrderId()
        );

        assertEquals(
                sellOrder.getOrderId(),
                trade.getSellOrderId()
        );

        assertEquals(
                new BigDecimal("100"),
                trade.getPrice()
        );

        assertEquals(
                new BigDecimal("2"),
                trade.getQuantity()
        );

        assertEquals(
                BigDecimal.ZERO,
                sellOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                sellOrder.getStatus()
        );

        assertEquals(
                BigDecimal.ZERO,
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                buyOrder.getStatus()
        );
    }

    @Test
    void marketBuyShouldMatchAvailableLiquidity() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sell100 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder sell101 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("101"),
                        new BigDecimal("3"),
                        TimeInForce.GTC,
                        2
                );

        engine.getOrderBook().addOrder(sell100);
        engine.getOrderBook().addOrder(sell101);

        MatchingOrder marketBuy =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        3
                );

        MatchingResult result =
                engine.submit(marketBuy);

        assertEquals(
                2,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("100"),
                result.getTrades()
                        .get(0)
                        .getPrice()
        );

        assertEquals(
                new BigDecimal("2"),
                result.getTrades()
                        .get(0)
                        .getQuantity()
        );

        assertEquals(
                new BigDecimal("101"),
                result.getTrades()
                        .get(1)
                        .getPrice()
        );

        assertEquals(
                new BigDecimal("3"),
                result.getTrades()
                        .get(1)
                        .getQuantity()
        );

        assertEquals(
                BigDecimal.ZERO,
                marketBuy.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                marketBuy.getStatus()
        );
    }

    @Test
    void marketBuyShouldStopWhenLiquidityRunsOut() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder marketBuy =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("10"),
                        TimeInForce.GTC,
                        2
                );

        MatchingResult result =
                engine.submit(marketBuy);

        assertEquals(
                1,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("5"),
                result.getTrades()
                        .get(0)
                        .getQuantity()
        );

        assertEquals(
                new BigDecimal("5"),
                marketBuy.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                marketBuy.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestAsk()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestBid()
        );
    }

    @Test
    void marketSellShouldMatchAvailableLiquidity() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder buy100 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder buy99 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("99"),
                        new BigDecimal("3"),
                        TimeInForce.GTC,
                        2
                );

        engine.getOrderBook().addOrder(buy100);
        engine.getOrderBook().addOrder(buy99);

        MatchingOrder marketSell =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        3
                );

        MatchingResult result =
                engine.submit(marketSell);

        assertEquals(
                2,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("100"),
                result.getTrades()
                        .get(0)
                        .getPrice()
        );

        assertEquals(
                new BigDecimal("2"),
                result.getTrades()
                        .get(0)
                        .getQuantity()
        );

        assertEquals(
                new BigDecimal("99"),
                result.getTrades()
                        .get(1)
                        .getPrice()
        );

        assertEquals(
                new BigDecimal("3"),
                result.getTrades()
                        .get(1)
                        .getQuantity()
        );

        assertEquals(
                BigDecimal.ZERO,
                marketSell.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                marketSell.getStatus()
        );
    }

    @Test
    void marketSellShouldStopWhenLiquidityRunsOut() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(buyOrder);

        MatchingOrder marketSell =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("10"),
                        TimeInForce.GTC,
                        2
                );

        MatchingResult result =
                engine.submit(marketSell);

        assertEquals(
                1,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("5"),
                marketSell.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                marketSell.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestBid()
        );
    }

    @Test
    void iocOrderShouldCancelUnfilledRemainder() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("4"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        TimeInForce.IOC,
                        2
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                1,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("4"),
                result.getTrades()
                        .get(0)
                        .getQuantity()
        );

        assertEquals(
                BigDecimal.ZERO,
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.CANCELLED,
                buyOrder.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestBid()
        );
    }

    @Test
    void iocOrderWithNoLiquidityShouldBeCanceled() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        TimeInForce.IOC,
                        1
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                0,
                result.getTrades().size()
        );

        assertEquals(
                BigDecimal.ZERO,
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.CANCELLED,
                buyOrder.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestBid()
        );
    }

    @Test
    void fokOrderShouldExecuteWhenEnoughLiquidityExists() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sell100 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder sell101 =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("101"),
                        new BigDecimal("3"),
                        TimeInForce.GTC,
                        2
                );

        engine.getOrderBook().addOrder(sell100);
        engine.getOrderBook().addOrder(sell101);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("105"),
                        new BigDecimal("5"),
                        TimeInForce.FOK,
                        3
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                2,
                result.getTrades().size()
        );

        assertEquals(
                BigDecimal.ZERO,
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.FILLED,
                buyOrder.getStatus()
        );
    }

    @Test
    void fokOrderShouldNotExecuteWhenInsufficientLiquidityExists() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder buyOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("105"),
                        new BigDecimal("10"),
                        TimeInForce.FOK,
                        2
                );

        MatchingResult result =
                engine.submit(buyOrder);

        assertEquals(
                0,
                result.getTrades().size()
        );

        assertEquals(
                new BigDecimal("10"),
                buyOrder.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.REJECTED,
                buyOrder.getStatus()
        );

        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook()
                        .getBestAsk()
                        .getPrice()
        );

        assertEquals(
                new BigDecimal("5"),
                engine.getOrderBook()
                        .getBestAsk()
                        .getTotalRemainingQuantity()
        );

        assertEquals(
                OrderStatus.NEW,
                sellOrder.getStatus()
        );
    }

    @Test
    void postOnlyOrderShouldBeRejectedIfItWouldCross() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder postOnlyBuy =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("101"),
                        new BigDecimal("1"),
                        TimeInForce.POST_ONLY,
                        2
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(postOnlyBuy)
        );

        assertEquals(
                OrderStatus.REJECTED,
                postOnlyBuy.getStatus()
        );

        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook()
                        .getBestAsk()
                        .getPrice()
        );
    }

    @Test
    void postOnlyOrderShouldRestWhenItDoesNotCross() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sellOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sellOrder);

        MatchingOrder postOnlyBuy =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("99"),
                        new BigDecimal("1"),
                        TimeInForce.POST_ONLY,
                        2
                );

        engine.submit(postOnlyBuy);

        assertEquals(
                new BigDecimal("99"),
                engine.getOrderBook()
                        .getBestBid()
                        .getPrice()
        );

        assertEquals(
                OrderStatus.NEW,
                postOnlyBuy.getStatus()
        );

        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook()
                        .getBestAsk()
                        .getPrice()
        );
    }

    @Test
    void cancelShouldRemoveOrderFromOrderBook() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("5"),
                        TimeInForce.GTC,
                        1
                );

        engine.submit(order);

        assertEquals(
                new BigDecimal("100"),
                engine.getOrderBook()
                        .getBestBid()
                        .getPrice()
        );

        engine.cancel(order.getOrderId());

        assertEquals(
                null,
                engine.getOrderBook().getBestBid()
        );

        assertEquals(
                BigDecimal.ZERO,
                order.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.CANCELLED,
                order.getStatus()
        );
    }

    @Test
    void cancelShouldRemoveOnlyRequestedOrder() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder first =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder second =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        2
                );

        MatchingOrder third =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        3
                );

        engine.submit(first);
        engine.submit(second);
        engine.submit(third);

        engine.cancel(second.getOrderId());

        PriceLevel level =
                engine.getOrderBook().getBestBid();

        assertEquals(
                first,
                level.peekFirstOrder()
        );

        assertEquals(
                new BigDecimal("1"),
                first.getRemainingQuantity()
        );

        assertEquals(
                new BigDecimal("1"),
                third.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.CANCELLED,
                second.getStatus()
        );

        assertEquals(
                OrderStatus.NEW,
                first.getStatus()
        );

        assertEquals(
                OrderStatus.NEW,
                third.getStatus()
        );
    }

    @Test
    void cancelUnknownOrderShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        UUID unknownOrderId =
                UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.cancel(unknownOrderId)
        );
    }

    @Test
    void partiallyFilledOrderCanBeCanceled() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder sell =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("4"),
                        TimeInForce.GTC,
                        1
                );

        engine.getOrderBook()
                .addOrder(sell);

        MatchingOrder buy =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        TimeInForce.GTC,
                        2
                );

        engine.submit(buy);

        assertEquals(
                new BigDecimal("6"),
                buy.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                buy.getStatus()
        );

        engine.cancel(buy.getOrderId());

        assertEquals(
                BigDecimal.ZERO,
                buy.getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.CANCELLED,
                buy.getStatus()
        );

        assertEquals(
                null,
                engine.getOrderBook().getBestBid()
        );
    }

    @Test
    void nullOrderShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(null)
        );
    }

    @Test
    void nullOrderIdShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        null,
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullUserIdShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        null,
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void wrongSymbolShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "ETH/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullSideShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        null,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullOrderTypeShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        null,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void nullTimeInForceShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        null,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void zeroQuantityShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void negativeQuantityShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("-1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void limitOrderWithoutPriceShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        null,
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void limitOrderWithZeroPriceShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        BigDecimal.ZERO,
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void limitOrderWithNegativePriceShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("-100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void marketOrderWithPriceShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void postOnlyMarketOrderShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        MatchingOrder order =
                new MatchingOrder(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("1"),
                        TimeInForce.POST_ONLY,
                        1
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(order)
        );
    }

    @Test
    void duplicateOrderIdShouldFail() {
        MatchingEngine engine =
                new MatchingEngine("BTC/USDT");

        UUID orderId =
                UUID.randomUUID();

        MatchingOrder firstOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        orderId,
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("100"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        1
                );

        MatchingOrder duplicateOrder =
                new MatchingOrder(
                        UUID.randomUUID(),
                        orderId,
                        "BTC/USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("99"),
                        new BigDecimal("1"),
                        TimeInForce.GTC,
                        2
                );

        engine.submit(firstOrder);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.submit(duplicateOrder)
        );
    }
}