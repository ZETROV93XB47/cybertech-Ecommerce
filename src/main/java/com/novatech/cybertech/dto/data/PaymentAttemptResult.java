package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;

public record PaymentAttemptResult(PaymentAttemptStatus status, String providerRef) {}