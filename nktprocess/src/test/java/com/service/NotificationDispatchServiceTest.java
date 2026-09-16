package com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.models.fcm.FcmSendResult;
import com.repository.NktDynamicRepository;

/**
 * Unit tests for the MongoDB ⇄ Firebase orchestration layer. FirebaseNotificationService
 * is mocked here — its own send/batch/invalid-token logic is covered by
 * {@link FirebaseNotificationServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NktDynamicRepository repo;

    @Mock
    private FirebaseNotificationService firebase;

    private NotificationDispatchService dispatch;

    private NotificationDispatchService service() {
        return new NotificationDispatchService(repo, firebase);
    }

    /** Test 6 — a user with no active devices never reaches Firebase and returns an empty result. */
    @Test
    void notifyUser_withNoActiveDevices_returnsEmptyResult() {
        dispatch = service();
        when(repo.findAll(eq("user_devices"), anyMap())).thenReturn(List.of());

        FcmSendResult result = dispatch.notifyUser("USER_404", "customer", null, "t", "b", "ORDER", Map.of(), true);

        assertEquals(0, result.getTotalDevices());
        verify(firebase, never()).sendToMultipleDevices(any(), anyString(), anyString(), anyMap());
    }

    /** Test 8 — a single user with multiple registered devices gets one multicast covering all of them. */
    @Test
    void notifyUser_withMultipleDevices_sendsToAllTokens() {
        dispatch = service();
        Map<String, Object> deviceA = device("USER_1", "TOK_A");
        Map<String, Object> deviceB = device("USER_1", "TOK_B");
        when(repo.findAll(eq("user_devices"), anyMap())).thenReturn(List.of(deviceA, deviceB));
        when(firebase.sendToMultipleDevices(any(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.builder().totalDevices(2).success(2).build());

        FcmSendResult result = dispatch.notifyUser("USER_1", "customer", null, "t", "b", "ORDER", Map.of(), true);

        verify(firebase, times(1)).sendToMultipleDevices(
                argThatContainsBoth("TOK_A", "TOK_B"), eq("t"), eq("b"), anyMap());
        assertEquals(2, result.getSuccess());
    }

    /** Test 7 — an invalid token reported by Firebase is deactivated in user_devices, not deleted. */
    @Test
    void invalidToken_isDeactivatedNotDeleted() {
        dispatch = service();
        when(repo.findAll(eq("user_devices"), anyMap())).thenReturn(List.of(device("USER_1", "DEAD_TOKEN")));
        when(firebase.sendToMultipleDevices(any(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.builder().totalDevices(1).failed(1)
                        .invalidTokens(new java.util.ArrayList<>(List.of("DEAD_TOKEN"))).build());

        dispatch.notifyUser("USER_1", "customer", null, "t", "b", "ORDER", Map.of(), true);

        verify(repo, times(1)).updateFirst(
                eq("user_devices"),
                eq(Map.of("fcmToken", "DEAD_TOKEN", "isActive", true)),
                argThatMapContains("isActive", false));
        verify(repo, never()).deleteById(eq("user_devices"), anyString());
    }

    /** notifyUsers() resolves devices for several userIds in one call (a group). */
    @Test
    void notifyUsers_resolvesDevicesForWholeGroup() {
        dispatch = service();
        when(repo.findAll(eq("user_devices"), anyMap()))
                .thenReturn(List.of(device("EMP_1", "T1"), device("EMP_2", "T2")));
        when(firebase.sendToMultipleDevices(any(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.builder().totalDevices(2).success(2).build());

        List<NotificationDispatchService.Recipient> recipients = List.of(
                new NotificationDispatchService.Recipient("EMP_1", "employee", "ST011"),
                new NotificationDispatchService.Recipient("EMP_2", "employee", "ST011"));

        FcmSendResult result = dispatch.notifyUsers(recipients, "New Order", "New order ORD1 received",
                "ORDER", Map.of("orderId", "ORD1"), true);

        assertEquals(2, result.getSuccess());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> device(String userId, String token) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("userId", userId);
        d.put("fcmToken", token);
        d.put("isActive", true);
        return d;
    }

    @SuppressWarnings("unchecked")
    private List<String> argThatContainsBoth(String a, String b) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.contains(a) && list.contains(b));
    }

    private Map<String, Object> argThatMapContains(String key, Object value) {
        return org.mockito.ArgumentMatchers.argThat(map -> map != null && value.equals(map.get(key)));
    }
}
