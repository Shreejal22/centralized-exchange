
package com.shreejal.exchange.service;
import com.shreejal.exchange.exception.InsufficientBalanceException;
import com.shreejal.exchange.exception.InvalidAmountException;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.Balance;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.BalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import java.math.BigDecimal;
import java.util.Optional;
import com.shreejal.exchange.exception.BalanceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private Wallet wallet;

    @Mock
    private Asset asset;

    @Mock
    private Balance balance;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void lockShouldMoveFundsFromAvailableToReserved() {

        when(balanceRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(balance));

        when(balance.getAvailable())
                .thenReturn(new BigDecimal("100.0000000000"));

        when(balance.getReserved())
                .thenReturn(new BigDecimal("0.0000000000"));

        balanceService.lock(
                wallet,
                asset,
                new BigDecimal("30.0000000000")
        );

        verify(balance).setAvailable(
                eq(new BigDecimal("70.0000000000"))
        );

        verify(balance).setReserved(
                eq(new BigDecimal("30.0000000000"))
        );

        verify(balanceRepository).save(balance);
    }

    @Test
    void lockShouldThrowWhenAvailableBalanceIsInsufficient() {

        when(balanceRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(balance));

        when(balance.getAvailable())
                .thenReturn(new BigDecimal("20.0000000000"));

        InsufficientBalanceException exception = assertThrows(
                InsufficientBalanceException.class,
                () -> balanceService.lock(
                        wallet,
                        asset,
                        new BigDecimal("30.0000000000")
                )
        );

        verify(balance, never()).setAvailable(any(BigDecimal.class));
        verify(balance, never()).setReserved(any(BigDecimal.class));
        verify(balanceRepository, never()).save(balance);
    }

    @Test
    void lockShouldRejectInvalidAmounts() {

        BigDecimal[] invalidAmounts = {
                BigDecimal.ZERO,
                new BigDecimal("-10"),
                null
        };

        for (BigDecimal amount : invalidAmounts) {

            assertThrows(
                    InvalidAmountException.class,
                    () -> balanceService.lock(wallet, asset, amount)
            );
        }

        verifyNoInteractions(balanceRepository);
    }

    @Test
    void unlockShouldMoveFundsFromReservedToAvailable() {

        when(balanceRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(balance));

        when(balance.getReserved())
                .thenReturn(new BigDecimal("30.0000000000"));

        when(balance.getAvailable())
                .thenReturn(new BigDecimal("70.0000000000"));

        balanceService.unlock(
                wallet,
                asset,
                new BigDecimal("10.0000000000")
        );

        verify(balance).setReserved(
                new BigDecimal("20.0000000000")
        );

        verify(balance).setAvailable(
                new BigDecimal("80.0000000000")
        );

        verify(balanceRepository).save(balance);
    }

    @Test
    void unlockShouldThrowWhenReservedBalanceIsInsufficient() {

        when(balanceRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.of(balance));

        when(balance.getReserved())
                .thenReturn(new BigDecimal("20.0000000000"));

        assertThrows(
                InsufficientBalanceException.class,
                () -> balanceService.unlock(
                        wallet,
                        asset,
                        new BigDecimal("30.0000000000")
                )
        );

        verify(balance, never()).setReserved(any(BigDecimal.class));
        verify(balance, never()).setAvailable(any(BigDecimal.class));
        verify(balanceRepository, never()).save(balance);
    }

    @Test
    void unlockShouldRejectInvalidAmounts() {

        BigDecimal[] invalidAmounts = {
                BigDecimal.ZERO,
                new BigDecimal("-10"),
                null
        };

        for (BigDecimal amount : invalidAmounts) {
            assertThrows(
                    InvalidAmountException.class,
                    () -> balanceService.unlock(wallet, asset, amount)
            );
        }

        verifyNoInteractions(balanceRepository);
    }

    @Test
    void unlockShouldThrowWhenBalanceDoesNotExist() {

        when(balanceRepository.findByWalletAndAsset(wallet, asset))
                .thenReturn(Optional.empty());

        assertThrows(
                BalanceNotFoundException.class,
                () -> balanceService.unlock(
                        wallet,
                        asset,
                        new BigDecimal("10.0000000000")
                )
        );

        verify(balanceRepository, never()).save(balance);
    }
}