package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet,Long> {
    Optional<Wallet> findByUser_Id(Long userId);
}
