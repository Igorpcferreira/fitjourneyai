package br.edu.puc.fitjourneyai.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramMessage {

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("from")
    private TelegramUser from;

    @JsonProperty("chat")
    private TelegramChat chat;

    /**
     * Telegram envia a data como UNIX time (segundos desde 1970).
     * Irei manter como Integer por enquanto; se precisar,
     * lembrar de converter depois para LocalDateTime.
     */
    @JsonProperty("date")
    private Integer date;

    @JsonProperty("text")
    private String text;
}
