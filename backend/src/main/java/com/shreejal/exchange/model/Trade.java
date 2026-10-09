package com.shreejal.exchange.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Getter
@Setter
@NoArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trading_pair_id", nullable = false)
    private TradingPair tradingPair;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maker_order_id", nullable = false)
    private Order makerOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taker_order_id", nullable = false)
    private Order takerOrder;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal price;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal quantity;

    @Column(name = "quote_quantity", nullable = false, precision = 30, scale = 10)
    private BigDecimal quoteQuantity;

    @Column(name = "maker_fee_amount", nullable = false, precision = 30, scale = 10)
    private BigDecimal makerFeeAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maker_fee_asset_id")
    private Asset makerFeeAsset;

    @Column(name = "taker_fee_amount", nullable = false, precision = 30, scale = 10)
    private BigDecimal takerFeeAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taker_fee_asset_id")
    private Asset takerFeeAsset;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (makerFeeAmount == null) {
            makerFeeAmount = BigDecimal.ZERO;
        }

        if (takerFeeAmount == null) {
            takerFeeAmount = BigDecimal.ZERO;
        }

        executedAt = Instant.now();
    }
}