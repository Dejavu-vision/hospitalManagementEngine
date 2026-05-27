package com.curamatrix.hsm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TranscriptionWebSocketHandler extends BinaryWebSocketHandler {

    @Value("${deepgram.api.key:}")
    private String deepgramApiKey;

    // Active client session -> Deepgram websocket connection
    private final ConcurrentHashMap<String, WebSocket> deepgramSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = (String) session.getAttributes().get("username");
        log.info("WebSocket connection established with client: {} (session ID: {})", username, session.getId());

        if (deepgramApiKey == null || deepgramApiKey.trim().isEmpty()) {
            log.error("DEEPGRAM_API_KEY is not configured! Closing connection.");
            session.close(new CloseStatus(1008, "Deepgram API key not configured"));
            return;
        }

        String keyToUse = deepgramApiKey.trim();
        String keyPreview = keyToUse.length() > 6 
            ? keyToUse.substring(0, 4) + "..." + keyToUse.substring(keyToUse.length() - 2)
            : "TOO_SHORT";
        log.info("Connecting to Deepgram with Key: {} (length: {})", keyPreview, keyToUse.length());

        // Connect to Deepgram's streaming WebSocket
        // Let Deepgram auto-detect WebM/Opus container headers natively
        String deepgramUrl = "wss://api.deepgram.com/v1/listen" +
                "?model=nova-2-medical" +
                "&interim_results=true" +
                "&punctuate=true" +
                "&endpointing=300";

        HttpClient httpClient = HttpClient.newHttpClient();
        
        try {
            WebSocket deepgramWs = httpClient.newWebSocketBuilder()
                    .header("Authorization", "Token " + keyToUse)
                    .buildAsync(URI.create(deepgramUrl), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            try {
                                if (session.isOpen()) {
                                    session.sendMessage(new TextMessage(data));
                                }
                            } catch (Exception e) {
                                log.error("Error forwarding Deepgram response to client: {}", e.getMessage());
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            log.info("Deepgram closed connection for session {}: status={}, reason={}", session.getId(), statusCode, reason);
                            try {
                                if (session.isOpen()) {
                                    session.close(new CloseStatus(statusCode, reason));
                                }
                            } catch (Exception e) {
                                log.error("Error closing client session: {}", e.getMessage());
                            }
                            deepgramSessions.remove(session.getId());
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.error("Deepgram WebSocket error for session {}: {}", session.getId(), error.getMessage());
                            try {
                                if (session.isOpen()) {
                                    session.sendMessage(new TextMessage("{\"error\": \"Deepgram connection error\", \"message\": \"" + error.getMessage() + "\"}"));
                                    session.close(CloseStatus.SERVER_ERROR);
                                }
                            } catch (Exception e) {
                                log.error("Error sending error message or closing client session: {}", e.getMessage());
                            }
                            deepgramSessions.remove(session.getId());
                        }
                    }).join();

            deepgramSessions.put(session.getId(), deepgramWs);
            log.info("Successfully tunneled session {} to Deepgram streaming API.", session.getId());

        } catch (Exception e) {
            log.error("Failed to establish connection to Deepgram: {}", e.getMessage(), e);
            session.close(new CloseStatus(1011, "Failed to connect to speech engine: " + e.getMessage()));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        WebSocket deepgramWs = deepgramSessions.get(session.getId());
        if (deepgramWs != null) {
            ByteBuffer payload = message.getPayload();
            deepgramWs.sendBinary(payload, true);
        } else {
            log.warn("Received binary audio message for session {}, but no Deepgram session is active.", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed for session {} with status: {}", session.getId(), status);
        WebSocket deepgramWs = deepgramSessions.remove(session.getId());
        if (deepgramWs != null) {
            try {
                // Send an empty JSON message to close the stream gracefully in Deepgram
                deepgramWs.sendText("{\"type\": \"CloseStream\"}", true);
                deepgramWs.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnected");
            } catch (Exception e) {
                log.error("Error closing Deepgram WebSocket: {}", e.getMessage());
            }
        }
    }
}
