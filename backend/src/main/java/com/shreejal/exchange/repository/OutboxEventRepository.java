
package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {
}
