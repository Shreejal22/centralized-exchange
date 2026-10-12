package com.shreejal.exchange.repository;

import com.shreejal.exchange.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,Long> {
}
