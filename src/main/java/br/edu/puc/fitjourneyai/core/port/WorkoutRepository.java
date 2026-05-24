package br.edu.puc.fitjourneyai.core.port;

import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.WorkoutSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída para persistência de treinos.
 */
public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    /**
     * Busca treinos realizados em um período (para resumos e progresso).
     */
    List<Workout> findByUserAndDataRealizacaoBetween(
            User user,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    /**
     * Conta treinos no período (para indicadores de resumo).
     */
    long countByUserAndDataRealizacaoBetween(
            User user,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    /**
     * Busca o treino sugerido pela IA mais recente ainda não confirmado como realizado.
     */
    Optional<Workout> findTopByUserAndFonteAndDataRealizacaoIsNullAndDataGeracaoAfterOrderByDataGeracaoDesc(
            User user,
            WorkoutSource fonte,
            LocalDateTime dataGeracaoAfter
    );
}
