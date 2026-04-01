package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String identifier;          // phone number or email

    private String identifierType;      // "phone" | "email"
    private String name;
    private String email;
    private String userType;            // "customer" | "store"
    private String status;              // "ACTIVE" | "INACTIVE" | "SUSPENDED"

    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    @Builder.Default
    private List<String> favouriteStoreIds = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
