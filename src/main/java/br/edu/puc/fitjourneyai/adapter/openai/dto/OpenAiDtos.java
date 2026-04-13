package br.edu.puc.fitjourneyai.adapter.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTOs para comunicação com a API da OpenAI (/v1/chat/completions).
 */
public class OpenAiDtos {

    @Data
    @Builder
    public static class ChatCompletionRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;

        @JsonProperty("max_completion_tokens")
        private Integer maxTokens;
    }

    @Data
    @Builder
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletionResponse {
        private List<Choice> choices;
        private Usage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Integer index;
        private ChatMessage message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
