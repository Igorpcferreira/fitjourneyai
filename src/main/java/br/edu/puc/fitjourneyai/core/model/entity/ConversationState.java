package br.edu.puc.fitjourneyai.core.model.entity;

import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Estado conversacional persistível e retomável.
 * <p>
 * Decisão arquitetural do TCC2: o estado da conversa é uma entidade separada
 * do User, com coluna JSONB para dados parciais do fluxo em andamento.
 * Isso permite retomar qualquer fluxo após restart da aplicação.
 * <p>
 * Cada FlowHandler define a estrutura do seu partialData (serializado como JSON).
 * Exemplos:
 * <ul>
 *   <li>Onboarding: {"nome":"Igor","objetivo":"EMAGRECER",...}</li>
 *   <li>Medidas: {"peso":72.5,"cintura":82.0,...}</li>
 *   <li>Treino feito: {"grupoMuscular":"pernas","duracaoMinutos":45,...}</li>
 * </ul>
 */
@Entity
@Table(name = "conversation_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_flow", length = 50)
    private ConversationFlowType currentFlow;

    @Column(name = "current_step")
    private Integer currentStep;

    /**
     * Dados parciais do fluxo em andamento, serializado como JSON.
     * Armazenado como JSONB no PostgreSQL para queries eficientes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "partial_data", columnDefinition = "jsonb")
    private String partialData;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.updatedAt = LocalDateTime.now();
        if (this.partialData == null) {
            this.partialData = "{}";
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Verifica se há um fluxo ativo (diferente de NONE ou null).
     */
    public boolean hasActiveFlow() {
        return currentFlow != null && currentFlow != ConversationFlowType.NONE;
    }

    /**
     * Reseta o estado para ocioso (sem fluxo ativo).
     */
    public void reset() {
        this.currentFlow = ConversationFlowType.NONE;
        this.currentStep = null;
        this.partialData = "{}";
    }
}
