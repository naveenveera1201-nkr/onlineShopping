package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    private String id;

    private String label;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private String status;              // "ACTIVE" | "DELETED"
}
