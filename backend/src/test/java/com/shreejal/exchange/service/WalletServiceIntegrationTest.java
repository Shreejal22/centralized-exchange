
package com.shreejal.exchange.service;

import com.shreejal.exchange.TestcontainersConfiguration;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.User;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.BalanceRepository;
import com.shreejal.exchange.repository.UserRepository;
import com.shreejal.exchange.repository.WalletRepository;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WalletServiceIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    private User createAndSaveUser(String suffix) {
        User user = new User();

        user.setUsername("wallet_test_" + suffix);
        user.setEmail("wallet_test_" + suffix + "@example.com");
        user.setPasswordHash("test-password-hash");

        return userRepository.saveAndFlush(user);
    }

    private Asset createAndSaveAsset(String symbol) {
        Asset asset = new Asset();

        asset.setSymbol(symbol);
        asset.setName(symbol + " test asset");

        return assetRepository.saveAndFlush(asset);
    }

    @Test
    @Transactional
    void getOrCreateWallet_createsWalletAndInitialisesBalances() {
        User user = createAndSaveUser("create");
        Asset btc = createAndSaveAsset("WTESTBTC");
        Asset usdt = createAndSaveAsset("WTESTUSDT");

        Wallet wallet = walletService.getOrCreateWallet(user.getId());

        assertNotNull(wallet.getId());

        assertTrue(
                walletRepository.findByUser_Id(user.getId()).isPresent()
        );

        assertEquals(
                2,
                balanceRepository.findAll().stream()
                        .filter(balance ->
                                balance.getWallet().getId()
                                        .equals(wallet.getId()))
                        .count()
        );
    }

    @Test
    @Transactional
    void getOrCreateWallet_returnsExistingWalletWithoutDuplicates() {
        User user = createAndSaveUser("existing");
        createAndSaveAsset("WEXBTC");
        createAndSaveAsset("WEXUSDT");

        Wallet first = walletService.getOrCreateWallet(user.getId());
        Long firstWalletId = first.getId();

        long balancesAfterFirstCall =
                balanceRepository.findAll().stream()
                        .filter(balance ->
                                balance.getWallet().getId()
                                        .equals(firstWalletId))
                        .count();

        Wallet second = walletService.getOrCreateWallet(user.getId());

        long balancesAfterSecondCall =
                balanceRepository.findAll().stream()
                        .filter(balance ->
                                balance.getWallet().getId()
                                        .equals(firstWalletId))
                        .count();

        assertEquals(firstWalletId, second.getId());
        assertEquals(balancesAfterFirstCall, balancesAfterSecondCall);
        assertEquals(2, balancesAfterSecondCall);
    }

    @Test
    void getOrCreateWallet_throwsWhenUserDoesNotExist() {
        assertThrows(
                EntityNotFoundException.class,
                () -> walletService.getOrCreateWallet(Long.MAX_VALUE)
        );
    }

    @Test
    @Transactional
    void getOrCreateWallet_createsOnlyOneWalletPerUser() {
        User user = createAndSaveUser("unique");
        createAndSaveAsset("WUNIBTC");
        createAndSaveAsset("WUNIUSDT");

        Wallet first = walletService.getOrCreateWallet(user.getId());
        Wallet second = walletService.getOrCreateWallet(user.getId());

        assertEquals(first.getId(), second.getId());

        assertEquals(
                1,
                walletRepository.findAll().stream()
                        .filter(wallet ->
                                wallet.getUser().getId()
                                        .equals(user.getId()))
                        .count()
        );
    }
}
