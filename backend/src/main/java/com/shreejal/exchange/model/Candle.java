package com.shreejal.exchange.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "candles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_candle_pair_interval_time",
                        columnNames = {"trading_pair_id", "interval", "open_time"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trading_pair_id", nullable = false)
    private TradingPair tradingPair;

    @Column(nullable = false, length = 10)
    private String interval;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal open;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal high;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal low;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal close;

    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal volume;

    @Column(name = "quote_volume", nullable = false, precision = 30, scale = 10)
    private BigDecimal quoteVolume;

    @Column(name = "trade_count", nullable = false)
    private Integer tradeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (tradeCount == null) {
            tradeCount = 0;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}