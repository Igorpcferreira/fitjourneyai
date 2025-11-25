package br.edu.puc.fitjourneyai.domain.entity;

import br.edu.puc.fitjourneyai.domain.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.domain.enums.GoalType;
import br.edu.puc.fitjourneyai.domain.enums.LevelType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private Long telegramChatId;

    @Column(name = "nome", length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "objetivo", length = 30)
    private GoalType objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", length = 30)
    private LevelType nivel;

    @Column(name = "frequencia_treino_estimada")
    private Integer frequenciaTreinoEstimada;

    @Column(name = "peso_atual")
    private Double pesoAtual;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "onboarding_concluido", nullable = false)
    private boolean onboardingConcluido = false;

    @Column(name = "nudges_enabled", nullable = false)
    private boolean nudgesEnabled = true;

    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;

    /**
     * Data/hora do ultimo "nudge" (mensagem de reengajamento) enviado pelo bot.
     *
     * Ideia:
     *  - Um job agendado procura usuarios inativos usando lastInteractionAt.
     *  - Se nudgesEnabled = true E lastNudgeAt for nulo ou muito antigo,
     *    o bot envia uma mensagem motivacional lembrando o usuario de voltar a treinar
     *    e atualiza este campo.
     *
     * Assim evita-se de mandar lembretes com muita frequencia para a mesma pessoa.
     */
    @Column(name = "last_nudge_at")
    private LocalDateTime lastNudgeAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Fluxo de conversa atual do usuario (ex.: ONBOARDING, REGISTRO_PESO, etc.).
     * Usado para saber em que "modo" o bot deve interpretar a proxima mensagem.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_flow", length = 40)
    private ConversationFlowType currentFlow;

    /**
     * Passo atual dentro do fluxo em andamento.
     * Exemplo: no onboarding, passo 1 = perguntar nome, 2 = objetivo, etc.
     */
    @Column(name = "current_step")
    private Integer currentStep;


    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
