package br.edu.puc.fitjourneyai.core.port;

import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Porta de saída para persistência do estado conversacional.
 */
public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {

    Optional<ConversationState> findByUserId(Long userId);
}
