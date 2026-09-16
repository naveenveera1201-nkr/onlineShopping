package com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.models.fcm.FcmSendResult;

/**
 * Unit tests for the pure Firebase-facing send layer. No MongoDB involved —
 * see {@link NotificationDispatchServiceTest} for the Mongo/business layer.
 */
@ExtendWith(MockitoExtension.class)
class FirebaseNotificationServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private FirebaseNotificationService service;

    @BeforeEach
    void setUp() {
        service = new FirebaseNotificationService(firebaseMessaging);
    }

    /** Test 15/16 — Firebase configuration missing → service is a safe no-op, never throws. */
    @Test
    void whenFirebaseDisabled_sendIsSafeNoOp() {
        FirebaseNotificationService disabled = new FirebaseNotificationService(null);

        FcmSendResult result = disabled.sendToMultipleDevices(List.of("tokenA", "tokenB"),
                "title", "body", Map.of("type", "ORDER"));

        assertEquals(0, result.getTotalDevices());
        assertEquals(0, result.getSuccess());
        assertEquals(0, result.getFailed());
    }

    /** Test 6 — empty token list never calls Firebase and returns an empty result. */
    @Test
    void emptyTokenList_returnsEmptyResultWithoutCallingFirebase() throws Exception {
        FcmSendResult result = service.sendToMultipleDevices(List.of(), "title", "body", Map.of());

        assertEquals(0, result.getTotalDevices());
        verify(firebaseMessaging, times(0)).sendEachForMulticast(any());
    }

    /** Test 9 — exactly 500 devices is sent as a single batch. */
    @Test
    void exactly500Devices_sentAsSingleBatch() throws Exception {
        List<String> tokens = tokens(500);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse(allSuccessful(500)));

        FcmSendResult result = service.sendToMultipleDevices(tokens, "t", "b", Map.of());

        verify(firebaseMessaging, times(1)).sendEachForMulticast(any());
        assertEquals(500, result.getTotalDevices());
        assertEquals(500, result.getSuccess());
    }

    /** Test 10 — 1200 devices are split into batches of the Firebase-supported maximum (500). */
    @Test
    void moreThan500Devices_splitIntoMultipleBatches() throws Exception {
        List<String> tokens = tokens(1200);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse(allSuccessful(500)))
                .thenReturn(batchResponse(allSuccessful(500)))
                .thenReturn(batchResponse(allSuccessful(200)));

        FcmSendResult result = service.sendToMultipleDevices(tokens, "t", "b", Map.of());

        verify(firebaseMessaging, times(3)).sendEachForMulticast(any());
        assertEquals(1200, result.getTotalDevices());
        assertEquals(1200, result.getSuccess());
    }

    /** Test 7 — UNREGISTERED is classified as an invalid token and surfaced for deactivation. */
    @Test
    void unregisteredToken_isClassifiedInvalid() throws Exception {
        SendResponse ok = mockResponse(true, null);
        SendResponse bad = mockResponse(false, MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse(List.of(ok, bad)));

        FcmSendResult result = service.sendToMultipleDevices(List.of("goodToken", "deadToken"), "t", "b", Map.of());

        assertEquals(1, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertTrue(result.getInvalidTokens().contains("deadToken"));
    }

    /** A transient error (e.g. UNAVAILABLE) must NOT be treated as an invalid token. */
    @Test
    void transientError_isNotClassifiedInvalid() throws Exception {
        SendResponse transientFailure = mockResponse(false, MessagingErrorCode.UNAVAILABLE);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse(List.of(transientFailure)));

        FcmSendResult result = service.sendToMultipleDevices(List.of("flakyToken"), "t", "b", Map.of());

        assertEquals(1, result.getFailed());
        assertFalse(result.getInvalidTokens().contains("flakyToken"));
    }

    @Test
    void maskNeverExposesFullToken() {
        String masked = FirebaseNotificationService.mask("f9Kx82jsldkalskdjalskdjLKJH1234ABCD");
        assertTrue(masked.startsWith("f9Kx82"));
        assertTrue(masked.endsWith("ABCD"));
        assertFalse(masked.contains("lskdjalskdj"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<String> tokens(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add("token-" + i);
        return list;
    }

    private List<SendResponse> allSuccessful(int n) {
        List<SendResponse> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(mockResponse(true, null));
        return list;
    }

    private SendResponse mockResponse(boolean success, MessagingErrorCode errorCode) {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(success);
        if (!success) {
            FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
            when(ex.getMessagingErrorCode()).thenReturn(errorCode);
            when(ex.getMessage()).thenReturn("mock failure: " + errorCode);
            when(r.getException()).thenReturn(ex);
        }
        return r;
    }

    private BatchResponse batchResponse(List<SendResponse> responses) {
        BatchResponse br = mock(BatchResponse.class);
        when(br.getResponses()).thenReturn(responses);
        return br;
    }
}
