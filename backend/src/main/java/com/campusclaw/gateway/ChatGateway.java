package com.campusclaw.gateway;

import java.util.List;
import java.util.function.Consumer;

public interface ChatGateway {
    String complete(List<ChatMessage> messages);

    default Completion completeWithUsage(List<ChatMessage> messages) {
        return new Completion(complete(messages), null);
    }

    default String completeBounded(List<ChatMessage> messages, int maxTokens) {
        return complete(messages);
    }

    ChatStream openStream(List<ChatMessage> messages);

    interface ChatStream extends AutoCloseable {
        void consume(Consumer<String> deltaConsumer);

        /** 固定文本等本地流不产生模型用量和模型耗时。 */
        default boolean modelBacked() {
            return false;
        }

        /** 供应商没有返回 usage 时保持 null；调用方不得把未知值写成 0。 */
        default Usage usage() {
            return null;
        }

        @Override
        void close();
    }

    record ChatMessage(String role, String content) {
    }

    record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }

    record Completion(String text, Usage usage) {
    }
}
