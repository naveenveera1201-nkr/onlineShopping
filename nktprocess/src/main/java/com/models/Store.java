package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "stores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Store {

    @Id
    private String id;

    private String ownerId;             // references User.id
    private String name;
    private String categoryId;
    private String phone;
    private String status;              // "ACTIVE" | "INACTIVE"
    private boolean isOpen;

    private Address address;

    private double rating;
    private int totalRatings;

    /** e.g. { "monday": {"open":"09:00","close":"21:00"}, ... } */
    private Map<String, Map<String, String>> hours;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
