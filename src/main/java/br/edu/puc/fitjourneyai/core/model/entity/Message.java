package br.edu.puc.fitjourneyai.core.model.entity;

import br.edu.puc.fitjourneyai.core.model.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de uma mensagem no histórico de conversa.
 * Armazena tanto mensagens do usuário quanto do bot para contexto.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "conteudo", nullable = false, columnDefinition = "text")
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private MessageType tipo;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;
}
