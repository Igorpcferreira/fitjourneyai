package br.edu.puc.fitjourneyai.core.model.entity;

import br.edu.puc.fitjourneyai.core.model.enums.WorkoutGroup;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de um treino — seja gerado pela IA, registrado manualmente
 * ou importado de uma fonte externa (Strava).
 */
@Entity
@Table(name = "workouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "grupo_muscular", length = 30)
    private WorkoutGroup grupoMuscular;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte", nullable = false, length = 20)
    private WorkoutSource fonte;

    @Column(name = "descricao_treino", columnDefinition = "text")
    private String descricaoTreino;

    @Column(name = "data_geracao")
    private LocalDateTime dataGeracao;

    @Column(name = "data_realizacao")
    private LocalDateTime dataRealizacao;

    @Column(name = "duracao_minutos")
    private Integer duracaoMinutos;

    @Column(name = "intensidade_percebida")
    private Integer intensidadePercebida;

    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;
}
