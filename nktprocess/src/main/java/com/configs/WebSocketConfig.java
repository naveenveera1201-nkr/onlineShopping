package com.configs;

import com.websocket.OrderTrackingHandler;
import com.websocket.StoreOrderAlertHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderTrackingHandler    orderTrackingHandler;
    private final StoreOrderAlertHandler  storeOrderAlertHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // API 52 – Customer order tracking
        registry.addHandler(orderTrackingHandler, "/ws/orders/{orderId}/track")
                .setAllowedOrigins("*");

        // API 53 – Store new-order alerts
        registry.addHandler(storeOrderAlertHandler, "/ws/store/orders")
                .setAllowedOrigins("*");
    }
}
