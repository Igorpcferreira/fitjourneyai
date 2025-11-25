package br.edu.puc.fitjourneyai.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Payload para chamada ao endpoint /chat/completions da OpenAI.
 */
@Data
@Builder
public class OpenAiChatCompletionRequest {

    private String model;

    private List<OpenAiChatMessage> messages;

    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("top_p")
    private Double topP;

    private Boolean stream;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
}
