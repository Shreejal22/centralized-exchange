
package com.shreejal.exchange.service;

import com.shreejal.exchange.dto.PlaceOrderRequest;
import com.shreejal.exchange.matching.MatchingEngine;
import com.shreejal.exchange.matching.MatchingEngineRegistry;
import com.shreejal.exchange.model.Order;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

@Service
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

    public Order placeOrder(PlaceOrderRequest request) {

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

        ReentrantLock marketLock =
                matchingEngineRegistry.lockFor(symbol);

        marketLock.lock();

        try {
            MatchingEngine engine =
                    matchingEngineRegistry.getOrCreate(symbol);

            MatchingEngine.Checkpoint checkpoint =
                    engine.checkpoint();

            try {
                /*
                 * OrderTransactionService is a separate Spring
                 * bean, so its @Transactional proxy can start and
                 * commit the database transaction around execute().
                 *
                 * If execute() or transaction commit throws a
                 * RuntimeException, restore the in-memory state.
                 */
                return orderTransactionService.execute(
                        request,
                        engine
                );

            } catch (RuntimeException exception) {
                engine.restore(checkpoint);
                throw exception;
            }

        } finally {
            /*
             * Always release the market lock, even if validation,
             * database work, matching, or rollback fails.
             */
            marketLock.unlock();
        }
    }
}
