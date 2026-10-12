package com.shreejal.exchange.service;

import com.shreejal.exchange.model.*;
import com.shreejal.exchange.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class WithdrawalServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("exchange")
                    .withUsername("exchange")
                    .withPassword("exchange");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",
                POSTGRES::getDriverClassName);
    }

    @Autowired private WithdrawalService withdrawalService;
    @Autowired private DepositService depositService;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private WalletService walletService;
    @Autowired private BalanceService balanceService;
    @Autowired private BalanceRepository balanceRepository;
    @Autowired private WithdrawalRepository withdrawalRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long userId;
    private String username;
    private Asset asset;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "");

        username = "withdraw_" + unique;
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        // users.password_hash is NOT NULL.
        user.setPasswordHash("{noop}test-password");
        user = userRepository.saveAndFlush(user);
        userId = user.getId();

        // Flyway does not seed the USDT asset in the test DB.
        // Find it or create it, so every test in this class has it.
        asset = assetRepository.findBySymbol("USDT")
                .orElseGet(() -> {
                    Asset usdt = new Asset();
                    usdt.setSymbol("USDT");
                    usdt.setName("Tether USD");
                    // IMPORTANT: if Asset has other NOT NULL columns
                    // (decimals, displayName, etc.), set them here, or
                    // this insert will fail the same way users did.
                    // e.g. usdt.setDecimals(6);
                    return assetRepository.saveAndFlush(usdt);
                });

        depositService.deposit(
                userId,
                asset.getSymbol(),
                new BigDecimal("100.00"),
                "initial-" + unique
        );
    }

    private Balance loadBalance() {
        Wallet wallet = walletService.getOrCreateWallet(userId);

        return transactionTemplate.execute(status ->
                balanceRepository.findByWalletAndAsset(wallet, asset)
                        .orElseThrow(() ->
                                new AssertionError("Balance not found")));
    }

    private BigDecimal availableBalance() {
        return loadBalance().getAvailable();
    }

    @Test
    void successfulWithdrawalPersistsWithdrawalAndReducesBalance() {
        BigDecimal before = availableBalance();

        withdrawalService.withdraw(
                userId, asset.getSymbol(),
                new BigDecimal("25.00"), "success-" + UUID.randomUUID()
        );

        assertEquals(
                0,
                before.subtract(new BigDecimal("25.00"))
                        .compareTo(availableBalance())
        );

        assertTrue(withdrawalRepository.count() > 0);
    }

    @Test
    void repeatedRequestDoesNotDebitTwice() {
        String key = "retry-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10.00");

        withdrawalService.withdraw(
                userId, asset.getSymbol(), amount, key);

        BigDecimal afterFirst = availableBalance();

        withdrawalService.withdraw(
                userId, asset.getSymbol(), amount, key);

        assertEquals(0, afterFirst.compareTo(availableBalance()));
    }

    @Test
    void insufficientFundsDoesNotPersistWithdrawal() {
        String key = "insufficient-" + UUID.randomUUID();
        long countBefore = withdrawalRepository.count();
        BigDecimal before = availableBalance();

        assertThrows(
                RuntimeException.class,
                () -> withdrawalService.withdraw(
                        userId,
                        asset.getSymbol(),
                        new BigDecimal("999999.00"),
                        key
                )
        );

        assertEquals(countBefore, withdrawalRepository.count());
        assertEquals(0, before.compareTo(availableBalance()));
    }

    @Test
    void reusingKeyWithDifferentAmountIsRejected() {
        String key = "conflict-" + UUID.randomUUID();

        withdrawalService.withdraw(
                userId, asset.getSymbol(),
                new BigDecimal("5.00"), key
        );

        BigDecimal afterFirst = availableBalance();

        assertThrows(
                IdempotencyConflictException.class,
                () -> withdrawalService.withdraw(
                        userId, asset.getSymbol(),
                        new BigDecimal("6.00"), key
                )
        );

        assertEquals(0, afterFirst.compareTo(availableBalance()));
    }

    @Test
    void withdrawalDoesNotAllowOverspendingUnderConcurrency()
            throws Exception {

        // Only 100 USDT was deposited. Two 70 USDT withdrawals
        // must not both succeed.
        String key1 = "parallel-a-" + UUID.randomUUID();
        String key2 = "parallel-b-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> first = () -> attemptWithdrawal(key1, ready, start);
        Callable<Boolean> second = () -> attemptWithdrawal(key2, ready, start);

        try {
            Future<Boolean> result1 = executor.submit(first);
            Future<Boolean> result2 = executor.submit(second);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean success1 = result1.get(30, TimeUnit.SECONDS);
            boolean success2 = result2.get(30, TimeUnit.SECONDS);

            assertNotEquals(
                    success1, success2,
                    "Exactly one withdrawal should succeed"
            );

            assertEquals(
                    0,
                    new BigDecimal("30.00").compareTo(availableBalance())
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean attemptWithdrawal(
            String key,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();
        start.await();

        try {
            withdrawalService.withdraw(
                    userId,
                    asset.getSymbol(),
                    new BigDecimal("70.00"),
                    key
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}