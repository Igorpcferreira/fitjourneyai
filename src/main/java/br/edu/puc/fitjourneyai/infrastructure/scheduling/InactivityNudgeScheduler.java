package br.edu.puc.fitjourneyai.infrastructure.scheduling;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Fluxo 9 — Reengajamento por Inatividade.
 * <p>
 * Conforme Fig.14 do Pacote Consolidado:
 * Job agendado que recupera candidatos inativos, verifica elegibilidade,
 * compõe mensagem contextual via IA com CTA claro, oferece fallback
 * template, registra evento de nudge e acompanha resposta.
 * <p>
 * Executa a cada 6 horas por padrão. Envia no máximo 1 nudge por
 * usuário a cada 48h para não ser invasivo.
 * <p>
 * Princípios:
 * <ul>
 *   <li>IA personaliza a mensagem (tom, objetivo, histórico)</li>
 *   <li>Fallback determinístico se IA falhar</li>
 *   <li>Não envia para quem desabilitou nudges</li>
 *   <li>Registra last_nudge_at para controle de frequência</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InactivityNudgeScheduler {

    private final UserRepository userRepository;
    private final AiService aiService;
    private final MessageGateway messageGateway;

    /** Dias sem interação para considerar inativo. */
    @Value("${fitjourneyai.nudge.inactivity-days:3}")
    private int inactivityDays;

    /** Horas mínimas entre nudges para o mesmo usuário. */
    @Value("${fitjourneyai.nudge.cooldown-hours:48}")
    private int cooldownHours;

    /**
     * Executa a cada 6 horas: busca usuários inativos e envia mensagem de reengajamento.
     */
    @Scheduled(fixedRateString = "${fitjourneyai.nudge.interval-ms:21600000}") // 6h default
    public void checkAndNudgeInactiveUsers() {
        LocalDateTime limiteInatividade = LocalDateTime.now().minusDays(inactivityDays);
        LocalDateTime limiteReenvio = LocalDateTime.now().minusHours(cooldownHours);

        List<User> inativos = userRepository.findUsersForNudge(limiteInatividade, limiteReenvio);

        if (inativos.isEmpty()) {
            log.debug("Nenhum usuário elegível para nudge");
            return;
        }

        log.info("Nudge de reengajamento: {} usuários elegíveis", inativos.size());

        for (User user : inativos) {
            try {
                sendNudge(user);
            } catch (Exception e) {
                log.error("Erro ao enviar nudge para user={}: {}", user.getId(), e.getMessage());
            }
        }
    }

    /**
     * Compõe e envia mensagem de reengajamento para um usuário.
     */
    private void sendNudge(User user) {
        int diasInativo = calcDaysInactive(user);
        String nome = user.getNome() != null ? user.getNome() : "amigo(a)";

        // Tenta gerar mensagem personalizada via IA
        String message = generateNudgeMessage(user, diasInativo);

        // Envia pelo Telegram
        messageGateway.sendText(user.getTelegramChatId(), message);

        // Atualiza timestamp do último nudge
        user.setLastNudgeAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Nudge enviado: user={}, nome={}, diasInativo={}", user.getId(), nome, diasInativo);
    }

    /**
     * Gera mensagem de nudge: tenta via IA, fallback determinístico.
     */
    private String generateNudgeMessage(User user, int diasInativo) {
        try {
            String aiMessage = aiService.composeNudgeMessage(user, diasInativo);
            if (aiMessage != null && !aiMessage.isBlank()) {
                return aiMessage;
            }
        } catch (Exception e) {
            log.warn("IA indisponível para nudge, usando fallback: {}", e.getMessage());
        }

        return buildFallbackNudge(user, diasInativo);
    }

    /**
     * Template de fallback quando a IA não está disponível.
     * Seleciona variação com base nos dias de inatividade.
     */
    private String buildFallbackNudge(User user, int diasInativo) {
        String nome = user.getNome() != null ? user.getNome() : "amigo(a)";
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().getLabel() : "seus objetivos";

        if (diasInativo <= 3) {
            return String.format("""
                    Ei, %s! 💪 Faz %d dias que você não aparece por aqui.
                    
                    Que tal registrar seu peso com /peso ou pedir um treino com /treino?
                    
                    Consistência é o segredo! 🔥""", nome, diasInativo);
        } else if (diasInativo <= 7) {
            return String.format("""
                    %s, senti sua falta! 🙏 Faz %d dias que a gente não conversa.
                    
                    Lembra do seu objetivo de %s? Cada dia conta!
                    
                    Manda /peso para a gente retomar ou /treino para um treino novo. Tô aqui te esperando! 🚀""",
                    nome, diasInativo, objetivo);
        } else {
            return String.format("""
                    %s! 👋 Faz um tempinho que você sumiu (%d dias).
                    
                    Sem julgamento nenhum — todo mundo tem fases. O importante é retomar! 🌟
                    
                    Quando quiser voltar, é só mandar /menu que eu te mostro tudo que posso fazer por você.
                    
                    Tô aqui te esperando, bora juntos! 💪""", nome, diasInativo);
        }
    }

    private int calcDaysInactive(User user) {
        if (user.getLastInteractionAt() == null) return 0;
        return (int) ChronoUnit.DAYS.between(user.getLastInteractionAt(), LocalDateTime.now());
    }
}
