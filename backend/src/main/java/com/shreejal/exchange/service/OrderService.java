package com.shreejal.exchange.service;

import com.shreejal.exchange.dto.PlaceOrderRequest;
import com.shreejal.exchange.matching.MatchingEngine;
import com.shreejal.exchange.matching.MatchingEngineRegistry;

import java.util.concurrent.locks.ReentrantLock;

public class OrderService {
    private final MatchingEngineRegistry matchingEngineRegistry;
    private final OrderTransactionService orderTransactionService;

    public OrderService(
            MatchingEngineRegistry matchingEngineRegistry,
            OrderTransactionService orderTransactionService
    ) {
        this.matchingEngineRegistry = matchingEngineRegistry;
        this.orderTransactionService = orderTransactionService;
    }

    public void placeOrder(PlaceOrderRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Order request cannot be null"
            );
        }

        String symbol = request.getSymbol();

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Trading pair symbol is required"
            );
        }
        ReentrantLock marketLock = matchingEngineRegistry.lockFor(symbol);
        marketLock.lock();

        try {
            MatchingEngine engine = matchingEngineRegistry.getOrCreate(symbol);
            MatchingEngine.Checkpoint checkpoint = engine.checkpoint();

            try {
                orderTransactionService.execute(request, engine);

            } catch (RuntimeException exception) {
                engine.restore(checkpoint);
                throw exception;
            }
            }
        finally {
            marketLock.unlock();

    }

    }
}

