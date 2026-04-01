package com.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 52 – Customer Order Tracking Socket
 * wss://host/ws/orders/{orderId}/track
 * Pushes GPS coordinates, ETA and status changes to the connected customer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTrackingHandler extends TextWebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper     objectMapper = new ObjectMapper();

    /** sessionId → WebSocketSession */
    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getQueryParam(session, "token");
        if (token == null || !jwtTokenProvider.isTokenValid(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
            return;
        }
        SESSIONS.put(session.getId(), session);
        String orderId = extractOrderId(session);
        log.info("Customer connected for order tracking: orderId={}", orderId);

        Map<String, Object> welcome = Map.of(
            "event",     "connected",
            "orderId",   orderId != null ? orderId : "unknown",
            "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session.getId());
        log.info("Customer disconnected: sessionId={}", session.getId());
    }

    /** Push tracking update to all sessions watching a specific order */
    public void pushTrackingUpdate(String orderId, double lat, double lon, String status, int etaMinutes) {
        Map<String, Object> payload = Map.of(
            "event",      "location_update",
            "orderId",     orderId,
            "agentLat",    lat,
            "agentLon",    lon,
            "status",      status,
            "etaMinutes",  etaMinutes,
            "timestamp",   LocalDateTime.now().toString()
        );
        SESSIONS.values().forEach(s -> {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                log.warn("Failed to push to session {}", s.getId());
            }
        });
    }

    private String getQueryParam(WebSocketSession session, String name) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) return kv[1];
        }
        return null;
    }

    private String extractOrderId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++)
            if ("orders".equals(parts[i])) return parts[i + 1];
        return null;
    }
}
