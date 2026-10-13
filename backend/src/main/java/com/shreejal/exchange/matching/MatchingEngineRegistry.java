package com.shreejal.exchange.matching;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class MatchingEngineRegistry {

    private final ConcurrentMap<String, MatchingEngine> engines =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, ReentrantLock> marketLocks =
            new ConcurrentHashMap<>();

    public MatchingEngine getOrCreate(String symbol) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Trading pair symbol is required"
            );
        }

        return engines.computeIfAbsent(
                symbol,
                MatchingEngine::new
        );
    }

    public ReentrantLock lockFor(String symbol) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Trading pair symbol is required"
            );
        }

        return marketLocks.computeIfAbsent(
                symbol,
                ignored -> new ReentrantLock()
        );
    }
}