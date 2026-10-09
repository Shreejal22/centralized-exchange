package com.shreejal.exchange.service;

import com.shreejal.exchange.model.Account;
import com.shreejal.exchange.model.LedgerEntry;
import com.shreejal.exchange.model.Transaction;
import com.shreejal.exchange.model.TransactionType;
import com.shreejal.exchange.repository.LedgerEntryRepository;
import com.shreejal.exchange.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class LedgerService {
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(TransactionRepository transactionRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }
    @Transactional
    public Transaction post(
            TransactionType transactionType, String referenceType, Long referenceId, String description, List<LedgerPosting> postings
            ){
        if (transactionType == null) {
            throw new IllegalArgumentException(
                    "Transaction type is required"
            );
        }

        if (postings == null || postings.size() < 2) {
            throw new IllegalArgumentException(
                    "At least two ledger postings are required"
            );
        }
        Map<Long , BigDecimal[]> totalsByAsset=new HashMap<>();
        for(LedgerPosting posting:postings) {
            if (posting == null
                    || posting.account() == null
                    || posting.entryType() == null
                    || posting.amount() == null) {

                throw new IllegalArgumentException(
                        "Posting, account, entry type, and amount are required"
                );
            }
            if (posting.amount().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Ledger amount must be positive"
                );
            }
            Account account = posting.account();
            if (account.getAsset() == null
                    || account.getAsset().getId() == null) {

                throw new IllegalArgumentException(
                        "Every account must have a persisted asset"
                );
            }

            Long assetId = account.getAsset().getId();
            BigDecimal[] totals = totalsByAsset.computeIfAbsent(
                    assetId,
                    ignored -> new BigDecimal[]{
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    }
            );
            switch (posting.entryType()) {
                case DEBIT -> totals[0] = totals[0].add(posting.amount());

                case CREDIT -> totals[1] = totals[1].add(posting.amount());

            }
        }
            for(Map.Entry<Long,BigDecimal[]> entry:totalsByAsset.entrySet()){
                BigDecimal debits=entry.getValue()[0];
                BigDecimal credits=entry.getValue()[1];

                if (debits.compareTo(credits) != 0) {
                    throw new IllegalArgumentException(
                            "Ledger postings must balance for asset ID "
                                    + entry.getKey()
                    );
                }
            }

            Transaction transaction=new Transaction();
            transaction.setTransactionType(transactionType);
            transaction.setReferenceType(referenceType);
            transaction.setReferenceId(referenceId);
            transaction.setDescription(description);
            Transaction savedTransaction=transactionRepository.save(transaction);

            List<LedgerEntry> entries=new ArrayList<>();

            for(LedgerPosting posting:postings){

                LedgerEntry entry= new LedgerEntry();
                entry.setTransaction(transaction);
                entry.setAccount(posting.account());
                entry.setAmount(posting.amount());
                entry.setEntryType(posting.entryType());
                entries.add(entry);
            }
            ledgerEntryRepository.saveAll(entries);
            return savedTransaction;

    }
}
