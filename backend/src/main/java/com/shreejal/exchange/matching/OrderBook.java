package com.shreejal.exchange.matching;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

public class OrderBook {
    private final String symbol;

    public String getSymbol() {
        return symbol;
    }

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    private final TreeMap<BigDecimal, PriceLevel> bids=new TreeMap<>(Comparator.reverseOrder());

    private final TreeMap<BigDecimal,PriceLevel> asks= new TreeMap<>();

    public void addOrder(MatchingOrder order){
        if(order.getType()!=OrderType.LIMIT){
            throw new IllegalArgumentException(
                    "Only limit orders can be added to the order book"
            );
        }

        TreeMap<BigDecimal,PriceLevel> levels=order.getSide()==OrderSide.BUY?bids:asks;

        PriceLevel priceLevel=levels.computeIfAbsent(order.getPrice(),PriceLevel::new);

        priceLevel.addOrder(order);
    }

    public PriceLevel getBestBid(){
        return bids.isEmpty()?null:bids.firstEntry().getValue();
    }
    public PriceLevel getBestAsk(){
        return asks.isEmpty()?null:asks.firstEntry().getValue();
    }
    public void removeEmptyBidLevel() {
        if (!bids.isEmpty() && bids.firstEntry().getValue().isEmpty()) {
            bids.pollFirstEntry();
        }
    }

    public void removeEmptyAskLevel() {
        if (!asks.isEmpty() && asks.firstEntry().getValue().isEmpty()) {
            asks.pollFirstEntry();
        }
    }
    public NavigableMap<BigDecimal, PriceLevel> getBids() {
        return Collections.unmodifiableNavigableMap(bids);
    }

    public NavigableMap<BigDecimal, PriceLevel> getAsks() {
        return Collections.unmodifiableNavigableMap(asks);
    }
    public boolean removeOrder(MatchingOrder order) {

        TreeMap<BigDecimal, PriceLevel> levels;

        if (order.getSide() == OrderSide.BUY) {
            levels = bids;
        } else {
            levels = asks;
        }

        PriceLevel level =
                levels.get(order.getPrice());

        if (level == null) {
            return false;
        }

        boolean removed =
                level.removeOrder(order);

        if (!removed) {
            return false;
        }

        if (level.isEmpty()) {
            levels.remove(order.getPrice());
        }

        return true;
    }

    public void clear() {
        bids.clear();
        asks.clear();
    }
}
