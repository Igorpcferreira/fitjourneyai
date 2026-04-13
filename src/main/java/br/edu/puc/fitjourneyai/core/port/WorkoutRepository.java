package br.edu.puc.fitjourneyai.core.port;

import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

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
}
