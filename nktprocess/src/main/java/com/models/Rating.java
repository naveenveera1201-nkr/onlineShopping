package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "ratings")
@CompoundIndex(def = "{'orderId': 1, 'customerId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rating {

    @Id
    private String id;

    private String orderId;
    private String storeId;
    private String customerId;
    private int rating;                 // 1-5
    private String review;
    private String status;              // "ACTIVE"

    private LocalDateTime createdAt;
}
