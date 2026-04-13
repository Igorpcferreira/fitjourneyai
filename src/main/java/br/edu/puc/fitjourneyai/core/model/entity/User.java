package br.edu.puc.fitjourneyai.core.model.entity;

import br.edu.puc.fitjourneyai.core.model.enums.GoalType;
import br.edu.puc.fitjourneyai.core.model.enums.IntensityLevel;
import br.edu.puc.fitjourneyai.core.model.enums.LevelType;
import br.edu.puc.fitjourneyai.core.model.enums.PersonaType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidade de domínio que representa um usuário do FitJourneyAI.
 * <p>
 * No TCC2, o estado conversacional (fluxo atual e step) foi migrado
 * para a entidade {@link ConversationState}, separando a identidade
 * do usuário do estado transiente da conversa.
 */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "persona", length = 30)
    private PersonaType persona;

    @Enumerated(EnumType.STRING)
    @Column(name = "intensity_level", length = 20)
    private IntensityLevel intensityLevel;

    @Column(name = "onboarding_concluido", nullable = false)
    private boolean onboardingConcluido;

    @Column(name = "nudges_enabled", nullable = false)
    private boolean nudgesEnabled;

    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;

    @Column(name = "last_nudge_at")
    private LocalDateTime lastNudgeAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
