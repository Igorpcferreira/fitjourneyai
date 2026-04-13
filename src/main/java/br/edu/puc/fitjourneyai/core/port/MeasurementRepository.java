package br.edu.puc.fitjourneyai.core.port;

import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.MeasurementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída para persistência de medidas corporais.
 */
public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    /**
     * Busca a medida mais recente de um tipo específico para o usuário.
     */
    Optional<Measurement> findTopByUserAndTipoOrderByDataRegistroDesc(User user, MeasurementType tipo);

    /**
     * Busca medidas de um tipo em um período (para gráficos de progresso).
     */
    List<Measurement> findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(
            User user,
            MeasurementType tipo,
            LocalDateTime inicio,
            LocalDateTime fim
    );
}
