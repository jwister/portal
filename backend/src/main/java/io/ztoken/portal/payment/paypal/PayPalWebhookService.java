package io.ztoken.portal.payment.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.payment.credit.PaymentConfirmedEvent;
import io.ztoken.portal.payment.domain.PaymentMethod;
import io.ztoken.portal.payment.domain.PaymentOrder;
import io.ztoken.portal.payment.domain.PaymentOrderStatus;
import io.ztoken.portal.payment.domain.PaymentTransaction;
import io.ztoken.portal.payment.repository.PaymentOrderRepository;
import io.ztoken.portal.payment.repository.PaymentProviderEventRepository;
import io.ztoken.portal.payment.repository.PaymentTransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Verifies PayPal's official webhook signature before accepting any payload fields, then records
 * the provider event atomically before applying the completed-capture state transition.
 */
@Service
public class PayPalWebhookService {

    private static final String COMPLETED_CAPTURE_EVENT = "PAYMENT.CAPTURE.COMPLETED";
    private static final String PAYPAL_PROVIDER = PaymentMethod.PAYPAL.name();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayPalClient payPal;
    private final PaymentProviderEventRepository events;
    private final PaymentTransactionRepository transactions;
    private final PaymentOrderRepository orders;
    private final ApplicationEventPublisher eventPublisher;

    public PayPalWebhookService(PayPalClient payPal, PaymentProviderEventRepository events,
                                PaymentTransactionRepository transactions, PaymentOrderRepository orders,
                                ApplicationEventPublisher eventPublisher) {
        this.payPal = Objects.requireNonNull(payPal, "payPal");
        this.events = Objects.requireNonNull(events, "events");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    @Transactional
    public void handle(Map<String, String> headers, String rawBody) {
        if (!payPal.verifyWebhook(headers, rawBody)) {
            throw new IllegalArgumentException("PayPal webhook signature verification failed");
        }

        JsonNode event = parseEvent(rawBody);
        String eventId = requiredText(event.path("id").asText(), "PayPal webhook event ID");
        String eventType = requiredText(event.path("event_type").asText(), "PayPal webhook event type");
        JsonNode resource = event.path("resource");
        String providerOrderId = resource.path("supplementary_data").path("related_ids").path("order_id")
                .asText("").trim();

        PaymentTransaction transaction = providerOrderId.isEmpty() ? null
                : transactions.findByProviderAndProviderOrderId(PaymentMethod.PAYPAL, providerOrderId).orElse(null);
        PaymentOrder order = transaction == null ? null
                : orders.findByOrderNoForUpdate(transaction.getPaymentOrder().getOrderNo()).orElse(null);

        if (events.insertIfAbsent(PAYPAL_PROVIDER, eventId, eventType, order == null ? null : order.getId(),
                Instant.now(), "Verified PayPal webhook") == 0) {
            return;
        }
        if (!COMPLETED_CAPTURE_EVENT.equals(eventType) || transaction == null || order == null) {
            return;
        }

        validateCompletedCapture(order, transaction, resource);
        if (order.getStatus() != PaymentOrderStatus.WAITING_PAYMENT) {
            return;
        }

        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            order.expireIfPast(now);
            orders.save(order);
            return;
        }

        String captureId = resource.path("id").asText().trim();
        if (!transaction.recordCapture(captureId, "COMPLETED", now)) {
            throw new IllegalArgumentException("PayPal capture ID does not match the local transaction");
        }
        if (!order.confirm(now)) {
            return;
        }
        transactions.save(transaction);
        orders.save(order);
        eventPublisher.publishEvent(new PaymentConfirmedEvent(order.getOrderNo()));
    }

    private void validateCompletedCapture(PaymentOrder order, PaymentTransaction transaction, JsonNode resource) {
        String providerOrderId = resource.path("supplementary_data").path("related_ids").path("order_id")
                .asText("").trim();
        String captureId = resource.path("id").asText("").trim();
        String status = resource.path("status").asText("");
        String currency = resource.path("amount").path("currency_code").asText("");
        long amountMinor = parseUsd(resource.path("amount").path("value").asText(""));

        if (order.getPaymentMethod() != PaymentMethod.PAYPAL || !transaction.getProviderOrderId().equals(providerOrderId)
                || (transaction.getProviderCaptureId() != null && !transaction.getProviderCaptureId().equals(captureId))
                || captureId.isEmpty() || !"COMPLETED".equals(status) || !"USD".equals(currency)
                || amountMinor != order.getAmountUsdMinor()) {
            throw new IllegalArgumentException("PayPal webhook capture does not match the local order");
        }
    }

    private static JsonNode parseEvent(String rawBody) {
        try {
            JsonNode event = JSON.readTree(rawBody);
            if (event == null || !event.isObject()) {
                throw new IllegalArgumentException("PayPal webhook body is invalid");
            }
            return event;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("PayPal webhook body is invalid", exception);
        }
    }

    private static long parseUsd(String value) {
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("PayPal webhook USD amount is invalid", exception);
        }
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
