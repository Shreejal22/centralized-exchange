
package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradingPairRepository
        extends JpaRepository<TradingPair, Long> {

    Optional<TradingPair> findBySymbol(String symbol);
}
