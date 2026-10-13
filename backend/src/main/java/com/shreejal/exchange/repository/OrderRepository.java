package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Order;
import com.shreejal.exchange.model.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    Optional<Order> findByUser_IdAndClientOrderId(
            Long userId,
            String clientOrderId
    );

    List<Order> findByTradingPairAndStatusInOrderByIdAsc(
            TradingPair tradingPair,
            List<String> statuses
    );
}
