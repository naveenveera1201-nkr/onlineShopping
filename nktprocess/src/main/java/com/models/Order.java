package com.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "orders")
@CompoundIndex(def = "{'customerId': 1, 'createdAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private String id;

    private String customerId;
    private String storeId;
    private String categoryId;
    private String addressId;
    private String paymentMethod;       // "upi" | "netbanking" | "cod" | "wallet"
    private String appointmentSlot;
    private String notes;
    private String urgency;             // "normal" | "urgent" | "express"
    private String orderType;           // "delivery" | "pickup" | "appointment"

    private List<OrderItem> items;

    /** status: placed | accepted | dispatched | delivered | cancelled */
    private String status;
    private String currentStatus;

    private List<Map<String, Object>> statusTimeline;

    private String paymentId;
    private String storeNote;

    private DeliveryAgent deliveryAgent;
    private String deliveryProof;

    private double totalAmount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String itemId;
        private String itemName;
        private int qty;
        private double price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryAgent {
        private String name;
        private String phone;
        private String vehiclePlate;
        private Double latitude;
        private Double longitude;
    }
}
