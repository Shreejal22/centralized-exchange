
package com.shreejal.exchange.service;

import jakarta.persistence.EntityNotFoundException;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.User;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.UserRepository;
import com.shreejal.exchange.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private BalanceService balanceService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void getOrCreateWallet_returnsExistingWallet() {
        Long userId = 1L;
        Wallet existingWallet = new Wallet();

        when(walletRepository.findByUser_Id(userId))
                .thenReturn(Optional.of(existingWallet));

        Wallet result = walletService.getOrCreateWallet(userId);

        assertSame(existingWallet, result);

        verify(walletRepository).findByUser_Id(userId);
        verifyNoInteractions(
                assetRepository,
                balanceService,
                userRepository
        );
    }

    @Test
    void getOrCreateWallet_rejectsNullUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> walletService.getOrCreateWallet(null)
        );

        verifyNoInteractions(
                walletRepository,
                assetRepository,
                balanceService,
                userRepository
        );
    }

    @Test
    void getOrCreateWallet_rejectsZeroUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> walletService.getOrCreateWallet(0L)
        );

        verifyNoInteractions(
                walletRepository,
                assetRepository,
                balanceService,
                userRepository
        );
    }

    @Test
    void getOrCreateWallet_rejectsNegativeUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> walletService.getOrCreateWallet(-1L)
        );

        verifyNoInteractions(
                walletRepository,
                assetRepository,
                balanceService,
                userRepository
        );
    }

    @Test
    void getOrCreateWallet_createsWalletAndInitialisesBalances() {
        Long userId = 2L;

        User user = new User();
        Asset btc = new Asset();
        Asset usdt = new Asset();
        Wallet savedWallet = new Wallet();

        when(walletRepository.findByUser_Id(userId))
                .thenReturn(Optional.empty());

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(walletRepository.save(any(Wallet.class)))
                .thenReturn(savedWallet);

        when(assetRepository.findAll())
                .thenReturn(List.of(btc, usdt));

        Wallet result = walletService.getOrCreateWallet(userId);

        assertSame(savedWallet, result);

        verify(walletRepository).save(any(Wallet.class));
        verify(balanceService).initialise(savedWallet, btc);
        verify(balanceService).initialise(savedWallet, usdt);
        verify(balanceService, times(2))
                .initialise(any(Wallet.class), any(Asset.class));
    }

    @Test
    void getOrCreateWallet_throwsWhenUserDoesNotExist() {
        Long userId = 999L;

        when(walletRepository.findByUser_Id(userId))
                .thenReturn(Optional.empty());

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> walletService.getOrCreateWallet(userId)
        );

        verify(walletRepository, never()).save(any(Wallet.class));
        verifyNoInteractions(assetRepository, balanceService);
    }
}
