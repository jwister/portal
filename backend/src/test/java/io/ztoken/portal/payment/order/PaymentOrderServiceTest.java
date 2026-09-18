package io.ztoken.portal.payment.order;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.provider.PaymentProvider;
import io.ztoken.portal.payment.provider.PaymentProviderRegistry;
import io.ztoken.portal.payment.provider.PayPalPaymentProvider;
import io.ztoken.portal.session.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    private static final PortalPrincipal USER_SEVEN = new PortalPrincipal(7L, "alice", "browser-access-token");

    @Mock
    private PaymentOrderRepository orders;

    @Mock
    private PaymentProvider trc20Provider;

    @Captor
    private ArgumentCaptor<PaymentOrder> savedOrder;

    private PaymentOrderService service;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(0);
        service = service(properties);
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
        PaymentOrderService configuredService = service(properties);
        returnSavedOrder();

        PaymentOrderView order = configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThat(order.amountUsdMinor()).isEqualTo(100L);
        assertThat(order.quotaToCredit()).isEqualTo(600_000L);
    }

    @Test
    void rejectsAConfiguredQuotaRateThatCannotRepresentEveryCentAmount() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(500_001L);
        PaymentOrderService configuredService = service(properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void capsTheDefaultQuotaRateAtTheWalletSafeAmountBeforeSaving() {
        returnSavedOrder();

        PaymentOrderView accepted = service.createForUser(USER_SEVEN, new BigDecimal("4294.96"));

        assertThat(accepted.amountUsdMinor()).isEqualTo(429_496L);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createForUser(USER_SEVEN, new BigDecimal("4294.97")))
                .withMessage("Payment amount exceeds the NewAPI wallet limit");
        verify(orders, times(1)).save(any(PaymentOrder.class));
    }

    @Test
    void rejectsQuotaAboveTheDefaultNewApiWalletLimitBeforeSavingTheOrder() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(2_147_483_700L);
        PaymentOrderService configuredService = service(properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void rejectsQuotaAboveTheConfiguredNewApiWalletLimitBeforeSavingTheOrder() {
        PaymentProperties properties = new PaymentProperties();
        properties.getNewApiCredit().setMaxWalletQuota(1_000_000L);
        properties.setQuotaPerUsd(1_000_100L);
        PaymentOrderService configuredService = service(properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void calculatesQuotaAtTheConfiguredNewApiWalletLimit() {
        PaymentProperties properties = new PaymentProperties();
        properties.getNewApiCredit().setMaxWalletQuota(2_147_483_600L);
        properties.setQuotaPerUsd(2_147_483_600L);
        PaymentOrderService configuredService = service(properties);
        returnSavedOrder();

        PaymentOrderView order = configuredService.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThat(order.quotaToCredit()).isEqualTo(2_147_483_600L);
    }

    @Test
    void rejectsAConfiguredQuotaRateWhenNoPaymentAmountFitsTheWalletLimit() {
        PaymentProperties properties = new PaymentProperties();
        properties.setQuotaPerUsd(9_223_372_036_854_775_800L);
        PaymentOrderService configuredService = service(properties);

        assertThatIllegalArgumentException().isThrownBy(
                () -> configuredService.createForUser(USER_SEVEN, new BigDecimal("10000.00")));
        verify(orders, never()).save(any(PaymentOrder.class));
    }

    @Test
    void acceptsTheInclusiveMinimumAndMaximumAmounts() {
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(0);
        properties.setQuotaPerUsd(200_000L);
        PaymentOrderService lowerRateService = service(properties);
        returnSavedOrder();
        PaymentOrderView minimum = lowerRateService.createForUser(USER_SEVEN, new BigDecimal("1.00"));
        PaymentOrderView maximum = lowerRateService.createForUser(USER_SEVEN, new BigDecimal("10000.00"));

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
    void delegatesTrc20OrderCreationToTheAddressPoolWithoutTrustingClientPaymentInstructions() {
        PaymentOrder trc20Order = PaymentOrder.usdtTrc20("PO_TRON", 7L, 100L, 500_000L,
                new PaymentAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", Instant.now()),
                1_000_001L, Instant.now(), Instant.now().plusSeconds(60));
        when(trc20Provider.method()).thenReturn(PaymentMethod.USDT_TRC20);
        when(trc20Provider.createOrder(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(trc20Order);
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(0);
        PaymentOrderService trc20Service = service(properties, trc20Provider);

        PaymentOrderView created = trc20Service.createForUser(USER_SEVEN, new BigDecimal("1.00"), PaymentMethod.USDT_TRC20);

        assertThat(created.orderNo()).isEqualTo("PO_TRON");
        verify(trc20Provider).createOrder(anyLong(), anyLong(), anyLong(), any(), any());
        verify(orders, never()).save(trc20Order);
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

    @Test
    void rejectsOrderCreationWhenCooldownNotMet() {
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(3);
        Clock mutableClock = mock(Clock.class);
        Instant t0 = Instant.parse("2026-09-02T10:00:00Z");
        Instant t1 = Instant.parse("2026-09-02T10:00:02Z");
        when(mutableClock.instant()).thenReturn(t0, t1);
        PaymentOrderService cooldownService = new PaymentOrderService(
                orders, properties, new PaymentProviderRegistry(List.of(new PayPalPaymentProvider(orders))), mutableClock);
        returnSavedOrder();

        cooldownService.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cooldownService.createForUser(USER_SEVEN, new BigDecimal("1.00")))
                .withMessage("创建订单过于频繁，请稍后再试");
    }

    @Test
    void allowsOrderCreationAfterCooldownExpires() {
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(3);
        Clock mutableClock = mock(Clock.class);
        Instant t0 = Instant.parse("2026-09-02T10:00:00Z");
        Instant t1 = Instant.parse("2026-09-02T10:00:04Z");
        when(mutableClock.instant()).thenReturn(t0, t1);
        PaymentOrderService cooldownService = new PaymentOrderService(
                orders, properties, new PaymentProviderRegistry(List.of(new PayPalPaymentProvider(orders))), mutableClock);
        returnSavedOrder();

        cooldownService.createForUser(USER_SEVEN, new BigDecimal("1.00"));
        PaymentOrderView second = cooldownService.createForUser(USER_SEVEN, new BigDecimal("1.00"));

        assertThat(second).isNotNull();
    }

    @Test
    void rejectsOrderCreationWhenMaxWaitingOrdersExceeded() {
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(0);
        properties.setMaxWaitingOrdersPerUser(3);
        when(orders.countByNewApiUserIdAndStatus(USER_SEVEN.userId(), PaymentOrderStatus.WAITING_PAYMENT))
                .thenReturn(3L);
        PaymentOrderService limitedService = service(properties);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> limitedService.createForUser(USER_SEVEN, new BigDecimal("1.00")))
                .withMessage("您当前已有待支付订单，请先完成或取消未支付订单后再创建新订单");
    }

    @Test
    void allowsOrderCreationWhenUnderMaxWaitingOrders() {
        PaymentProperties properties = new PaymentProperties();
        properties.setOrderCreationCooldownSeconds(0);
        properties.setMaxWaitingOrdersPerUser(3);
        when(orders.countByNewApiUserIdAndStatus(USER_SEVEN.userId(), PaymentOrderStatus.WAITING_PAYMENT))
                .thenReturn(2L);
        PaymentOrderService limitedService = service(properties);
        returnSavedOrder();

        PaymentOrderView created = limitedService.createForUser(USER_SEVEN, new BigDecimal("1.00"));
        assertThat(created).isNotNull();
    }

    private void returnSavedOrder() {
        when(orders.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PaymentOrderService service(PaymentProperties properties, PaymentProvider... providers) {
        List<PaymentProvider> allProviders = new ArrayList<>();
        allProviders.add(new PayPalPaymentProvider(orders));
        allProviders.addAll(List.of(providers));
        return new PaymentOrderService(orders, properties, new PaymentProviderRegistry(allProviders));
    }
}
