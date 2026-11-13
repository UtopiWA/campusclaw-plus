package com.campusclaw.gateway;

import java.util.List;
import java.util.function.Consumer;

public interface ChatGateway {
    String complete(List<ChatMessage> messages);

    default String completeBounded(List<ChatMessage> messages, int maxTokens) {
        return complete(messages);
    }

    ChatStream openStream(List<ChatMessage> messages);

    interface ChatStream extends AutoCloseable {
        void consume(Consumer<String> deltaConsumer);

        @Override
        void close();
    }

    record ChatMessage(String role, String content) {
    }
}
