package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private String id;

    private String orderId;
    private String customerId;
    private long amount;                // in paise
    private String method;             // "upi" | "netbanking" | "cod" | "wallet"
    private String upiId;
    private String bankCode;

    /** status: pending | success | failed | refunded */
    private String status;
    private String currentStatus;

    private String gatewayPaymentId;
    private String redirectUrl;        // UPI deep-link or net-banking URL

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
