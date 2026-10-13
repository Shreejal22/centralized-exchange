
        package com.shreejal.exchange.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchingEngineRecoveryTest {

    private static final String SYMBOL = "BTC/USDT";

    private MatchingOrder limitOrder(
            OrderSide side,
            String price,
            String quantity,
            long sequenceNumber
    ) {
        return new MatchingOrder(
                1L,
                UUID.randomUUID(),
                SYMBOL,
                side,
                OrderType.LIMIT,
                new BigDecimal(price),
                new BigDecimal(quantity),
                TimeInForce.GTC,
                sequenceNumber
        );
    }

    @Test
    void restoreShouldUndoInMemoryChangesFromFailedMatch() {

        // 1. Create the matching engine.
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        // 2. Add a seller offering 2 BTC at 50,000 USDT per BTC.
        MatchingOrder sell = limitOrder(
                OrderSide.SELL,
                "50000",
                "2",
                1
        );

        engine.submit(sell);

        // 3. Save the engine state before the buyer's order.
        MatchingEngine.Checkpoint checkpoint = engine.checkpoint();

        // 4. Submit a buyer's order for 1 BTC at 50,000.
        MatchingOrder buy = limitOrder(
                OrderSide.BUY,
                "50000",
                "1",
                2
        );

        MatchingResult result = engine.submit(buy);

        // 5. The first match should execute 1 BTC.
        assertEquals(
                1,
                result.getTrades().size(),
                "The buyer should match with the resting seller"
        );

        // 6. The seller should now have 1 BTC remaining.
        assertEquals(
                new BigDecimal("1"),
                engine.getOrderBook()
                        .getBestAsk()
                        .getTotalRemainingQuantity()
        );

        // 7. Simulate recovery after a database transaction fails.
        engine.restore(checkpoint);

        // 8. Verify that the seller has been restored to 2 BTC.
        PriceLevel restoredAsk = engine.getOrderBook().getBestAsk();

        assertNotNull(restoredAsk);

        assertEquals(
                new BigDecimal("50000"),
                restoredAsk.getPrice()
        );

        assertEquals(
                new BigDecimal("2"),
                restoredAsk.getTotalRemainingQuantity()
        );

        assertEquals(
                new BigDecimal("2"),
                restoredAsk.peekFirstOrder().getRemainingQuantity()
        );

        assertEquals(
                OrderStatus.NEW,
                restoredAsk.peekFirstOrder().getStatus()
        );

        // 9. Re-create the buyer order using the SAME ID.
        // The original buy object was already mutated by the first match.
        // Therefore, do not submit that old object again.
        MatchingOrder retryBuy = new MatchingOrder(
                1L,
                buy.getOrderId(),
                SYMBOL,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000"),
                new BigDecimal("1"),
                TimeInForce.GTC,
                2
        );

        // 10. The retry should match the restored seller.
        MatchingResult retryResult = engine.submit(retryBuy);

        assertEquals(
                1,
                retryResult.getTrades().size(),
                "The retried order should match after restoration"
        );

        // 11. One BTC should remain on the sell side.
        PriceLevel askAfterRetry = engine.getOrderBook().getBestAsk();

        assertNotNull(askAfterRetry);

        assertEquals(
                new BigDecimal("1"),
                askAfterRetry.getTotalRemainingQuantity()
        );
    }

    @Test
    void restoreShouldRejectCheckpointFromAnotherMarket() {

        MatchingEngine btcEngine = new MatchingEngine("BTC/USDT");
        MatchingEngine ethEngine = new MatchingEngine("ETH/USDT");

        MatchingEngine.Checkpoint btcCheckpoint =
                btcEngine.checkpoint();

        assertThrows(
                IllegalArgumentException.class,
                () -> ethEngine.restore(btcCheckpoint)
        );
    }

    @Test
    void restoreShouldRejectNullCheckpoint() {

        MatchingEngine engine = new MatchingEngine(SYMBOL);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.restore(null)
        );
    }
}
