package com.seatsure.booking;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public String charge(Long userId, BigDecimal amount, String idempotencyKey) {
        if (amount.signum() <= 0) {
            throw new PaymentDeclinedException("Amount must be positive");
        }
        // A real provider gets the same idempotency key, so it won't double-charge either.
        return "pay_" + UUID.randomUUID();
    }
}
