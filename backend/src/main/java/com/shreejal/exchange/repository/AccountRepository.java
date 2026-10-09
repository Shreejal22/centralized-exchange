package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account,Long> {
}
