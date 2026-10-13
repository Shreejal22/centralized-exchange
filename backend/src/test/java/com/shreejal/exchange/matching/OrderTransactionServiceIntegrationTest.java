
package com.shreejal.exchange.matching;


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
import com.shreejal.exchange.TestcontainersConfiguration;

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

        String testId = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        // Ensure assets exist in the Testcontainers database.
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

        TradingPair newPair = new TradingPair();
        newPair.setSymbol(symbol);
        newPair.setBaseAsset(btc);
        newPair.setQuoteAsset(usdt);

        final TradingPair pair =
                tradingPairRepository.saveAndFlush(newPair);

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

        // Seller offers 1 BTC at 50,000 USDT.
        PlaceOrderRequest sellRequest = createRequest(
                seller.getId(),
                symbol,
                OrderSide.SELL,
                "1",
                "sell-" + testId
        );

        orderTransactionService.execute(sellRequest, engine);

        // Buyer purchases 0.5 BTC at 50,000 USDT.
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

        assertEquals(tradesBefore + 1, tradeRepository.count());

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

    private void assertAmount(String expected, BigDecimal actual) {
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
}
