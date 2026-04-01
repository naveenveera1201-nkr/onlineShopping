package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "otp_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRecord {

    @Id
    private String id;

    @Indexed
    private String identifier;
    private String identifierType;
    private String otp;
    private String userType;
    private String purpose;             // "register" | "login"
    private boolean used;
    private int attempts;

    @Indexed(expireAfterSeconds = 300)  // TTL: 5 minutes
    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;
}
