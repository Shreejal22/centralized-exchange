package com.shreejal.exchange.matching;

import java.util.List;

public class MatchingResult {
    private final List<Trade> trades;
    private final MatchingOrder order;

    public List<Trade> getTrades() {
        return trades;
    }

    public MatchingOrder getOrder() {
        return order;
    }

    public MatchingResult(List<Trade> trades, MatchingOrder order) {
        this.trades = trades;
        this.order = order;
    }

}
