package br.edu.puc.fitjourneyai.dto.openai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa uma mensagem no formato de chat da OpenAI (role + content).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiChatMessage {

    private String role;    // "system", "user", "assistant"
    private String content;
}
