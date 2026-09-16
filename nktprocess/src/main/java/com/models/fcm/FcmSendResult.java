package com.models.fcm;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate outcome of a multicast/batch/group send — the shape returned to
 * the mobile client in {@code data} for FCM_SEND_NOTIFICATION,
 * FCM_SEND_GROUP_NOTIFICATION and FCM_SEND_BATCH_NOTIFICATION:
 * <pre>
 * { "totalDevices": 1250, "success": 1220, "failed": 30, "invalidTokens": 15 }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmSendResult {

    @Builder.Default
    private int totalDevices = 0;

    @Builder.Default
    private int success = 0;

    @Builder.Default
    private int failed = 0;

    /** fcmTokens Firebase reported as invalid/unregistered — already marked isActive=false. */
    @Builder.Default
    private List<String> invalidTokens = new ArrayList<>();

    public static FcmSendResult empty() {
        return FcmSendResult.builder().build();
    }

    public void merge(FcmSendResult other) {
        if (other == null) return;
        this.totalDevices += other.totalDevices;
        this.success       += other.success;
        this.failed        += other.failed;
        this.invalidTokens.addAll(other.invalidTokens);
    }
}
