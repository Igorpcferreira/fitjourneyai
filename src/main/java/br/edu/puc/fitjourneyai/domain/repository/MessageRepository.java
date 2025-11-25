package br.edu.puc.fitjourneyai.domain.repository;

import br.edu.puc.fitjourneyai.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
