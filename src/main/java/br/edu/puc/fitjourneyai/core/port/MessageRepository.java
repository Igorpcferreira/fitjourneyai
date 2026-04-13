package br.edu.puc.fitjourneyai.core.port;

import br.edu.puc.fitjourneyai.core.model.entity.Message;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Porta de saída para persistência do histórico de mensagens.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Busca as últimas N mensagens do usuário (para contexto da IA).
     */
    List<Message> findTop20ByUserOrderByDataHoraDesc(User user);

    /**
     * Busca as últimas 10 mensagens (para contexto conversacional compacto).
     */
    List<Message> findTop10ByUserOrderByDataHoraDesc(User user);
}
