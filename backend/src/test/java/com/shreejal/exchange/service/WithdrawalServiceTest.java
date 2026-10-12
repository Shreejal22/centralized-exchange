package com.shreejal.exchange.service;

import com.shreejal.exchange.exception.InvalidAmountException;
import com.shreejal.exchange.model.*;
import com.shreejal.exchange.repository.*;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WithdrawalServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private WalletService walletService;
    @Mock private BalanceService balanceService;
    @Mock private AccountRepository accountRepository;
    @Mock private LedgerService ledgerService;
    @Mock private WithdrawalRepository withdrawalRepository;

    @InjectMocks
    private WithdrawalService withdrawalService;

    private User user;
    private Asset asset;
    private Wallet wallet;
    private Balance balance;
    private Account userAccount;
    private Account fundingAccount;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        asset = mock(Asset.class);
        wallet = mock(Wallet.class);
        balance = mock(Balance.class);
        userAccount = mock(Account.class);
        fundingAccount = mock(Account.class);

        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(user));

        when(assetRepository.findBySymbol("USDT"))
                .thenReturn(Optional.of(asset));

        when(asset.getId()).thenReturn(10L);

        when(withdrawalRepository
                .findByUser_IdAndIdempotencyKey(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        when(walletService.getOrCreateWallet(1L))
                .thenReturn(wallet);

        when(accountRepository.findByAssetAndName(asset, "USER:1"))
                .thenReturn(Optional.of(userAccount));

        when(accountRepository.findByAssetAndName(asset, "EXCHANGE_FUNDING"))
                .thenReturn(Optional.of(fundingAccount));

        when(withdrawalRepository.saveAndFlush(any(Withdrawal.class)))
                .thenAnswer(invocation -> {
                    Withdrawal withdrawal = invocation.getArgument(0);
                    withdrawal.setId(100L);
                    return withdrawal;
                });

        when(balanceService.debit(
                wallet, asset, new BigDecimal("25.00")))
                .thenReturn(balance);
    }

    @Test
    void successfulWithdrawalPostsLedgerAndDebitsBalance() {
        Balance result = withdrawalService.withdraw(
                1L, "USDT", new BigDecimal("25.00"), "withdraw-1");

        assertSame(balance, result);

        verify(withdrawalRepository).saveAndFlush(any(Withdrawal.class));

        verify(ledgerService).post(
                eq(TransactionType.WITHDRAWAL),
                eq("SIMULATED_WITHDRAWAL"),
                eq(100L),
                contains("25.00 USDT"),
                argThat(postings ->
                        postings.size() == 2
                                && postings.get(0).account() == userAccount
                                && postings.get(0).entryType() == LedgerEntryType.DEBIT
                                && postings.get(1).account() == fundingAccount
                                && postings.get(1).entryType() == LedgerEntryType.CREDIT)
        );

        verify(balanceService).debit(
                wallet, asset, new BigDecimal("25.00"));
    }

    @Test
    void repeatedRequestWithSameKeyDoesNotDebitAgain() {
        Withdrawal previous = mock(Withdrawal.class);

        when(withdrawalRepository
                .findByUser_IdAndIdempotencyKey(1L, "withdraw-1"))
                .thenReturn(Optional.of(previous));

        when(previous.getAsset()).thenReturn(asset);
        when(previous.getAmount()).thenReturn(new BigDecimal("25.00"));
        when(balanceService.getBalance(wallet, asset)).thenReturn(balance);

        Balance result = withdrawalService.withdraw(
                1L, "USDT", new BigDecimal("25.00"), "withdraw-1");

        assertSame(balance, result);

        verify(withdrawalRepository, never()).saveAndFlush(any());
        verify(ledgerService, never()).post(
                any(), anyString(), any(), anyString(), anyList());
        verify(balanceService, never()).debit(any(), any(), any());
    }

    @Test
    void reusingKeyWithDifferentAmountThrowsConflict() {
        Withdrawal previous = mock(Withdrawal.class);

        when(withdrawalRepository
                .findByUser_IdAndIdempotencyKey(1L, "withdraw-1"))
                .thenReturn(Optional.of(previous));

        when(previous.getAsset()).thenReturn(asset);
        when(previous.getAmount()).thenReturn(new BigDecimal("25.00"));

        assertThrows(
                IdempotencyConflictException.class,
                () -> withdrawalService.withdraw(
                        1L, "USDT", new BigDecimal("30.00"), "withdraw-1")
        );

        verify(balanceService, never()).debit(any(), any(), any());
    }

    @Test
    void reusingKeyWithDifferentAssetThrowsConflict() {
        Asset btc = mock(Asset.class);
        Withdrawal previous = mock(Withdrawal.class);

        when(assetRepository.findBySymbol("BTC"))
                .thenReturn(Optional.of(btc));
        when(btc.getId()).thenReturn(20L);

        when(withdrawalRepository
                .findByUser_IdAndIdempotencyKey(1L, "withdraw-1"))
                .thenReturn(Optional.of(previous));

        when(previous.getAsset()).thenReturn(asset);
        when(previous.getAmount()).thenReturn(new BigDecimal("25.00"));

        assertThrows(
                IdempotencyConflictException.class,
                () -> withdrawalService.withdraw(
                        1L, "BTC", new BigDecimal("25.00"), "withdraw-1")
        );

        verify(balanceService, never()).debit(any(), any(), any());
    }

    @Test
    void zeroAmountIsRejected() {
        assertThrows(
                InvalidAmountException.class,
                () -> withdrawalService.withdraw(
                        1L, "USDT", BigDecimal.ZERO, "withdraw-zero")
        );

        verify(withdrawalRepository, never()).saveAndFlush(any());
        verify(balanceService, never()).debit(any(), any(), any());
    }

    @Test
    void negativeAmountIsRejected() {
        assertThrows(
                InvalidAmountException.class,
                () -> withdrawalService.withdraw(
                        1L, "USDT", new BigDecimal("-5"), "withdraw-negative")
        );

        verify(withdrawalRepository, never()).saveAndFlush(any());
        verify(balanceService, never()).debit(any(), any(), any());
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> withdrawalService.withdraw(
                        999L, "USDT", new BigDecimal("10"), "unknown-user")
        );

        verify(withdrawalRepository, never()).saveAndFlush(any());
    }

    @Test
    void blankIdempotencyKeyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> withdrawalService.withdraw(
                        1L, "USDT", new BigDecimal("10"), " ")
        );

        verify(withdrawalRepository, never()).saveAndFlush(any());
    }
}