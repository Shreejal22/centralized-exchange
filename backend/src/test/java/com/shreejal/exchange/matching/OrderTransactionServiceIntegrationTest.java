
package com.shreejal.exchange.matching;

import com.shreejal.exchange.TestcontainersConfiguration;
import com.shreejal.exchange.dto.PlaceOrderRequest;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.Balance;
import com.shreejal.exchange.model.Order;
import com.shreejal.exchange.model.Trade;
import com.shreejal.exchange.model.TradingPair;
import com.shreejal.exchange.model.User;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.AssetRepository;
import com.shreejal.exchange.repository.LedgerEntryRepository;
import com.shreejal.exchange.repository.OrderRepository;
import com.shreejal.exchange.repository.OutboxEventRepository;
import com.shreejal.exchange.repository.TradeRepository;
import com.shreejal.exchange.repository.TradingPairRepository;
import com.shreejal.exchange.repository.UserRepository;
import com.shreejal.exchange.service.BalanceService;
import com.shreejal.exchange.service.OrderTransactionService;
import com.shreejal.exchange.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.shreejal.exchange.service.OrderService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderTransactionServiceIntegrationTest {

    @Autowired
    private OrderTransactionService orderTransactionService;

    @Autowired
    private MatchingEngineRegistry matchingEngineRegistry;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private OrderService orderService;

    @Autowired
    private TradingPairRepository tradingPairRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldExecuteTradeAndSettleBothUsers() {

        String testId = uniqueId();

        Asset btc = findOrCreateAsset("BTC");
        Asset usdt = findOrCreateAsset("USDT");

        User buyer = createUser(
                "buyer_" + testId,
                "buyer_" + testId + "@example.com"
        );

        User seller = createUser(
                "seller_" + testId,
                "seller_" + testId + "@example.com"
        );

        String symbol = "BTC/USDT-" + testId;

        TradingPair pair = createPair(symbol, btc, usdt);

        Wallet buyerWallet =
                walletService.getOrCreateWallet(buyer.getId());

        Wallet sellerWallet =
                walletService.getOrCreateWallet(seller.getId());

        balanceService.credit(
                buyerWallet,
                usdt,
                new BigDecimal("100000")
        );

        balanceService.credit(
                sellerWallet,
                btc,
                new BigDecimal("1")
        );

        MatchingEngine engine =
                matchingEngineRegistry.getOrCreate(symbol);

        long tradesBefore = tradeRepository.count();
        long ledgerEntriesBefore = ledgerEntryRepository.count();
        long outboxEventsBefore = outboxEventRepository.count();

        PlaceOrderRequest sellRequest = createRequest(
                seller.getId(),
                symbol,
                OrderSide.SELL,
                "1",
                "sell-" + testId
        );

        orderTransactionService.execute(sellRequest, engine);

        PlaceOrderRequest buyRequest = createRequest(
                buyer.getId(),
                symbol,
                OrderSide.BUY,
                "0.5",
                "buy-" + testId
        );

        orderTransactionService.execute(buyRequest, engine);

        Order sellOrder = orderRepository
                .findByUser_IdAndClientOrderId(
                        seller.getId(),
                        "sell-" + testId
                )
                .orElseThrow();

        Order buyOrder = orderRepository
                .findByUser_IdAndClientOrderId(
                        buyer.getId(),
                        "buy-" + testId
                )
                .orElseThrow();

        assertEquals(
                tradesBefore + 1,
                tradeRepository.count()
        );

        Trade trade = tradeRepository.findAll()
                .stream()
                .filter(t -> t.getTradingPair().getId()
                        .equals(pair.getId()))
                .filter(t -> t.getQuantity()
                        .compareTo(new BigDecimal("0.5")) == 0)
                .filter(t -> t.getPrice()
                        .compareTo(new BigDecimal("50000")) == 0)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Expected trade was not persisted"
                        ));

        assertAmount("0.5", trade.getQuantity());
        assertAmount("50000", trade.getPrice());
        assertAmount("25000", trade.getQuoteQuantity());

        assertEquals("FILLED", buyOrder.getStatus());
        assertEquals("PARTIALLY_FILLED", sellOrder.getStatus());

        assertAmount("0.5", buyOrder.getFilledQuantity());
        assertAmount("0.5", sellOrder.getFilledQuantity());

        Balance buyerUsdt =
                balanceService.getBalance(buyerWallet, usdt);

        Balance buyerBtc =
                balanceService.getBalance(buyerWallet, btc);

        Balance sellerUsdt =
                balanceService.getBalance(sellerWallet, usdt);

        Balance sellerBtc =
                balanceService.getBalance(sellerWallet, btc);

        assertAmount("75000", buyerUsdt.getAvailable());
        assertAmount("0", buyerUsdt.getReserved());

        assertAmount("0.5", buyerBtc.getAvailable());
        assertAmount("0", buyerBtc.getReserved());

        assertAmount("25000", sellerUsdt.getAvailable());
        assertAmount("0", sellerUsdt.getReserved());

        assertAmount("0", sellerBtc.getAvailable());
        assertAmount("0.5", sellerBtc.getReserved());

        assertNonNegative(buyerUsdt);
        assertNonNegative(buyerBtc);
        assertNonNegative(sellerUsdt);
        assertNonNegative(sellerBtc);

        assertEquals(
                ledgerEntriesBefore + 4,
                ledgerEntryRepository.count(),
                "Expected four ledger postings for one trade"
        );

        assertTrue(
                outboxEventRepository.count()
                        >= outboxEventsBefore + 3,
                "Expected outbox events for both orders and the trade"
        );
    }

    @Test
    void shouldReturnOriginalOrderForIdenticalClientOrderRetry() {

        String testId = uniqueId();

        Asset btc = findOrCreateAsset("BTC");
        Asset usdt = findOrCreateAsset("USDT");

        User buyer = createUser(
                "retry_buyer_" + testId,
                "retry_buyer_" + testId + "@example.com"
        );

        String symbol = "BTC/USDT-" + testId;

        createPair(symbol, btc, usdt);

        Wallet buyerWallet =
                walletService.getOrCreateWallet(buyer.getId());

        balanceService.credit(
                buyerWallet,
                usdt,
                new BigDecimal("100000")
        );

        MatchingEngine engine =
                matchingEngineRegistry.getOrCreate(symbol);

        String clientOrderId = "retry-" + testId;

        PlaceOrderRequest originalRequest = createRequest(
                buyer.getId(),
                symbol,
                OrderSide.BUY,
                "0.5",
                clientOrderId
        );

        long ordersBefore = orderRepository.count();
        long tradesBefore = tradeRepository.count();
        long outboxBefore = outboxEventRepository.count();

        Order originalOrder = orderTransactionService.execute(
                originalRequest,
                engine
        );

        Order retryResult = orderTransactionService.execute(
                originalRequest,
                engine
        );

        assertNotNull(originalOrder.getId());

        assertEquals(
                originalOrder.getId(),
                retryResult.getId(),
                "Retry should return the original order"
        );

        assertEquals(
                originalOrder.getMatchingId(),
                retryResult.getMatchingId(),
                "Retry must not create a new matching ID"
        );

        assertEquals(
                ordersBefore + 1,
                orderRepository.count(),
                "Retry must not create another database order"
        );

        assertEquals(
                tradesBefore,
                tradeRepository.count(),
                "Retry must not execute another trade"
        );

        assertEquals(
                outboxBefore + 1,
                outboxEventRepository.count(),
                "Retry must not publish another order event"
        );

        Balance quoteBalance =
                balanceService.getBalance(buyerWallet, usdt);

        assertAmount("75000", quoteBalance.getAvailable());
        assertAmount("25000", quoteBalance.getReserved());
    }

    @Test
    void shouldRejectReusingClientOrderIdWithDifferentRequest() {

        String testId = uniqueId();

        Asset btc = findOrCreateAsset("BTC");
        Asset usdt = findOrCreateAsset("USDT");

        User buyer = createUser(
                "mismatch_buyer_" + testId,
                "mismatch_buyer_" + testId + "@example.com"
        );

        String symbol = "BTC/USDT-" + testId;

        createPair(symbol, btc, usdt);

        Wallet buyerWallet =
                walletService.getOrCreateWallet(buyer.getId());

        balanceService.credit(
                buyerWallet,
                usdt,
                new BigDecimal("100000")
        );

        MatchingEngine engine =
                matchingEngineRegistry.getOrCreate(symbol);

        String clientOrderId = "mismatch-" + testId;

        PlaceOrderRequest originalRequest = createRequest(
                buyer.getId(),
                symbol,
                OrderSide.BUY,
                "0.5",
                clientOrderId
        );

        Order originalOrder = orderTransactionService.execute(
                originalRequest,
                engine
        );

        long ordersAfterOriginal = orderRepository.count();
        long tradesAfterOriginal = tradeRepository.count();

        PlaceOrderRequest differentRequest = createRequest(
                buyer.getId(),
                symbol,
                OrderSide.BUY,
                "0.25",
                clientOrderId
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderTransactionService.execute(
                        differentRequest,
                        engine
                ),
                "Same client order ID with a different quantity must be rejected"
        );

        Order storedOrder = orderRepository
                .findByUser_IdAndClientOrderId(
                        buyer.getId(),
                        clientOrderId
                )
                .orElseThrow();

        assertEquals(originalOrder.getId(), storedOrder.getId());

        assertAmount("0.5", storedOrder.getQuantity());

        assertEquals(
                ordersAfterOriginal,
                orderRepository.count(),
                "Mismatched retry must not create another order"
        );

        assertEquals(
                tradesAfterOriginal,
                tradeRepository.count(),
                "Mismatched retry must not execute trades"
        );

        Balance quoteBalance =
                balanceService.getBalance(buyerWallet, usdt);

        assertAmount("75000", quoteBalance.getAvailable());
        assertAmount("25000", quoteBalance.getReserved());
    }

    private String uniqueId() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
    }

    private Asset findOrCreateAsset(String symbol) {
        return assetRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    Asset asset = new Asset();
                    asset.setSymbol(symbol);

                    if (symbol.equals("BTC")) {
                        asset.setName("Bitcoin");
                    } else if (symbol.equals("USDT")) {
                        asset.setName("Tether USD");
                    }

                    return assetRepository.saveAndFlush(asset);
                });
    }

    private TradingPair createPair(
            String symbol,
            Asset btc,
            Asset usdt
    ) {
        TradingPair pair = new TradingPair();
        pair.setSymbol(symbol);
        pair.setBaseAsset(btc);
        pair.setQuoteAsset(usdt);

        return tradingPairRepository.saveAndFlush(pair);
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("integration-test-password-hash");

        return userRepository.saveAndFlush(user);
    }

    private PlaceOrderRequest createRequest(
            Long userId,
            String symbol,
            OrderSide side,
            String quantity,
            String clientOrderId
    ) {
        PlaceOrderRequest request = new PlaceOrderRequest();

        request.setUserId(userId);
        request.setSymbol(symbol);
        request.setSide(side);
        request.setOrderType(OrderType.LIMIT);
        request.setTimeInForce(TimeInForce.GTC);
        request.setQuantity(new BigDecimal(quantity));
        request.setPrice(new BigDecimal("50000"));
        request.setClientOrderId(clientOrderId);

        return request;
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {
        assertNotNull(actual, "Amount must not be null");

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Unexpected amount"
        );
    }

    private void assertNonNegative(Balance balance) {
        assertNotNull(balance.getAvailable());
        assertNotNull(balance.getReserved());

        assertTrue(
                balance.getAvailable().signum() >= 0,
                "Available balance must not be negative"
        );

        assertTrue(
                balance.getReserved().signum() >= 0,
                "Reserved balance must not be negative"
        );
    }
    @Test
    void concurrentIdenticalRequestsShouldCreateOnlyOneOrder()
            throws Exception {

        String testId = uniqueId();

        Asset btc = findOrCreateAsset("BTC");
        Asset usdt = findOrCreateAsset("USDT");

        User buyer = createUser(
                "concurrent_buyer_" + testId,
                "concurrent_buyer_" + testId + "@example.com"
        );

        String symbol = "BTC/USDT-" + testId;

        createPair(symbol, btc, usdt);

        Wallet buyerWallet =
                walletService.getOrCreateWallet(buyer.getId());

        balanceService.credit(
                buyerWallet,
                usdt,
                new BigDecimal("100000")
        );

        String clientOrderId = "concurrent-" + testId;

        PlaceOrderRequest request = createRequest(
                buyer.getId(),
                symbol,
                OrderSide.BUY,
                "0.5",
                clientOrderId
        );

        long ordersBefore = orderRepository.count();
        long tradesBefore = tradeRepository.count();
        long outboxBefore = outboxEventRepository.count();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Order> firstRequest = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start first request"
                    );
                }

                return orderService.placeOrder(request);
            });

            Future<Order> secondRequest = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start second request"
                    );
                }

                return orderService.placeOrder(request);
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both requests should be ready"
            );

            start.countDown();

            Order firstOrder = firstRequest.get(30, TimeUnit.SECONDS);
            Order secondOrder = secondRequest.get(30, TimeUnit.SECONDS);

            assertNotNull(firstOrder.getId());
            assertNotNull(secondOrder.getId());

            assertEquals(
                    firstOrder.getId(),
                    secondOrder.getId(),
                    "Concurrent identical requests must return the same order"
            );

            assertEquals(
                    firstOrder.getMatchingId(),
                    secondOrder.getMatchingId(),
                    "Both requests must refer to the same matching order"
            );

            assertEquals(
                    ordersBefore + 1,
                    orderRepository.count(),
                    "Only one order should be created"
            );

            assertEquals(
                    tradesBefore,
                    tradeRepository.count(),
                    "The unmatched limit order must not create trades"
            );

            assertEquals(
                    outboxBefore + 1,
                    outboxEventRepository.count(),
                    "Only one order event should be created"
            );

            Balance quoteBalance =
                    balanceService.getBalance(buyerWallet, usdt);

            assertAmount("75000", quoteBalance.getAvailable());
            assertAmount("25000", quoteBalance.getReserved());

            assertNonNegative(quoteBalance);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Concurrent request threads should terminate"
            );
        }
    }
}
