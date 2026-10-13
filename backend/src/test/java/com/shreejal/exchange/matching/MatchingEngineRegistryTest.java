package com.shreejal.exchange.matching;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineRegistryTest {

    @Test
    void shouldReturnSameEngineForSameSymbol() {
        MatchingEngineRegistry registry =
                new MatchingEngineRegistry();

        MatchingEngine first =
                registry.getOrCreate("BTC/USDT");

        MatchingEngine second =
                registry.getOrCreate("BTC/USDT");

        assertSame(first, second);
    }

    @Test
    void shouldCreateDifferentEnginesForDifferentSymbols() {
        MatchingEngineRegistry registry =
                new MatchingEngineRegistry();

        MatchingEngine btc =
                registry.getOrCreate("BTC/USDT");

        MatchingEngine eth =
                registry.getOrCreate("ETH/USDT");

        assertNotSame(btc, eth);
        assertEquals("BTC/USDT", btc.getOrderBook().getSymbol());
        assertEquals("ETH/USDT", eth.getOrderBook().getSymbol());
    }

    @Test
    void shouldReturnSameLockForSameSymbol() {
        MatchingEngineRegistry registry =
                new MatchingEngineRegistry();

        ReentrantLock first =
                registry.lockFor("BTC/USDT");

        ReentrantLock second =
                registry.lockFor("BTC/USDT");

        assertSame(first, second);
    }

    @Test
    void shouldUseDifferentLocksForDifferentSymbols() {
        MatchingEngineRegistry registry =
                new MatchingEngineRegistry();

        assertNotSame(
                registry.lockFor("BTC/USDT"),
                registry.lockFor("ETH/USDT")
        );
    }

    @Test
    void shouldRejectBlankSymbol() {
        MatchingEngineRegistry registry =
                new MatchingEngineRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.getOrCreate(" ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.lockFor(" ")
        );
    }
}