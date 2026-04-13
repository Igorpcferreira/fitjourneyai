package br.edu.puc.fitjourneyai.core.port;

import br.edu.puc.fitjourneyai.core.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída para persistência de usuários.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramChatId(Long telegramChatId);

    /**
     * Busca usuários elegíveis para nudge de reengajamento.
     * Critérios: nudges habilitados, última interação antes do limite,
     * e último nudge enviado antes do limite de reenvio (ou nunca enviado).
     */
    @Query("""
           SELECT u FROM User u
           WHERE u.nudgesEnabled = true
             AND u.onboardingConcluido = true
             AND u.lastInteractionAt IS NOT NULL
             AND u.lastInteractionAt < :limiteInatividade
             AND (u.lastNudgeAt IS NULL OR u.lastNudgeAt < :limiteReenvio)
           """)
    List<User> findUsersForNudge(
            @Param("limiteInatividade") LocalDateTime limiteInatividade,
            @Param("limiteReenvio") LocalDateTime limiteReenvio
    );
}
