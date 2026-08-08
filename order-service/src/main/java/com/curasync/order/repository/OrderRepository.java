package com.curasync.order.repository;

import com.curasync.order.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByCustomerEmail(String email);

    List<Order> findByCustomerPhoneOrderByCreatedAtDesc(String phone);

    Optional<Order> findByOrderId(String orderId);

    List<Order> findByStatus(String status);

    @Query("{ 'items.productId': ?0 }")
    List<Order> findByItemsProductId(String productId);
}
