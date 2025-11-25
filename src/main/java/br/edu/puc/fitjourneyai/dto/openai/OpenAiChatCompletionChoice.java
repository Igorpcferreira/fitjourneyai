package br.edu.puc.fitjourneyai.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Cada uma das escolhas retornadas pela OpenAI (normalmente choices[0]).
 */
@Data
public class OpenAiChatCompletionChoice {

    private Integer index;

    private OpenAiChatMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;
}
