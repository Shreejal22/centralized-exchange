
        package com.shreejal.exchange.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderBookTest {

    @Test
    void testBestBid() {
        OrderBook book = new OrderBook("BTC/USDT");

        MatchingOrder order = new MatchingOrder(
                1L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("100"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                1
        );

        book.addOrder(order);

        PriceLevel bestBid = book.getBestBid();

        assertEquals(new BigDecimal("100"), bestBid.getPrice());
    }

    @Test
    void bestBidShouldBeHighestPrice() {
        OrderBook book = new OrderBook("BTC/USDT");

        MatchingOrder order100 = new MatchingOrder(
                1L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("100"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                1
        );

        MatchingOrder order105 = new MatchingOrder(
                2L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("105"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                2
        );

        MatchingOrder order102 = new MatchingOrder(
                3L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("102"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                3
        );

        book.addOrder(order100);
        book.addOrder(order105);
        book.addOrder(order102);

        assertEquals(new BigDecimal("105"), book.getBestBid().getPrice());
    }

    @Test
    void bestAskShouldBeLowestPrice() {
        OrderBook book = new OrderBook("BTC/USDT");

        MatchingOrder order105 = new MatchingOrder(
                1L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("105"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                1
        );

        MatchingOrder order101 = new MatchingOrder(
                2L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("101"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                2
        );

        MatchingOrder order103 = new MatchingOrder(
                3L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("103"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                3
        );

        book.addOrder(order105);
        book.addOrder(order101);
        book.addOrder(order103);

        assertEquals(new BigDecimal("101"), book.getBestAsk().getPrice());
    }

    @Test
    void ordersAtSamePriceShouldFollowTimePriority() {
        OrderBook book = new OrderBook("BTC/USDT");

        MatchingOrder firstOrder = new MatchingOrder(
                1L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("100"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                1
        );

        MatchingOrder secondOrder = new MatchingOrder(
                2L,
                UUID.randomUUID(),
                "BTC/USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("100"),
                new BigDecimal("2"),
                TimeInForce.GTC,
                2
        );

        book.addOrder(firstOrder);
        book.addOrder(secondOrder);

        PriceLevel level = book.getBestBid();

        assertEquals(firstOrder, level.peekFirstOrder());
    }
}