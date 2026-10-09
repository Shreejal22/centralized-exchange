package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet,Long> {
}
