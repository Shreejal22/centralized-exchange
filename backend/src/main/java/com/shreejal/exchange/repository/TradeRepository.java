package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade,Long> {
}
