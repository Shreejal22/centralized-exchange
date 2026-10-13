
package com.shreejal.exchange.dto;

import com.shreejal.exchange.matching.OrderSide;
import com.shreejal.exchange.matching.OrderType;
import com.shreejal.exchange.matching.TimeInForce;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class PlaceOrderRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotBlank
    @Size(max = 30)
    private String symbol;

    @NotNull
    private OrderSide side;

    @NotNull
    private OrderType orderType;

    @NotNull
    private TimeInForce timeInForce;

    @NotNull
    @DecimalMin("0.0000000001")
    @Digits(integer = 20, fraction = 10)
    private BigDecimal quantity;

    @Digits(integer = 20, fraction = 10)
    private BigDecimal price;

    @NotBlank
    @Size(max = 100)
    private String clientOrderId;

    @AssertTrue(message =
            "LIMIT orders require a positive price; MARKET orders must omit price")
    public boolean isPriceConfigurationValid() {
        if (orderType == null) {
            return true;
        }

        if (orderType == OrderType.LIMIT) {
            return price != null && price.signum() > 0;
        }

        return price == null;
    }

    @AssertTrue(message =
            "POST_ONLY is supported only for LIMIT orders")
    public boolean isTimeInForceConfigurationValid() {
        return timeInForce != TimeInForce.POST_ONLY
                || orderType == OrderType.LIMIT;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }
}
