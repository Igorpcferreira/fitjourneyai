package br.edu.puc.fitjourneyai.dto.telegram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelegramSendMessageRequest {

    @JsonProperty("chat_id")
    private Long chatId;

    @JsonProperty("text")
    private String text;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("disable_notification")
    private Boolean disableNotification;
}
