package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.session.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    private static final PortalPrincipal USER_SEVEN = new PortalPrincipal(7L, "alice", "browser-access-token");

    @Mock
    private PaymentOrderRepository orders;

    @Captor
    private ArgumentCaptor<PaymentOrder> savedOrder;

    private PaymentOrderService service;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        service = new PaymentOrderService(orders, properties);
    }

    @Test
    void convertsDecimalDollarsToIntegerCentsAndQuotaWithoutFloatingPoint() {
        returnSavedOrder();
        PaymentOrderView order = service.createForUser(USER_SEVEN, new BigDecimal("25.50"));

        verify(orders).save(savedOrder.capture());
        assertThat(order.amountUsdMinor()).isEqualTo(2_550L);
        assertThat(order.quotaToCredit()).isEqualTo(12_750_000L);
        assertThat(savedOrder.getValue().getNewApiUserId()).isEqualTo(USER_SEVEN.userId());
        assertThat(savedOrder.getValue().getAmountUsdMinor()).isEqualTo(2_550L);
        assertThat(savedOrder.getValue().getQuotaToCredit()).isEqualTo(12_750_000L);
    }

    @Test
    void calculatesQuotaFromTheConfiguredPerDollarRate() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(600_000L);
        PaymentOrderService configuredService = new PaymentOrderService(orders, properties);
        returnSavedOrder();

        PaymentOrderView order = configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThat(order.amountUsdMinor()).isEqualTo(100L);
        assertThat(order.quotaToCredit()).isEqualTo(600_000L);
    }

    @Test
    void rejectsAConfiguredQuotaRateThatCannotRepresentEveryCentAmount() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(500_001L);
        PaymentOrderService configuredService = new PaymentOrderService(orders, properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void rejectsQuotaAboveTheNewApiWalletLimitBeforeSavingTheOrder() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(9_007_199_254_741_000L);
        PaymentOrderService configuredService = new PaymentOrderService(orders, properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void calculatesQuotaAtTheLargestSupportedConfiguredRate() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(9_007_199_254_740_900L);
        PaymentOrderService configuredService = new PaymentOrderService(orders, properties);
        returnSavedOrder();

        PaymentOrderView order = configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThat(order.quotaToCredit()).isEqualTo(9_007_199_254_740_900L);
    }

    @Test
    void rejectsAConfiguredQuotaRateWhenTheFinalQuotaOverflows() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(9_223_372_036_854_775_800L);
        PaymentOrderService configuredService = new PaymentOrderService(orders, properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("10000.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void acceptsTheInclusiveMinimumAndMaximumAmounts() {
        returnSavedOrder();
        PaymentOrderView minimum = service.createForUser(USER_SEVEN, new BigDecimal("1.00"));
        PaymentOrderView maximum = service.createForUser(USER_SEVEN, new BigDecimal("10000.00"));

        assertThat(minimum.amountUsdMinor()).isEqualTo(100L);
        assertThat(maximum.amountUsdMinor()).isEqualTo(1_000_000L);
    }

    @Test
    void rejectsAmountsOutsideTheAllowedScaleAndRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.createForUser(USER_SEVEN, new BigDecimal("1.001")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.createForUser(USER_SEVEN, new BigDecimal("0.99")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.createForUser(USER_SEVEN, new BigDecimal("10000.01")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.createForUser(USER_SEVEN, new BigDecimal("-1.00")));

        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void createsOpaqueNonSequentialSecureOrderNumbers() {
        returnSavedOrder();
        PaymentOrderView first = service.createForUser(USER_SEVEN, new BigDecimal("1.00"));
        PaymentOrderView second = service.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThat(first.orderNo()).matches("PO_[A-Za-z0-9_-]{32}");
        assertThat(second.orderNo()).matches("PO_[A-Za-z0-9_-]{32}");
        assertThat(second.orderNo()).isNotEqualTo(first.orderNo());
    }

    @Test
    void neverReturnsAnOrderOwnedByAnotherPortalPrincipal() {
        PaymentOrder someoneElsesOrder = PaymentOrder.paypal(
                "PO_FOREIGN", 8L, 100L, 500_000L, Instant.now(), Instant.now().plusSeconds(60));
        when(orders.findByOrderNo("PO_FOREIGN")).thenReturn(Optional.of(someoneElsesOrder));

        Optional<PaymentOrderView> result = service.findForUser(USER_SEVEN, "PO_FOREIGN");

        assertThat(result).isEmpty();
    }

    @Test
    void listsOnlyOrdersSelectedByThePortalPrincipalUserId() {
        PaymentOrder ownOrder = PaymentOrder.paypal(
                "PO_OWN", 7L, 100L, 500_000L, Instant.now(), Instant.now().plusSeconds(60));
        when(orders.findByNewApiUserIdOrderByCreatedAtDesc(USER_SEVEN.userId())).thenReturn(List.of(ownOrder));

        List<PaymentOrderView> result = service.listForUser(USER_SEVEN);

        assertThat(result).extracting(PaymentOrderView::orderNo).containsExactly("PO_OWN");
        verify(orders).findByNewApiUserIdOrderByCreatedAtDesc(USER_SEVEN.userId());
    }

    private void returnSavedOrder() {
        when(orders.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
