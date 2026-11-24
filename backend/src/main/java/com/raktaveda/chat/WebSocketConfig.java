package com.raktaveda.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raktaveda.config.RaktavedaProperties;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Real-time chat over WebSocket with STOMP (a simple messaging format on top of WebSocket).
 *
 * How a client uses it:
 *   1. Open a WebSocket to /ws.
 *   2. Send a CONNECT frame with the header  Authorization: Bearer <token>.
 *   3. SUBSCRIBE to /user/queue/messages (new messages) and /user/queue/errors (error answers).
 *   4. SEND to /app/chat.send with JSON {"conversationId": "...", "body": "..."}.
 *
 * Messages are pushed only to the two accounts in the conversation, never to a shared topic.
 * The built-in memory broker is enough for one server. For several servers, swap in an external broker.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor authInterceptor;
    private final RaktavedaProperties props;
    private final ObjectMapper objectMapper;

    public WebSocketConfig(StompAuthInterceptor authInterceptor, RaktavedaProperties props, ObjectMapper objectMapper) {
        this.authInterceptor = authInterceptor;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** Use the same JSON settings as the REST API, so dates look the same (for example 2026-11-23T10:15:00Z). */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);
        converters.add(converter);
        return false; // false = do not add Spring's default converters as well
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Only the configured origins may open a socket from a browser page. This blocks other
        // websites from using a visitor's browser to talk to the chat.
        registry.addEndpoint("/ws").setAllowedOrigins(props.security().allowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8 * 1024);          // one frame can be at most 8 KB
        registration.setSendBufferSizeLimit(64 * 1024);
        registration.setTimeToFirstMessage(10_000);          // a connection that never says CONNECT is dropped after 10 s
    }
}
