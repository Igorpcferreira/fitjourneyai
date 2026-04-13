package br.edu.puc.fitjourneyai.core.flow.conversation;

import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.intent.CommandIntentDetector;
import br.edu.puc.fitjourneyai.core.intent.IntentDetector;
import br.edu.puc.fitjourneyai.core.intent.KeywordIntentDetector;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.orchestrator.ConversationOrchestrator;
import br.edu.puc.fitjourneyai.core.orchestrator.FlowRegistry;
import br.edu.puc.fitjourneyai.core.port.ConversationStateRepository;
import br.edu.puc.fitjourneyai.core.port.MessageRepository;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import br.edu.puc.fitjourneyai.infrastructure.ConversationCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationOrchestratorTest {

    @Mock private UserRepository userRepository;
    @Mock private ConversationStateRepository stateRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private FlowHandler onboardingHandler;
    @Mock private FlowHandler navigationHandler;
    @Mock private ConversationCacheService cacheService;

    private ConversationOrchestrator orchestrator;
    private User user;
    private ConversationState state;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .onboardingConcluido(false)
                .nudgesEnabled(true)
                .build();

        state = ConversationState.builder()
                .id(1L)
                .user(user)
                .currentFlow(ConversationFlowType.NONE)
                .currentStep(null)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();

        when(onboardingHandler.getFlowType()).thenReturn(ConversationFlowType.ONBOARDING);
        when(navigationHandler.getFlowType()).thenReturn(ConversationFlowType.NAVIGATION);

        FlowRegistry registry = new FlowRegistry(List.of(onboardingHandler, navigationHandler));

        List<IntentDetector> detectors = List.of(
                new CommandIntentDetector(),
                new KeywordIntentDetector()
        );

        orchestrator = new ConversationOrchestrator(
                userRepository,
                stateRepository,
                messageRepository,
                registry,
                detectors,
                new ObjectMapper(),
                cacheService
        );
    }

    @Test
    @DisplayName("Deve criar novo usuário e estado quando chatId é desconhecido")
    void deveCriarUsuarioNovo() {
        when(cacheService.allowRequest(12345L)).thenReturn(true);
        when(userRepository.findByTelegramChatId(12345L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(stateRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(stateRepository.save(any(ConversationState.class))).thenReturn(state);

        when(onboardingHandler.handle(any())).thenReturn(
                FlowResult.text(
                        "Oi! Qual seu nome?",
                        ConversationFlowType.ONBOARDING,
                        1,
                        Map.of(),
                        null
                )
        );

        FlowResult result = orchestrator.handleMessage(12345L, "/start");

        assertThat(result).isNotNull();
        assertThat(result.responseText()).contains("nome");

        verify(userRepository, times(2)).save(any(User.class));
        verify(stateRepository, atLeastOnce()).save(any(ConversationState.class));
        verify(onboardingHandler).handle(any());
    }

    @Test
    @DisplayName("Deve rotear /start para OnboardingFlowHandler")
    void deveRotearStartParaOnboarding() {
        when(cacheService.allowRequest(12345L)).thenReturn(true);
        when(userRepository.findByTelegramChatId(12345L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(stateRepository.findByUserId(1L)).thenReturn(Optional.of(state));
        when(stateRepository.save(any(ConversationState.class))).thenReturn(state);

        when(onboardingHandler.handle(any())).thenReturn(
                FlowResult.text(
                        "Qual seu nome?",
                        ConversationFlowType.ONBOARDING,
                        1,
                        Map.of(),
                        null
                )
        );

        FlowResult result = orchestrator.handleMessage(12345L, "/start");

        assertThat(result).isNotNull();
        verify(onboardingHandler).handle(any());
        verify(navigationHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Deve rotear /menu para NavigationFlowHandler")
    void deveRotearMenuParaNavigation() {
        user.setOnboardingConcluido(true);

        when(cacheService.allowRequest(12345L)).thenReturn(true);
        when(userRepository.findByTelegramChatId(12345L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(stateRepository.findByUserId(1L)).thenReturn(Optional.of(state));
        when(stateRepository.save(any(ConversationState.class))).thenReturn(state);

        when(navigationHandler.handle(any())).thenReturn(
                FlowResult.done("Menu principal", "Use /menu")
        );

        FlowResult result = orchestrator.handleMessage(12345L, "/menu");

        assertThat(result).isNotNull();
        assertThat(result.responseText()).contains("Menu");
        verify(navigationHandler).handle(any());
        verify(onboardingHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Deve priorizar fluxo ativo sobre intenção detectada")
    void devePriorizarFluxoAtivo() {
        state.setCurrentFlow(ConversationFlowType.ONBOARDING);
        state.setCurrentStep(2);

        when(cacheService.allowRequest(12345L)).thenReturn(true);
        when(userRepository.findByTelegramChatId(12345L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(stateRepository.findByUserId(1L)).thenReturn(Optional.of(state));
        when(stateRepository.save(any(ConversationState.class))).thenReturn(state);

        when(onboardingHandler.handle(any())).thenReturn(
                FlowResult.text(
                        "Qual objetivo?",
                        ConversationFlowType.ONBOARDING,
                        3,
                        Map.of(),
                        null
                )
        );

        FlowResult result = orchestrator.handleMessage(12345L, "peso");

        assertThat(result).isNotNull();
        assertThat(result.responseText()).contains("objetivo");
        verify(onboardingHandler).handle(any());
        verify(navigationHandler, never()).handle(any());
    }

    @Test
    @DisplayName("/cancelar deve resetar fluxo ativo")
    void deveCancelarFluxoAtivo() {
        state.setCurrentFlow(ConversationFlowType.ONBOARDING);
        state.setCurrentStep(3);

        when(cacheService.allowRequest(12345L)).thenReturn(true);
        when(userRepository.findByTelegramChatId(12345L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(stateRepository.findByUserId(1L)).thenReturn(Optional.of(state));
        when(stateRepository.save(any(ConversationState.class))).thenReturn(state);

        FlowResult result = orchestrator.handleMessage(12345L, "/cancelar");

        assertThat(result).isNotNull();
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(result.responseText()).contains("cancelado");

        verify(onboardingHandler, never()).handle(any());
        verify(navigationHandler, never()).handle(any());
        verify(stateRepository, atLeastOnce()).save(any(ConversationState.class));
    }

    @Test
    @DisplayName("Deve retornar null para chatId nulo")
    void deveRetornarNullParaChatIdNulo() {
        FlowResult result = orchestrator.handleMessage(null, "oi");

        assertThat(result).isNull();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(stateRepository);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(cacheService);
        verify(onboardingHandler, never()).handle(any());
        verify(navigationHandler, never()).handle(any());
    }
}
