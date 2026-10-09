package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Balance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import com.shreejal.exchange.model.Asset;

import com.shreejal.exchange.model.Wallet;



public interface BalanceRepository extends JpaRepository<Balance,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Balance> findByWalletAndAsset(Wallet wallet, Asset asset);
}
