package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.team12.teamproject.entity.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByAccountIdOrderByOrderedAtDesc(Long accountId);

    List<Order> findByAccount_User_IdOrderByOrderedAtDesc(Long userId);

    @Query(value = """
        SELECT CASE
                 WHEN COUNT(*) > 0 THEN 1
                 ELSE 0
               END
        FROM orders o
        JOIN accounts a ON a.account_id = o.account_id
        JOIN users u ON u.user_id = a.user_id
        WHERE u.user_id = :userId
          AND o.stock_id = :stockId
          AND UPPER(o.order_side) = UPPER(:orderSide)
          AND UPPER(o.order_status) = UPPER(:orderStatus)
        """, nativeQuery = true)
    int existsByAccountUserIdAndStockIdAndOrderSideIgnoreCaseAndOrderStatusIgnoreCase(
            @Param("userId") Long userId,
            @Param("stockId") Long stockId,
            @Param("orderSide") String orderSide,
            @Param("orderStatus") String orderStatus
    );

    @Query("""
        SELECT COUNT(o)
        FROM Order o
        WHERE o.account.user.id = :userId
          AND o.orderStatus = 'COMPLETED'
    """)
    long countCompletedOrdersByUserId(@Param("userId") Long userId);
}