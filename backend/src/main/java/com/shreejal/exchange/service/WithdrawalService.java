
package com.shreejal.exchange.service;

import com.shreejal.exchange.exception.InvalidAmountException;
import com.shreejal.exchange.model.*;
import com.shreejal.exchange.repository.AccountRepository;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.UserRepository;
import com.shreejal.exchange.repository.WithdrawalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class WithdrawalService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final WalletService walletService;
    private final BalanceService balanceService;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final WithdrawalRepository withdrawalRepository;

    public WithdrawalService(
            UserRepository userRepository,
            AssetRepository assetRepository,
            WalletService walletService,
            BalanceService balanceService,
            AccountRepository accountRepository,
            LedgerService ledgerService,
            WithdrawalRepository withdrawalRepository
    ) {
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.walletService = walletService;
        this.balanceService = balanceService;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.withdrawalRepository = withdrawalRepository;
    }

    @Transactional
    public Balance withdraw(
            Long userId,
            String assetSymbol,
            BigDecimal amount,
            String idempotencyKey
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }

        if (assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Asset symbol is required"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(
                    "Withdrawal amount must be positive"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        String symbol =
                assetSymbol.trim().toUpperCase(Locale.ROOT);

        String key = idempotencyKey.trim();

        if (key.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must not exceed 100 characters"
            );
        }

        // Serialize withdrawal requests for the same user.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "User not found: " + userId
                        )
                );

        Asset asset = assetRepository.findBySymbol(symbol)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Asset not found: " + symbol
                        )
                );

        var existing =
                withdrawalRepository
                        .findByUser_IdAndIdempotencyKey(userId, key);

        if (existing.isPresent()) {
            Withdrawal previous = existing.get();

            boolean sameAsset =
                    previous.getAsset().getId()
                            .equals(asset.getId());

            boolean sameAmount =
                    previous.getAmount().compareTo(amount) == 0;

            if (!sameAsset || !sameAmount) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used " +
                                "for a different withdrawal"
                );
            }

            Wallet wallet = walletService.getOrCreateWallet(userId);

            return balanceService.getBalance(wallet, asset);
        }

        Wallet wallet = walletService.getOrCreateWallet(userId);

        // Save the request inside the same transaction.
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setUser(user);
        withdrawal.setAsset(asset);
        withdrawal.setAmount(amount);
        withdrawal.setIdempotencyKey(key);

        withdrawal = withdrawalRepository.saveAndFlush(withdrawal);

        Account userAccount = getOrCreateAccount(
                asset,
                "USER:" + userId,
                AccountType.USER
        );

        Account fundingAccount = getOrCreateAccount(
                asset,
                "EXCHANGE_FUNDING",
                AccountType.SYSTEM
        );

        // Reverse the simulated deposit ledger direction.
        ledgerService.post(
                TransactionType.WITHDRAWAL,
                "SIMULATED_WITHDRAWAL",
                withdrawal.getId(),
                "Simulated withdrawal of " + amount + " " + symbol,
                List.of(
                        new LedgerPosting(
                                userAccount,
                                LedgerEntryType.DEBIT,
                                amount
                        ),
                        new LedgerPosting(
                                fundingAccount,
                                LedgerEntryType.CREDIT,
                                amount
                        )
                )
        );

        return balanceService.debit(wallet, asset, amount);
    }

    private Account getOrCreateAccount(
            Asset asset,
            String name,
            AccountType accountType
    ) {
        return accountRepository
                .findByAssetAndName(asset, name)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setAsset(asset);
                    account.setName(name);
                    account.setAccountType(accountType);
                    return accountRepository.save(account);
                });
    }
}
