package com.seatsure.booking;

import java.math.BigDecimal;

/**
 * Boundary to a payment provider (Razorpay, Stripe...). The demo implementation
 * always approves; swapping in a real one doesn't touch booking logic.
 */
public interface PaymentGateway {

    /** @return provider transaction id; throws {@link PaymentDeclinedException} if declined. */
    String charge(Long userId, BigDecimal amount, String idempotencyKey);

    class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String message) {
            super(message);
        }
    }
}
