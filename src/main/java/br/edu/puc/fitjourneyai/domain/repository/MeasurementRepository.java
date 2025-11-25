package br.edu.puc.fitjourneyai.domain.repository;

import br.edu.puc.fitjourneyai.domain.entity.Measurement;
import br.edu.puc.fitjourneyai.domain.entity.User;
import br.edu.puc.fitjourneyai.domain.enums.MeasurementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    Optional<Measurement> findTopByUserAndTipoOrderByDataRegistroDesc(User user, MeasurementType tipo);
}
