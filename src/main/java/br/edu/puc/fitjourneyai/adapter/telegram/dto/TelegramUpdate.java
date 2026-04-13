package br.edu.puc.fitjourneyai.adapter.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO raiz do webhook update do Telegram.
 * Suporta mensagens de texto e audio (voice).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {

    @JsonProperty("update_id")
    private Long updateId;

    private TelegramMessage message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {

        @JsonProperty("message_id")
        private Long messageId;

        private TelegramChat chat;

        private TelegramFrom from;

        private String text;

        /** Audio de voz do Telegram (quando usuario envia audio). */
        private TelegramVoice voice;

        private Long date;

        /** Verifica se a mensagem contem audio de voz. */
        public boolean hasVoice() {
            return voice != null && voice.getFileId() != null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {
        private Long id;
        private String type;
        @JsonProperty("first_name")
        private String firstName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramFrom {
        private Long id;
        @JsonProperty("is_bot")
        private Boolean isBot;
        @JsonProperty("first_name")
        private String firstName;
        @JsonProperty("language_code")
        private String languageCode;
    }

    /**
     * Representa um audio de voz enviado pelo usuario no Telegram.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramVoice {
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("file_unique_id")
        private String fileUniqueId;
        private Integer duration;
        @JsonProperty("mime_type")
        private String mimeType;
        @JsonProperty("file_size")
        private Long fileSize;
    }
}
