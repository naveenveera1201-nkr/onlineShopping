package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    private String name;
    private String emoji;
    private String colour;
    private String badge;
    private String type;                // "service" | "product"
    private boolean active;
    private int sortOrder;
}
