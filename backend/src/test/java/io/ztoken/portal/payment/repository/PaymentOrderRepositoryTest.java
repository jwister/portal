package io.ztoken.portal.payment.repository;

import io.ztoken.portal.payment.domain.PaymentOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentOrderRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Autowired
    private PaymentOrderRepository orders;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearOrders() {
        orders.deleteAll();
    }

    @Test
    void v2MigrationCreatesPaymentOrderTable() {
        Integer tableCount = jdbcTemplate.queryForObject("select count(*) from payment_orders", Integer.class);

        assertThat(tableCount).isZero();
    }

    @Test
    void findsOrderByNumberWithPessimisticWriteLock() throws Exception {
        PaymentOrder saved = orders.saveAndFlush(PaymentOrder.paypal(
                "PO-LOCK", 7L, 500L, 2_500_000L, NOW, NOW.plusSeconds(30)));
        entityManager.clear();

        PaymentOrder locked = orders.findByOrderNoForUpdate("PO-LOCK").orElseThrow();
        Method lockingMethod = PaymentOrderRepository.class
                .getMethod("findByOrderNoForUpdate", String.class);

        assertThat(locked.getId()).isEqualTo(saved.getId());
        assertThat(lockingMethod.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
