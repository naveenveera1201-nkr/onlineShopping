package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "stock_items")
@CompoundIndex(def = "{'storeId': 1, 'category': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {

    @Id
    private String id;

    private String storeId;
    private String name;
    private String sub;                 // sub-category
    private double price;
    private String emoji;
    private String category;
    private int qty;
    private boolean available;
    private boolean custom;             // true = store-created, deletable

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String status;              // "ACTIVE" | "DELETED"
}
