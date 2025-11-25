package br.edu.puc.fitjourneyai.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramUpdate {

    @JsonProperty("update_id")
    private Long updateId;

    @JsonProperty("message")
    private TelegramMessage message;

    // Se futuramente usar callback_query, inline_query etc., adiciono aqui.
}
