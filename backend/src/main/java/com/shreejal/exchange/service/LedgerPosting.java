
package com.shreejal.exchange.service;

import com.shreejal.exchange.model.Account;
import com.shreejal.exchange.model.LedgerEntryType;

import java.math.BigDecimal;

public record LedgerPosting(
        Account account,
        LedgerEntryType entryType,
        BigDecimal amount
) {
}