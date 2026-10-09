package com.shreejal.exchange.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Trade {


    private final UUID tradeId;
    private final UUID buyOrderId;
    private final UUID sellOrderId;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Instant timestamp;


    public Trade(UUID tradeId, UUID buyOrderId, UUID sellOrderId, BigDecimal price, BigDecimal quantity, Instant timestamp) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public UUID getBuyOrderId() {
        return buyOrderId;
    }

    public UUID getSellOrderId() {
        return sellOrderId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}