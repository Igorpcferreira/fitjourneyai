package br.edu.puc.fitjourneyai.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InternalMessage {

    private Long chatId;
    private String texto;
    private LocalDateTime dataHora;
}
