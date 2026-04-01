package com.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.Order;
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
 * API 53 – Store New-Order Alert Socket
 * wss://host/ws/store/orders
 * Pushes full order objects to the store dashboard on new order arrival.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreOrderAlertHandler extends TextWebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper     objectMapper = new ObjectMapper();

    /** storeOwnerId → WebSocketSession */
    private static final ConcurrentHashMap<String, WebSocketSession> STORE_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getQueryParam(session, "token");
        if (token == null || !jwtTokenProvider.isTokenValid(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid store token"));
            return;
        }
        String ownerId = jwtTokenProvider.extractUserId(token);
        STORE_SESSIONS.put(ownerId, session);
        log.info("Store connected for order alerts: ownerId={}", ownerId);

        Map<String, Object> welcome = Map.of(
            "event",     "connected",
            "message",   "Listening for new orders",
            "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        STORE_SESSIONS.values().remove(session);
        log.info("Store disconnected: sessionId={}", session.getId());
    }

    /** Called by StoreOrderService when a new order arrives */
    public void pushNewOrder(String storeOwnerId, Order order) {
        WebSocketSession session = STORE_SESSIONS.get(storeOwnerId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> payload = Map.of(
                    "event",     "new_order",
                    "order",      order,
                    "timestamp",  LocalDateTime.now().toString()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                log.warn("Failed to push order alert to store {}", storeOwnerId);
            }
        }
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
}
