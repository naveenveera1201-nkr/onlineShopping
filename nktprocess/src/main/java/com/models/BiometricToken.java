package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "biometric_tokens")
@CompoundIndex(def = "{'userId': 1, 'deviceId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricToken {

    @Id
    private String id;

    private String userId;
    private String deviceId;
    private String tokenHash;           // stored hashed
    private String platform;            // "android" | "ios"
    private String status;              // "ACTIVE" | "REVOKED"

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
