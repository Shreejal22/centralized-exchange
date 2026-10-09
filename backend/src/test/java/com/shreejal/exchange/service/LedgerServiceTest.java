package com.shreejal.exchange.service;

import com.shreejal.exchange.model.Account;
import com.shreejal.exchange.model.Asset;
import com.shreejal.exchange.model.LedgerEntry;
import com.shreejal.exchange.model.LedgerEntryType;
import com.shreejal.exchange.model.Transaction;
import com.shreejal.exchange.model.TransactionType;
import com.shreejal.exchange.repository.LedgerEntryRepository;
import com.shreejal.exchange.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Account accountForAsset(Long assetId) {
        Asset asset = mock(Asset.class);
        Account account = mock(Account.class);

        // These stubs are shared by many tests, including tests
        // that intentionally fail validation before using them.
        lenient().when(account.getAsset()).thenReturn(asset);
        lenient().when(asset.getId()).thenReturn(assetId);

        return account;
    }

    private LedgerPosting debit(Account account, String amount) {
        return new LedgerPosting(
                account,
                LedgerEntryType.DEBIT,
                new BigDecimal(amount)
        );
    }

    private LedgerPosting credit(Account account, String amount) {
        return new LedgerPosting(
                account,
                LedgerEntryType.CREDIT,
                new BigDecimal(amount)
        );
    }

    private Transaction post(List<LedgerPosting> postings) {
        return ledgerService.post(
                TransactionType.DEPOSIT,
                "TEST",
                1L,
                "Ledger test",
                postings
        );
    }

    @Test
    void balancedPostingsShouldSaveTransactionAndEntries() {
        Account account = accountForAsset(1L);

        List<LedgerPosting> postings = List.of(
                debit(account, "100.00"),
                credit(account, "60.00"),
                credit(account, "40.00")
        );

        Transaction result = post(postings);

        assertNotNull(result);
        assertEquals(TransactionType.DEPOSIT, result.getTransactionType());

        verify(transactionRepository, times(1))
                .save(any(Transaction.class));

        verify(ledgerEntryRepository).saveAll(argThat(entries -> {
            if (entries == null) {
                return false;
            }

            int count = 0;

            for (Object item : entries) {
                LedgerEntry entry = (LedgerEntry) item;

                if (entry.getTransaction() != result) {
                    return false;
                }

                count++;
            }

            return count == 3;
        }));
    }

    @Test
    void multipleAssetsShouldBalanceIndependently() {
        Account usdtAccount = accountForAsset(1L);
        Account btcAccount = accountForAsset(2L);

        List<LedgerPosting> postings = List.of(
                debit(usdtAccount, "100.00"),
                credit(usdtAccount, "100.00"),
                debit(btcAccount, "0.50"),
                credit(btcAccount, "0.50")
        );

        assertDoesNotThrow(() -> post(postings));

        verify(transactionRepository).save(any(Transaction.class));
        verify(ledgerEntryRepository).saveAll(anyList());
    }

    @Test
    void unbalancedPostingsShouldBeRejected() {
        Account account = accountForAsset(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(account, "100.00"),
                        credit(account, "90.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void differentAssetsCannotBalanceEachOther() {
        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(accountForAsset(1L), "100.00"),
                        credit(accountForAsset(2L), "100.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void singlePostingShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(accountForAsset(1L), "10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void nullPostingsListShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> post(null)
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void nullTransactionTypeShouldBeRejected() {
        Account account = accountForAsset(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> ledgerService.post(
                        null,
                        "TEST",
                        1L,
                        "Invalid transaction",
                        List.of(
                                debit(account, "10.00"),
                                credit(account, "10.00")
                        )
                )
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void nullPostingShouldBeRejected() {
        List<LedgerPosting> postings = Arrays.asList(
                null,
                credit(accountForAsset(1L), "10.00")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> post(postings)
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void nullAccountShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        new LedgerPosting(
                                null,
                                LedgerEntryType.DEBIT,
                                new BigDecimal("10.00")
                        ),
                        credit(accountForAsset(1L), "10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void nullEntryTypeShouldBeRejected() {
        Account account = accountForAsset(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        new LedgerPosting(
                                account,
                                null,
                                new BigDecimal("10.00")
                        ),
                        credit(account, "10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void nullAmountShouldBeRejected() {
        Account account = accountForAsset(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        new LedgerPosting(
                                account,
                                LedgerEntryType.DEBIT,
                                null
                        ),
                        credit(account, "10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void accountWithoutAssetShouldBeRejected() {
        Account account = mock(Account.class);
        when(account.getAsset()).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(account, "10.00"),
                        credit(accountForAsset(1L), "10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void assetWithoutIdShouldBeRejected() {
        Asset asset = mock(Asset.class);
        Account account = mock(Account.class);

        when(account.getAsset()).thenReturn(asset);
        when(asset.getId()).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(account, "10.00"),
                        credit(accountForAsset(1L), "10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void zeroAmountShouldBeRejected() {
        Account account = accountForAsset(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(account, "0.00"),
                        credit(account, "0.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }

    @Test
    void negativeAmountShouldBeRejected() {
        Account account = accountForAsset(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> post(List.of(
                        debit(account, "-10.00"),
                        credit(account, "-10.00")
                ))
        );

        verifyNoInteractions(transactionRepository, ledgerEntryRepository);
    }
}

