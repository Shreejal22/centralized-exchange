
package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WithdrawalRepository
        extends JpaRepository<Withdrawal, Long> {

    Optional<Withdrawal> findByUser_IdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );
}
