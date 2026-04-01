package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "device_tokens")
@CompoundIndex(def = "{'userId': 1, 'deviceToken': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken {

    @Id
    private String id;

    private String userId;
    private String deviceToken;
    private String platform;            // "android" | "ios"
    private String userType;            // "customer" | "store"
    private String status;              // "ACTIVE" | "INACTIVE"

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
