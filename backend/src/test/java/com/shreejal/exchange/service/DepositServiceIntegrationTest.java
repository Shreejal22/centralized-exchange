
package com.shreejal.exchange.service;

import com.shreejal.exchange.model.*;
import com.shreejal.exchange.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class DepositServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("exchange_test")
                    .withUsername("exchange")
                    .withPassword("exchange");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                postgres::getDriverClassName
        );
    }

    @Autowired
    private DepositService depositService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private DepositRepository depositRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private BalanceService balanceService;

    private User user;
    private Asset asset;

    @BeforeEach
    void setUp() {
        // Unique username and email for every test execution.
        String uniqueId = UUID.randomUUID().toString();

        user = new User();
        user.setUsername("rollback-" + uniqueId);
        user.setEmail("rollback-" + uniqueId + "@example.com");
        user.setPasswordHash("test-password-hash");
        user = userRepository.saveAndFlush(user);

        // Reuse USDT if it already exists in the test database.
        asset = assetRepository.findBySymbol("USDT")
                .orElseGet(() -> {
                    Asset newAsset = new Asset();
                    newAsset.setSymbol("USDT");
                    newAsset.setName("Tether USD");
                    return assetRepository.saveAndFlush(newAsset);
                });

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet = walletRepository.saveAndFlush(wallet);

        Balance balance = new Balance();
        balance.setWallet(wallet);
        balance.setAsset(asset);
        balance.setAvailable(BigDecimal.ZERO);
        balance.setReserved(BigDecimal.ZERO);

        balanceRepository.saveAndFlush(balance);
    }

    @Test
    void failedBalanceCreditRollsBackDepositAndLedger() {
        long depositsBefore = depositRepository.count();
        long transactionsBefore = transactionRepository.count();
        long entriesBefore = ledgerEntryRepository.count();

        doThrow(new RuntimeException("Simulated balance failure"))
                .when(balanceService)
                .credit(
                        any(Wallet.class),
                        any(Asset.class),
                        any(BigDecimal.class)
                );

        assertThrows(
                RuntimeException.class,
                () -> depositService.deposit(
                        user.getId(),
                        "USDT",
                        new BigDecimal("100.00"),
                        "rollback-deposit-" + UUID.randomUUID()
                )
        );

        assertEquals(
                depositsBefore,
                depositRepository.count(),
                "Deposit record should have rolled back"
        );

        assertEquals(
                transactionsBefore,
                transactionRepository.count(),
                "Ledger transaction should have rolled back"
        );

        assertEquals(
                entriesBefore,
                ledgerEntryRepository.count(),
                "Ledger entries should have rolled back"
        );
    }

    @Test
    void concurrentDuplicateDepositsAreProcessedOnlyOnce()
            throws Exception {

        BigDecimal amount = new BigDecimal("100.00");
        String key = "concurrent-" + UUID.randomUUID();
        Long userId = user.getId();

        Wallet wallet = walletRepository.findByUser_Id(userId)
                .orElseThrow();

        Asset currentAsset = assetRepository.findBySymbol("USDT")
                .orElseThrow();

        // The repository query uses a pessimistic lock, so run it
        // inside a real transaction.
        Balance balance = loadBalance(wallet, currentAsset);

        when(balanceService.credit(
                any(Wallet.class),
                any(Asset.class),
                any(BigDecimal.class)
        )).thenReturn(balance);

        when(balanceService.getBalance(
                any(Wallet.class),
                any(Asset.class)
        )).thenReturn(balance);

        long depositsBefore = depositRepository.count();
        long transactionsBefore = transactionRepository.count();
        long entriesBefore = ledgerEntryRepository.count();

        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            Future<Balance> first = executor.submit(() -> {
                start.await();
                return depositService.deposit(
                        userId, "USDT", amount, key
                );
            });

            Future<Balance> second = executor.submit(() -> {
                start.await();
                return depositService.deposit(
                        userId, "USDT", amount, key
                );
            });

            // Release both workers as close together as possible.
            start.countDown();

            assertNotNull(first.get(15, TimeUnit.SECONDS));
            assertNotNull(second.get(15, TimeUnit.SECONDS));
        }

        assertEquals(
                depositsBefore + 1,
                depositRepository.count(),
                "Only one deposit should be created"
        );

        assertEquals(
                transactionsBefore + 1,
                transactionRepository.count(),
                "Only one ledger transaction should be created"
        );

        assertEquals(
                entriesBefore + 2,
                ledgerEntryRepository.count(),
                "Exactly two ledger entries should be created"
        );

        verify(balanceService, times(1)).credit(
                any(Wallet.class),
                any(Asset.class),
                any(BigDecimal.class)
        );
    }

    private Balance loadBalance(Wallet wallet, Asset asset) {
        return transactionTemplate.execute(status ->
                balanceRepository.findByWalletAndAsset(wallet, asset)
                        .orElseThrow()
        );
    }
}
