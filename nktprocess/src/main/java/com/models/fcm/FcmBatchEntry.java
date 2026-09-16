package com.models.fcm;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One fully-resolved message ready to hand to the Firebase batch API
 * ({@code sendEach}) — a single device token plus its own title/body/data,
 * used when different recipients must receive different content
 * (FCM_SEND_BATCH_NOTIFICATION).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmBatchEntry {
    private String token;
    private String title;
    private String body;
    private Map<String, String> data;
}
