
package com.shreejal.exchange.service;

import com.shreejal.exchange.exception.InvalidAmountException;
import com.shreejal.exchange.model.*;
import com.shreejal.exchange.repository.AccountRepository;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.DepositRepository;
import com.shreejal.exchange.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock
    private WalletService walletService;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceService balanceService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DepositService depositService;

    private final Long userId = 42L;

    private User user;
    private Asset asset;
    private Wallet wallet;
    private Balance balance;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);

        asset = new Asset();
        asset.setId(1L);
        asset.setSymbol("BTC");

        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUser(user);

        balance = new Balance();
        balance.setId(20L);
        balance.setWallet(wallet);
        balance.setAsset(asset);
        balance.setAvailable(new BigDecimal("1.5000000000"));
        balance.setReserved(BigDecimal.ZERO);
    }

    @Test
    void validDepositPostsLedgerEntriesAndCreditsBalance() {
        BigDecimal amount = new BigDecimal("0.5000000000");
        String key = "deposit-001";

        when(assetRepository.findBySymbol("BTC"))
                .thenReturn(Optional.of(asset));

        // Important: the service locks the user row before processing.
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(depositRepository.findByUser_IdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.empty());

        when(walletService.getOrCreateWallet(userId))
                .thenReturn(wallet);

        when(depositRepository.saveAndFlush(any(Deposit.class)))
                .thenAnswer(invocation -> {
                    Deposit deposit = invocation.getArgument(0);
                    deposit.setId(100L);
                    return deposit;
                });

        when(accountRepository.findByAssetAndName(eq(asset), anyString()))
                .thenReturn(Optional.empty());

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(balanceService.credit(wallet, asset, amount))
                .thenReturn(balance);

        Balance result = depositService.deposit(
                userId, " btc ", amount, key
        );

        assertNotNull(result);
        assertEquals(balance, result);

        verify(userRepository).findByIdForUpdate(userId);
        verify(depositRepository).saveAndFlush(any(Deposit.class));

        verify(ledgerService).post(
                eq(TransactionType.DEPOSIT),
                eq("SIMULATED_DEPOSIT"),
                eq(100L),
                anyString(),
                anyList()
        );

        verify(balanceService).credit(wallet, asset, amount);
    }

    @Test
    void zeroAmountIsRejectedBeforeDatabaseWork() {
        assertThrows(
                InvalidAmountException.class,
                () -> depositService.deposit(
                        userId,
                        "BTC",
                        BigDecimal.ZERO,
                        "deposit-zero"
                )
        );

        verifyNoInteractions(
                assetRepository,
                userRepository,
                depositRepository,
                walletService,
                ledgerService,
                balanceService
        );
    }

    @Test
    void unsupportedAssetIsRejected() {
        when(assetRepository.findBySymbol("DOGE"))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> depositService.deposit(
                        userId,
                        "DOGE",
                        new BigDecimal("1.0"),
                        "deposit-unsupported"
                )
        );

        verify(userRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(ledgerService);
        verifyNoInteractions(balanceService);
    }

    @Test
    void repeatedDepositDoesNotPostLedgerAgain() {
        BigDecimal amount = new BigDecimal("0.5000000000");
        String key = "deposit-repeat";

        Deposit previousDeposit = new Deposit();
        previousDeposit.setId(100L);
        previousDeposit.setUser(user);
        previousDeposit.setAsset(asset);
        previousDeposit.setAmount(amount);
        previousDeposit.setIdempotencyKey(key);

        when(assetRepository.findBySymbol("BTC"))
                .thenReturn(Optional.of(asset));

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(depositRepository.findByUser_IdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.of(previousDeposit));

        when(walletService.getOrCreateWallet(userId))
                .thenReturn(wallet);

        when(balanceService.getBalance(wallet, asset))
                .thenReturn(balance);

        Balance result = depositService.deposit(
                userId, "BTC", amount, key
        );

        assertEquals(balance, result);

        verify(ledgerService, never()).post(
                any(), any(), any(), any(), anyList()
        );

        verify(depositRepository, never()).saveAndFlush(any(Deposit.class));
        verify(balanceService, never()).credit(any(), any(), any());
    }

    @Test
    void reusingKeyWithDifferentAmountIsRejected() {
        String key = "deposit-reused";

        Deposit previousDeposit = new Deposit();
        previousDeposit.setId(100L);
        previousDeposit.setUser(user);
        previousDeposit.setAsset(asset);
        previousDeposit.setAmount(new BigDecimal("0.5000000000"));
        previousDeposit.setIdempotencyKey(key);

        when(assetRepository.findBySymbol("BTC"))
                .thenReturn(Optional.of(asset));

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(depositRepository.findByUser_IdAndIdempotencyKey(userId, key))
                .thenReturn(Optional.of(previousDeposit));

        assertThrows(
                IdempotencyConflictException.class,
                () -> depositService.deposit(
                        userId,
                        "BTC",
                        new BigDecimal("0.7500000000"),
                        key
                )
        );

        verify(ledgerService, never()).post(
                any(), any(), any(), any(), anyList()
        );

        verify(balanceService, never()).credit(any(), any(), any());
    }

    @Test
    void blankIdempotencyKeyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> depositService.deposit(
                        userId,
                        "BTC",
                        BigDecimal.ONE,
                        "   "
                )
        );

        verifyNoInteractions(
                assetRepository,
                userRepository,
                depositRepository,
                walletService,
                ledgerService,
                balanceService
        );
    }

    @Test
    void invalidUserIdIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> depositService.deposit(
                        0L,
                        "BTC",
                        BigDecimal.ONE,
                        "deposit-invalid-user"
                )
        );

        verifyNoInteractions(
                assetRepository,
                userRepository,
                depositRepository,
                walletService,
                ledgerService,
                balanceService
        );
    }
}
