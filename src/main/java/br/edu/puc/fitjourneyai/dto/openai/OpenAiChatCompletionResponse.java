package br.edu.puc.fitjourneyai.dto.openai;

import lombok.Data;

import java.util.List;

/**
 * Resposta do endpoint /chat/completions.
 */
@Data
public class OpenAiChatCompletionResponse {

    private String id;
    private String object;
    private Long created;
    private String model;

    private List<OpenAiChatCompletionChoice> choices;

    private OpenAiUsage usage;
}
