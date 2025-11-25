package br.edu.puc.fitjourneyai.service.scheduling;

import br.edu.puc.fitjourneyai.domain.entity.User;
import br.edu.puc.fitjourneyai.domain.repository.UserRepository;
import br.edu.puc.fitjourneyai.service.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InactivityNudgeScheduler {

    private final UserRepository userRepository;
    private final TelegramService telegramService;

    // Quantos dias parado para considerar "inativo"
    private static final long DAYS_INACTIVE_LIMIT = 7L;

    // Quantos dias de "cooldown" entre um nudge e outro
    private static final long DAYS_NUDGE_COOLDOWN = 3L;

    /**
     * 6.10 – Job diário de reengajamento.
     *
     * Executa todos os dias às 09:00 (horário do servidor).
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void checkInactiveUsersAndSendNudges() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime limiteInatividade = now.minusDays(DAYS_INACTIVE_LIMIT);
        LocalDateTime limiteReenvioNudge = now.minusDays(DAYS_NUDGE_COOLDOWN);

        List<User> candidates =
                userRepository.findUsersForNudge(limiteInatividade, limiteReenvioNudge);

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Encontrados {} usuários inativos para reengajamento", candidates.size());

        for (User user : candidates) {
            try {
                sendNudge(user, now);
            } catch (Exception e) {
                log.warn("Erro ao enviar nudge para userId={} chatId={}",
                        user.getId(), user.getTelegramChatId(), e);
            }
        }
    }

    private void sendNudge(User user, LocalDateTime now) {
        if (user.getTelegramChatId() == null) {
            return;
        }

        LocalDateTime lastInteraction = user.getLastInteractionAt();
        long diasParado = lastInteraction == null
                ? DAYS_INACTIVE_LIMIT
                : ChronoUnit.DAYS.between(lastInteraction, now);

        String nome = user.getNome() != null && !user.getNome().isBlank()
                ? user.getNome()
                : "por aí";
        String objetivo = user.getObjetivo() != null
                ? user.getObjetivo().getLabel()
                : "seus objetivos de treino";

        String texto = """
                Oi %s! 👋

                Notei que faz cerca de %d dia(s) que você não registra nada por aqui.
                Que tal retomar hoje e dar mais um passo em direção a %s?

                Algumas opções rápidas:
                - /treino_feito → registrar o treino de hoje
                - /treino       → pedir um treino sugerido
                - /peso         → atualizar seu peso atual

                Quando quiser, é só me chamar 💪
                """.formatted(nome, diasParado, objetivo);

        telegramService.sendMessage(user.getTelegramChatId(), texto);

        user.setLastNudgeAt(now);
        userRepository.save(user);
    }
}
