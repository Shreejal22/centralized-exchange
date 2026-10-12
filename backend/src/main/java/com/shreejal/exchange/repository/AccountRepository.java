package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Account;
import com.shreejal.exchange.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface AccountRepository extends JpaRepository<Account,Long> {
    Optional<Account> findByAssetAndName(Asset asset, String name);
}