package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "banners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Banner {

    @Id
    private String id;

    private String title;
    private String subtitle;
    private String imageUrl;
    private String deepLink;
    private boolean active;
    private int sortOrder;
    private LocalDateTime validUntil;
}
