package com.service.handlers;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.fcm.FcmSendResult;
import com.models.nkt.NktProcessDefinition;
import com.repository.NktDynamicRepository;
import com.service.NotificationDispatchService;

@ExtendWith(MockitoExtension.class)
class NktNotificationHandlerTest {

    @Mock
    private NktDynamicRepository repo;

    @Mock
    private NotificationDispatchService dispatch;

    private final ObjectMapper mapper = new ObjectMapper();
    private final NktProcessDefinition def = new NktProcessDefinition();

    private NktNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NktNotificationHandler(dispatch);
    }

    /** Test 1 — register a brand-new device inserts a fresh user_devices row. */
    @Test
    void registerDevice_new_insertsDocument() {
        when(repo.findOneByCriteria(eq("user_devices"), anyMap())).thenReturn(Optional.empty());

        Map<String, Object> data = requestData("DEVICE_001", "tok_abc", "ANDROID", "1.0.0", "customer");
        String response = handler.registerDevice().handle(data, "USER_001", repo, mapper, def);

        verify(repo, times(1)).insert(eq("user_devices"), anyMap());
        assertTrue(response.contains("\"N200\""));
        assertTrue(response.contains("registered"));
    }

    /** Test 2 — a device that registers again with a new token updates the existing row instead of duplicating it. */
    @Test
    void registerDevice_existing_updatesToken() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("userId", "USER_001");
        existing.put("deviceId", "DEVICE_001");
        when(repo.findOneByCriteria(eq("user_devices"), anyMap())).thenReturn(Optional.of(existing));

        Map<String, Object> data = requestData("DEVICE_001", "tok_new", "ANDROID", "1.1.0", "customer");
        String response = handler.registerDevice().handle(data, "USER_001", repo, mapper, def);

        verify(repo, never()).insert(eq("user_devices"), anyMap());
        verify(repo, times(1)).updateFirst(eq("user_devices"), anyMap(), anyMap());
        assertTrue(response.contains("\"N200\""));
    }

    /** Missing fcmToken is rejected with the documented error code before any DB call. */
    @Test
    void registerDevice_missingToken_returnsFcmTokenRequired() {
        Map<String, Object> data = requestData("DEVICE_001", null, "ANDROID", "1.0.0", "customer");
        String response = handler.registerDevice().handle(data, "USER_001", repo, mapper, def);

        assertTrue(response.contains("FCM_TOKEN_REQUIRED"));
        verify(repo, never()).insert(eq("user_devices"), anyMap());
    }

    /** Test 14 — a business user cannot target a store they do not own. */
    @Test
    void sendGroupNotification_storeStaff_rejectsWrongStoreOwner() {
        when(repo.findOne(eq("stores"), eq("userId"), eq("OWNER_A"))).thenReturn(
                Optional.of(Map.of("storeId", "ST_OWNED_BY_A")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupType", "STORE_STAFF");
        data.put("title", "New Order");
        data.put("body", "New order received");
        data.put("storeId", "ST_OWNED_BY_B"); // not A's store
        data.put("userType", "business");

        String response = handler.sendGroupNotification().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("\"N403\""));
        verify(dispatch, never()).notifyUsers(any(), anyString(), anyString(), anyString(), anyMap(), eq(true));
    }

    /** Test 14 — CUSTOMERS broadcast is refused without the isPlatformAdmin flag. */
    @Test
    void sendGroupNotification_customers_blockedWithoutPlatformAdmin() {
        when(repo.findOne(eq("stores"), eq("userId"), eq("OWNER_A")))
                .thenReturn(Optional.of(Map.of("storeId", "ST011", "isPlatformAdmin", false)));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupType", "CUSTOMERS");
        data.put("title", "Special Offer");
        data.put("body", "20% discount available today");
        data.put("userType", "business");

        String response = handler.sendGroupNotification().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("\"N403\""));
        verify(dispatch, never()).notifyUserType(anyString(), anyString(), anyString(), anyString(), anyMap(), eq(false));
    }

    /** Test 11/12/13 — only currently-Active employees are targeted; revoked/released ones are excluded. */
    @SuppressWarnings("unchecked")
    @Test
    void sendGroupNotification_storeStaff_excludesInactiveEmployees() {
        when(repo.findOne(eq("stores"), eq("userId"), eq("OWNER_A")))
                .thenReturn(Optional.of(Map.of("storeId", "ST011")));
        when(repo.findAll(eq("store_staff_employees"), anyMap())).thenReturn(List.of(
                Map.of("employeeId", "EMP_ACTIVE", "status", "Active"),
                Map.of("employeeId", "EMP_REVOKED", "status", "Inactive"),
                Map.of("employeeId", "EMP_RELEASED", "status", "Released")
        ));
        when(dispatch.notifyUsers(any(), anyString(), anyString(), anyString(), anyMap(), eq(true)))
                .thenReturn(FcmSendResult.empty());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupType", "STORE_STAFF");
        data.put("title", "New Order");
        data.put("body", "New order ORD10001 received");
        data.put("storeId", "ST011");
        data.put("userType", "business");

        handler.sendGroupNotification().handle(data, "OWNER_A", repo, mapper, def);

        ArgumentCaptor<List<NotificationDispatchService.Recipient>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatch).notifyUsers(captor.capture(), anyString(), anyString(), anyString(), anyMap(), eq(true));

        List<NotificationDispatchService.Recipient> recipients = captor.getValue();
        assertTrue(recipients.stream().anyMatch(r -> r.userId().equals("EMP_ACTIVE")));
        assertTrue(recipients.stream().noneMatch(r -> r.userId().equals("EMP_REVOKED")));
        assertTrue(recipients.stream().noneMatch(r -> r.userId().equals("EMP_RELEASED")));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> requestData(String deviceId, String fcmToken, String platform,
                                             String appVersion, String userType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", deviceId);
        data.put("fcmToken", fcmToken);
        data.put("platform", platform);
        data.put("appVersion", appVersion);
        data.put("userType", userType);
        return data;
    }
}
