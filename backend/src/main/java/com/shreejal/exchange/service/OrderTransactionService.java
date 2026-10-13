
package com.shreejal.exchange.service;

import com.shreejal.exchange.dto.PlaceOrderRequest;
import com.shreejal.exchange.matching.MatchingEngine;
import com.shreejal.exchange.matching.MatchingOrder;
import com.shreejal.exchange.matching.MatchingResult;
import com.shreejal.exchange.matching.OrderSide;
import com.shreejal.exchange.matching.OrderStatus;
import com.shreejal.exchange.matching.OrderType;
import com.shreejal.exchange.matching.TimeInForce;
import com.shreejal.exchange.model.Account;
import com.shreejal.exchange.model.AccountType;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.LedgerEntryType;
import com.shreejal.exchange.model.Order;
import com.shreejal.exchange.model.OutboxEvent;
import com.shreejal.exchange.model.Trade;
import com.shreejal.exchange.model.TradingPair;
import com.shreejal.exchange.model.TransactionType;
import com.shreejal.exchange.model.User;
import com.shreejal.exchange.model.Wallet;
import com.shreejal.exchange.repository.AccountRepository;
import com.shreejal.exchange.repository.OrderRepository;
import com.shreejal.exchange.repository.OutboxEventRepository;
import com.shreejal.exchange.repository.TradeRepository;
import com.shreejal.exchange.repository.TradingPairRepository;
import com.shreejal.exchange.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderTransactionService {

    private final UserRepository userRepository;
    private final TradingPairRepository tradingPairRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final WalletService walletService;
    private final BalanceService balanceService;
    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderTransactionService(
            UserRepository userRepository,
            TradingPairRepository tradingPairRepository,
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            WalletService walletService,
            BalanceService balanceService,
            LedgerService ledgerService,
            AccountRepository accountRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.tradingPairRepository = tradingPairRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.walletService = walletService;
        this.balanceService = balanceService;
        this.ledgerService = ledgerService;
        this.accountRepository = accountRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(
            PlaceOrderRequest request,
            MatchingEngine engine
    ) {
        User user = userRepository.findByIdForUpdate(
                request.getUserId()
        ).orElseThrow(() ->
                new EntityNotFoundException("User not found")
        );

        if (orderRepository
                .findByUser_IdAndClientOrderId(
                        user.getId(),
                        request.getClientOrderId()
                )
                .isPresent()) {
            throw new IllegalArgumentException(
                    "clientOrderId has already been used"
            );
        }

        TradingPair pair = tradingPairRepository
                .findBySymbol(request.getSymbol())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Trading pair not found: "
                                        + request.getSymbol()
                        )
                );

        validateRequest(request);

        Wallet wallet = walletService.getOrCreateWallet(
                user.getId()
        );

        BigDecimal reservedAmount = calculateReservation(
                request,
                pair,
                engine
        );

        Asset reservedAsset =
                request.getSide() == OrderSide.BUY
                        ? pair.getQuoteAsset()
                        : pair.getBaseAsset();

        if (reservedAmount.signum() > 0) {
            balanceService.lock(
                    wallet,
                    reservedAsset,
                    reservedAmount
            );
        }

        Order order = new Order();
        order.setMatchingId(UUID.randomUUID());
        order.setUser(user);
        order.setTradingPair(pair);
        order.setSide(request.getSide().name());
        order.setOrderType(request.getOrderType().name());
        order.setTimeInForce(
                request.getTimeInForce().name()
        );
        order.setPrice(request.getPrice());
        order.setQuantity(request.getQuantity());
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus("NEW");
        order.setClientOrderId(request.getClientOrderId());

        order = orderRepository.saveAndFlush(order);

        MatchingOrder matchingOrder = new MatchingOrder(
                user.getId(),
                order.getMatchingId(),
                pair.getSymbol(),
                request.getSide(),
                request.getOrderType(),
                request.getPrice(),
                request.getQuantity(),
                request.getTimeInForce(),
                order.getId()
        );

        MatchingResult result = engine.submit(matchingOrder);

        BigDecimal executedQuantity = BigDecimal.ZERO;
        BigDecimal totalQuoteSpent = BigDecimal.ZERO;

        for (com.shreejal.exchange.matching.Trade matched
                : result.getTrades()) {

            BigDecimal quantity = matched.getQuantity();

            BigDecimal quoteQuantity =
                    matched.getPrice().multiply(quantity);

            Order buyOrder = orderRepository
                    .findByMatchingId(matched.getBuyOrderId())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Buy order missing from database"
                            )
                    );

            Order sellOrder = orderRepository
                    .findByMatchingId(matched.getSellOrderId())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Sell order missing from database"
                            )
                    );

            Order makerOrder;
            Order takerOrder;

            if (order.getMatchingId().equals(
                    matched.getBuyOrderId())) {

                makerOrder = sellOrder;
                takerOrder = buyOrder;

            } else if (order.getMatchingId().equals(
                    matched.getSellOrderId())) {

                makerOrder = buyOrder;
                takerOrder = sellOrder;

            } else {
                throw new IllegalStateException(
                        "Matched trade does not involve incoming order"
                );
            }

            Trade persistedTrade = new Trade();

            persistedTrade.setTradingPair(pair);
            persistedTrade.setMakerOrder(makerOrder);
            persistedTrade.setTakerOrder(takerOrder);
            persistedTrade.setPrice(matched.getPrice());
            persistedTrade.setQuantity(quantity);
            persistedTrade.setQuoteQuantity(quoteQuantity);
            persistedTrade.setMakerFeeAmount(BigDecimal.ZERO);
            persistedTrade.setTakerFeeAmount(BigDecimal.ZERO);

            persistedTrade = tradeRepository.saveAndFlush(
                    persistedTrade
            );

            settleTrade(
                    buyOrder,
                    sellOrder,
                    pair,
                    matched.getPrice(),
                    quantity,
                    quoteQuantity,
                    persistedTrade.getId()
            );

            updateFilledOrder(buyOrder, quantity);
            updateFilledOrder(sellOrder, quantity);

            if (order.getMatchingId().equals(
                    matched.getBuyOrderId())
                    || order.getMatchingId().equals(
                    matched.getSellOrderId())) {

                executedQuantity =
                        executedQuantity.add(quantity);

                totalQuoteSpent =
                        totalQuoteSpent.add(quoteQuantity);
            }

            saveOutboxEvent(
                    "TRADE",
                    persistedTrade.getId(),
                    "TRADE_EXECUTED",
                    Map.of(
                            "tradeId", persistedTrade.getId(),
                            "pair", pair.getSymbol(),
                            "price", matched.getPrice(),
                            "quantity", quantity,
                            "quoteQuantity", quoteQuantity
                    )
            );
        }

        order.setFilledQuantity(executedQuantity);

        order.setStatus(
                databaseStatus(result.getOrder().getStatus())
        );

        if ("CANCELED".equals(order.getStatus())
                || "REJECTED".equals(order.getStatus())) {

            releaseUnfilledReservation(
                    order,
                    wallet,
                    reservedAsset,
                    reservedAmount,
                    executedQuantity,
                    totalQuoteSpent
            );
        }

        orderRepository.save(order);

        saveOutboxEvent(
                "ORDER",
                order.getId(),
                "ORDER_UPDATED",
                Map.of(
                        "orderId", order.getId(),
                        "matchingId", order.getMatchingId(),
                        "status", order.getStatus(),
                        "filledQuantity", executedQuantity
                )
        );
    }

    private void validateRequest(
            PlaceOrderRequest request
    ) {
        if (request.getQuantity() == null
                || request.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive"
            );
        }

        if (request.getSide() == null
                || request.getOrderType() == null
                || request.getTimeInForce() == null) {
            throw new IllegalArgumentException(
                    "Side, order type, and time in force are required"
            );
        }

        if (request.getOrderType() == OrderType.LIMIT
                && (request.getPrice() == null
                || request.getPrice().signum() <= 0)) {
            throw new IllegalArgumentException(
                    "Limit orders require a positive price"
            );
        }

        if (request.getOrderType() == OrderType.MARKET
                && request.getTimeInForce() != TimeInForce.IOC
                && request.getTimeInForce() != TimeInForce.FOK) {
            throw new IllegalArgumentException(
                    "Market orders must use IOC or FOK"
            );
        }
    }

    private BigDecimal calculateReservation(
            PlaceOrderRequest request,
            TradingPair pair,
            MatchingEngine engine
    ) {
        if (request.getSide() == OrderSide.SELL) {
            return request.getQuantity();
        }

        if (request.getOrderType() == OrderType.LIMIT) {
            return request.getPrice()
                    .multiply(request.getQuantity());
        }

        BigDecimal remaining = request.getQuantity();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (var level : engine.getOrderBook()
                .getAsks().entrySet()) {

            if (remaining.signum() <= 0) {
                break;
            }

            BigDecimal available = level.getValue()
                    .getTotalRemainingQuantity();

            BigDecimal taken = remaining.min(available);

            totalCost = totalCost.add(
                    level.getKey().multiply(taken)
            );

            remaining = remaining.subtract(taken);
        }

        if (totalCost.signum() <= 0) {
            throw new IllegalArgumentException(
                    "No ask-side liquidity available for market buy"
            );
        }

        return totalCost;
    }

    private void settleTrade(
            Order buyOrder,
            Order sellOrder,
            TradingPair pair,
            BigDecimal executionPrice,
            BigDecimal quantity,
            BigDecimal quoteQuantity,
            Long tradeId
    ) {
        Wallet buyerWallet = walletService.getOrCreateWallet(
                buyOrder.getUser().getId()
        );

        Wallet sellerWallet = walletService.getOrCreateWallet(
                sellOrder.getUser().getId()
        );

        balanceService.consumeReserved(
                buyerWallet,
                pair.getQuoteAsset(),
                quoteQuantity
        );

        if ("LIMIT".equals(buyOrder.getOrderType())) {
            BigDecimal priceImprovement =
                    buyOrder.getPrice()
                            .subtract(executionPrice)
                            .multiply(quantity);

            if (priceImprovement.signum() > 0) {
                balanceService.unlock(
                        buyerWallet,
                        pair.getQuoteAsset(),
                        priceImprovement
                );
            }
        }

        balanceService.consumeReserved(
                sellerWallet,
                pair.getBaseAsset(),
                quantity
        );

        balanceService.credit(
                buyerWallet,
                pair.getBaseAsset(),
                quantity
        );

        balanceService.credit(
                sellerWallet,
                pair.getQuoteAsset(),
                quoteQuantity
        );

        Account buyerQuoteAccount = getUserAccount(
                pair.getQuoteAsset(),
                buyOrder.getUser().getId()
        );

        Account sellerQuoteAccount = getUserAccount(
                pair.getQuoteAsset(),
                sellOrder.getUser().getId()
        );

        Account buyerBaseAccount = getUserAccount(
                pair.getBaseAsset(),
                buyOrder.getUser().getId()
        );

        Account sellerBaseAccount = getUserAccount(
                pair.getBaseAsset(),
                sellOrder.getUser().getId()
        );

        List<LedgerPosting> postings = new ArrayList<>();

        postings.add(new LedgerPosting(
                buyerQuoteAccount,
                LedgerEntryType.DEBIT,
                quoteQuantity
        ));

        postings.add(new LedgerPosting(
                sellerQuoteAccount,
                LedgerEntryType.CREDIT,
                quoteQuantity
        ));

        postings.add(new LedgerPosting(
                sellerBaseAccount,
                LedgerEntryType.DEBIT,
                quantity
        ));

        postings.add(new LedgerPosting(
                buyerBaseAccount,
                LedgerEntryType.CREDIT,
                quantity
        ));

        ledgerService.post(
                TransactionType.TRADE,
                "TRADE",
                tradeId,
                "Settlement for trade " + tradeId,
                postings
        );
    }

    private Account getUserAccount(
            Asset asset,
            Long userId
    ) {
        String accountName = "USER:" + userId;

        return accountRepository.findByAssetAndName(
                asset,
                accountName
        ).orElseGet(() -> {
            Account account = new Account();
            account.setAsset(asset);
            account.setName(accountName);
            account.setAccountType(AccountType.USER);

            return accountRepository.save(account);
        });
    }

    private void updateFilledOrder(
            Order order,
            BigDecimal quantity
    ) {
        BigDecimal filled = order.getFilledQuantity()
                .add(quantity);

        order.setFilledQuantity(filled);

        if (filled.compareTo(order.getQuantity()) >= 0) {
            order.setStatus("FILLED");
        } else {
            order.setStatus("PARTIALLY_FILLED");
        }

        orderRepository.save(order);
    }

    private void releaseUnfilledReservation(
            Order order,
            Wallet wallet,
            Asset asset,
            BigDecimal originalReservation,
            BigDecimal executedQuantity,
            BigDecimal totalQuoteSpent
    ) {
        BigDecimal unfilled = order.getQuantity()
                .subtract(executedQuantity);

        if (unfilled.signum() <= 0) {
            return;
        }

        BigDecimal amountToUnlock;

        if ("SELL".equals(order.getSide())) {
            amountToUnlock = unfilled;

        } else if ("LIMIT".equals(order.getOrderType())) {
            amountToUnlock = order.getPrice()
                    .multiply(unfilled);

        } else {
            amountToUnlock = originalReservation
                    .subtract(totalQuoteSpent);
        }

        if (amountToUnlock.signum() < 0) {
            throw new IllegalStateException(
                    "Calculated reservation release is negative"
            );
        }

        if (amountToUnlock.signum() > 0) {
            balanceService.unlock(
                    wallet,
                    asset,
                    amountToUnlock
            );
        }
    }

    private String databaseStatus(OrderStatus status) {
        return status.name().replace(
                "CANCELLED",
                "CANCELED"
        );
    }

    private void saveOutboxEvent(
            String aggregateType,
            Long aggregateId,
            String eventType,
            Map<String, ?> payload
    ) {
        OutboxEvent event = new OutboxEvent();

        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);

        try {
            event.setPayload(
                    objectMapper.writeValueAsString(payload)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize outbox event",
                    exception
            );
        }

        event.setRetryCount(0);

        outboxEventRepository.save(event);
    }
}
