package br.edu.puc.fitjourneyai.domain.repository;

import br.edu.puc.fitjourneyai.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramChatId(Long telegramChatId);

    @Query("""
           SELECT u
           FROM User u
           WHERE u.nudgesEnabled = true
             AND u.lastInteractionAt IS NOT NULL
             AND u.lastInteractionAt < :limiteInatividade
             AND (u.lastNudgeAt IS NULL OR u.lastNudgeAt < :limiteReenvio)
           """)
    List<User> findUsersForNudge(@Param("limiteInatividade") LocalDateTime limiteInatividade,
                                 @Param("limiteReenvio") LocalDateTime limiteReenvio);

}
