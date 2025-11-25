package br.edu.puc.fitjourneyai.domain.repository;

import br.edu.puc.fitjourneyai.domain.entity.User;
import br.edu.puc.fitjourneyai.domain.entity.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByUserAndDataRealizacaoBetween(
            User user,
            LocalDateTime inicio,
            LocalDateTime fim
    );
}
