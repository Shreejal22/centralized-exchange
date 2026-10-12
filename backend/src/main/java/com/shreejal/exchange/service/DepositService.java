
package com.shreejal.exchange.service;

import com.shreejal.exchange.exception.InvalidAmountException;
import com.shreejal.exchange.model.*;
import com.shreejal.exchange.repository.AccountRepository;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.DepositRepository;
import com.shreejal.exchange.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class DepositService {

    private final WalletService walletService;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final BalanceService balanceService;
    private final LedgerService ledgerService;
    private final DepositRepository depositRepository;
    private final UserRepository userRepository;

    public DepositService(
            WalletService walletService,
            AssetRepository assetRepository,
            AccountRepository accountRepository,
            BalanceService balanceService,
            LedgerService ledgerService,
            DepositRepository depositRepository,
            UserRepository userRepository) {

        this.walletService = walletService;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.balanceService = balanceService;
        this.ledgerService = ledgerService;
        this.depositRepository = depositRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Balance deposit(
            Long userId,
            String assetSymbol,
            BigDecimal amount,
            String idempotencyKey) {

        // 1. Validate the request.
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }

        if (assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException("Asset symbol is required");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(
                    "Deposit amount must be positive"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        // 2. Normalize the symbol and idempotency key.
        String symbol = assetSymbol.trim().toUpperCase(Locale.ROOT);
        String key = idempotencyKey.trim();

        if (key.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must not exceed 100 characters"
            );
        }

        // 3. Find the asset.
        Asset asset = assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Asset not found: " + symbol
                ));

        // 4. Lock the user row to serialize their deposit requests.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + userId
                ));

        // 5. Check whether this request was already processed.
        var existing = depositRepository
                .findByUser_IdAndIdempotencyKey(userId, key);

        if (existing.isPresent()) {
            Deposit previous = existing.get();

            boolean sameAsset =
                    previous.getAsset().getId().equals(asset.getId());

            boolean sameAmount =
                    previous.getAmount().compareTo(amount) == 0;

            if (!sameAsset || !sameAmount) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used for a different deposit"
                );
            }

            // Return the current balance without crediting again.
            Wallet existingWallet =
                    walletService.getOrCreateWallet(userId);

            return balanceService.getBalance(existingWallet, asset);
        }

        // 6. Get or create the user's wallet.
        // The user has already been loaded and locked above.
        Wallet wallet = walletService.getOrCreateWallet(user.getId());

        // 7. Persist the deposit to obtain its database ID.
        Deposit deposit = new Deposit();
        deposit.setUser(user);
        deposit.setAsset(asset);
        deposit.setAmount(amount);
        deposit.setIdempotencyKey(key);

        deposit = depositRepository.saveAndFlush(deposit);

        // 8. Get or create the ledger accounts.
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

        // 9. Record the double-entry ledger transaction.
        ledgerService.post(
                TransactionType.DEPOSIT,
                "SIMULATED_DEPOSIT",
                deposit.getId(),
                "Simulated deposit of " + amount + " " + symbol,
                List.of(
                        new LedgerPosting(
                                fundingAccount,
                                LedgerEntryType.DEBIT,
                                amount
                        ),
                        new LedgerPosting(
                                userAccount,
                                LedgerEntryType.CREDIT,
                                amount
                        )
                )
        );

        // 10. Credit the user's spendable balance.
        return balanceService.credit(wallet, asset, amount);
    }

    private Account getOrCreateAccount(
            Asset asset,
            String name,
            AccountType accountType) {

        return accountRepository.findByAssetAndName(asset, name)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setAsset(asset);
                    account.setName(name);
                    account.setAccountType(accountType);

                    return accountRepository.save(account);
                });
    }
}
