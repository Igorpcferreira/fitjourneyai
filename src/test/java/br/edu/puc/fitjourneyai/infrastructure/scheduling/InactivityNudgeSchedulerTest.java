package br.edu.puc.fitjourneyai.infrastructure.scheduling;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.GoalType;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactivityNudgeSchedulerTest {

    @Mock private UserRepository userRepository;
    @Mock private AiService aiService;
    @Mock private MessageGateway messageGateway;

    @InjectMocks private InactivityNudgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "inactivityDays", 3);
        ReflectionTestUtils.setField(scheduler, "cooldownHours", 48);
    }

    @Test
    @DisplayName("Não deve enviar nudge quando não há usuários inativos")
    void naoDeveEnviarQuandoSemInativos() {
        when(userRepository.findUsersForNudge(any(), any())).thenReturn(Collections.emptyList());

        scheduler.checkAndNudgeInactiveUsers();

        verify(messageGateway, never()).sendText(any(), any());
    }

    @Test
    @DisplayName("Deve enviar nudge com mensagem da IA para usuário inativo")
    void deveEnviarNudgeComIA() {
        User user = buildUser(5);
        when(userRepository.findUsersForNudge(any(), any())).thenReturn(List.of(user));
        when(aiService.composeNudgeMessage(any(), anyInt()))
                .thenReturn("Ei Igor, senti sua falta! Bora treinar?");

        scheduler.checkAndNudgeInactiveUsers();

        verify(messageGateway).sendText(eq(12345L), eq("Ei Igor, senti sua falta! Bora treinar?"));
        verify(userRepository).save(user);
        assertThat(user.getLastNudgeAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve usar fallback quando IA falha")
    void deveUsarFallbackQuandoIAFalha() {
        User user = buildUser(5);
        when(userRepository.findUsersForNudge(any(), any())).thenReturn(List.of(user));
        when(aiService.composeNudgeMessage(any(), anyInt())).thenThrow(new RuntimeException("API down"));

        scheduler.checkAndNudgeInactiveUsers();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageGateway).sendText(eq(12345L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("Igor");
        assertThat(msgCaptor.getValue()).contains("5 dias");
    }

    @Test
    @DisplayName("Deve enviar nudge leve para inatividade curta (<=3 dias)")
    void deveEnviarNudgeLeve() {
        User user = buildUser(2);
        when(userRepository.findUsersForNudge(any(), any())).thenReturn(List.of(user));
        when(aiService.composeNudgeMessage(any(), anyInt())).thenReturn(null); // null = fallback

        scheduler.checkAndNudgeInactiveUsers();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageGateway).sendText(eq(12345L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("2 dias");
        assertThat(msgCaptor.getValue()).contains("/peso");
    }

    @Test
    @DisplayName("Deve enviar nudge empático para inatividade longa (>7 dias)")
    void deveEnviarNudgeEmpatico() {
        User user = buildUser(12);
        when(userRepository.findUsersForNudge(any(), any())).thenReturn(List.of(user));
        when(aiService.composeNudgeMessage(any(), anyInt())).thenReturn(null);

        scheduler.checkAndNudgeInactiveUsers();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageGateway).sendText(eq(12345L), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("12 dias");
        assertThat(msgCaptor.getValue()).contains("Sem julgamento");
    }

    @Test
    @DisplayName("Deve processar múltiplos usuários")
    void deveProcessarMultiplosUsuarios() {
        User u1 = buildUser(3);
        User u2 = User.builder().id(2L).telegramChatId(99999L).nome("Maria")
                .objetivo(GoalType.EMAGRECER).nudgesEnabled(true)
                .lastInteractionAt(LocalDateTime.now().minusDays(4)).build();

        when(userRepository.findUsersForNudge(any(), any())).thenReturn(List.of(u1, u2));
        when(aiService.composeNudgeMessage(any(), anyInt())).thenReturn("Mensagem IA");

        scheduler.checkAndNudgeInactiveUsers();

        verify(messageGateway, times(2)).sendText(any(), any());
        verify(userRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Não deve quebrar se envio falhar para um usuário")
    void naoDeveQuebrarSeEnvioFalhar() {
        User u1 = buildUser(3);
        User u2 = User.builder().id(2L).telegramChatId(99999L).nome("Maria")
                .objetivo(GoalType.EMAGRECER).nudgesEnabled(true)
                .lastInteractionAt(LocalDateTime.now().minusDays(4)).build();

        when(userRepository.findUsersForNudge(any(), any())).thenReturn(List.of(u1, u2));
        when(aiService.composeNudgeMessage(any(), anyInt())).thenReturn("Msg");
        doThrow(new RuntimeException("Telegram error")).when(messageGateway).sendText(eq(12345L), any());

        scheduler.checkAndNudgeInactiveUsers();

        // Deve ter tentado enviar para o segundo mesmo com erro no primeiro
        verify(messageGateway).sendText(eq(99999L), any());
    }

    private User buildUser(int daysInactive) {
        return User.builder()
                .id(1L).telegramChatId(12345L).nome("Igor")
                .objetivo(GoalType.GANHAR_MUSCULO).nudgesEnabled(true)
                .lastInteractionAt(LocalDateTime.now().minusDays(daysInactive))
                .build();
    }
}
