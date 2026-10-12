
package com.shreejal.exchange.service;

import com.shreejal.exchange.exception.BalanceNotFoundException;
import com.shreejal.exchange.exception.InsufficientBalanceException;
import com.shreejal.exchange.exception.InvalidAmountException;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.Balance;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final BalanceRepository balanceRepository;

    public BalanceService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public void lock(Wallet wallet, Asset asset, BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(
                    "Lock amount must be greater than zero"
            );
        }

        Balance balance = balanceRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseThrow(() ->
                        new BalanceNotFoundException(
                                "Balance not found for wallet and asset"
                        )
                );

        if (balance.getAvailable().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient available balance"
            );
        }

        balance.setAvailable(
                balance.getAvailable().subtract(amount)
        );

        balance.setReserved(
                balance.getReserved().add(amount)
        );

        if (balance.getAvailable().signum() < 0) {
            throw new IllegalStateException(
                    "Available balance cannot be negative"
            );
        }

        if (balance.getReserved().signum() < 0) {
            throw new IllegalStateException(
                    "Reserved balance cannot be negative"
            );
        }

        balanceRepository.save(balance);
    }


    @Transactional
    public void unlock(Wallet wallet, Asset asset, BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(
                    "Unlock amount must be greater than zero"
            );
        }

        Balance balance = balanceRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseThrow(() ->
                        new BalanceNotFoundException(
                                "Balance not found for wallet and asset"
                        )
                );

        if (balance.getReserved().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient reserved balance"
            );
        }

        balance.setReserved(
                balance.getReserved().subtract(amount)
        );

        balance.setAvailable(
                balance.getAvailable().add(amount)
        );

        balanceRepository.save(balance);
    }

    @Transactional
    public Balance initialise(Wallet wallet,Asset asset){
        if(wallet==null||asset==null){
            throw new IllegalArgumentException("Wallet and Asset are required");
        }
        return balanceRepository. findByWalletAndAsset(wallet, asset)
                .orElseGet(() -> {
            Balance balance = new Balance();
            balance.setWallet(wallet);
            balance.setAsset(asset);
            balance.setAvailable(BigDecimal.ZERO);
            balance.setReserved(BigDecimal.ZERO);

            return balanceRepository.save(balance);
        }
                        );
    }
    @Transactional
    public Balance credit(Wallet wallet, Asset asset, BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be positive");
        }

        Balance balance = balanceRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseThrow(() ->
                        new BalanceNotFoundException(
                                "Balance not found for wallet and asset"));

        balance.setAvailable(balance.getAvailable().add(amount));

        return balanceRepository.save(balance);
    }

    @Transactional(readOnly = true)
    public Balance getBalance(Wallet wallet, Asset asset) {
        return balanceRepository.findByWalletAndAsset(wallet, asset)
                .orElseThrow(() -> new BalanceNotFoundException(
                        "Balance not found for wallet and asset"
                ));
    }

    @Transactional
    public Balance debit(
            Wallet wallet,
            Asset asset,
            BigDecimal amount
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(
                    "Debit amount must be positive"
            );
        }

        Balance balance = balanceRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseThrow(() ->
                        new BalanceNotFoundException(
                                "Balance not found for wallet and asset"
                        )
                );

        if (balance.getAvailable().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient available balance"
            );
        }

        balance.setAvailable(
                balance.getAvailable().subtract(amount)
        );

        return balanceRepository.save(balance);
    }


}