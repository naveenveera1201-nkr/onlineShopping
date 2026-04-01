package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "wishlist")
@CompoundIndex(def = "{'customerId': 1, 'itemId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItem {

    @Id
    private String id;

    private String customerId;
    private String itemId;
    private String itemName;
    private double price;
    private boolean available;
    private String status;              // "ACTIVE" | "DELETED"

    private LocalDateTime createdAt;
}
