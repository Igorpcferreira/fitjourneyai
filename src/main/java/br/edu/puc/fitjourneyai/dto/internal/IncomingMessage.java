package br.edu.puc.fitjourneyai.dto.internal;

import br.edu.puc.fitjourneyai.domain.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class IncomingMessage {

    private User user;
    private Long chatId;
    private String texto;
    private LocalDateTime dataHora;
}
