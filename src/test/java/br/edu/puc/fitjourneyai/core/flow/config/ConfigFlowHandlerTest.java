package br.edu.puc.fitjourneyai.core.flow.config;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.ConversationState;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import br.edu.puc.fitjourneyai.core.model.enums.IntensityLevel;
import br.edu.puc.fitjourneyai.core.model.enums.PersonaType;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigFlowHandlerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ConfigFlowHandler handler;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .telegramChatId(12345L)
                .nome("Igor")
                .onboardingConcluido(true)
                .persona(PersonaType.COACH_AMIGO)
                .intensityLevel(IntensityLevel.MODERADO)
                .build();
    }

    @Test
    @DisplayName("Deve retornar CONFIG como flowType")
    void flowType() {
        assertThat(handler.getFlowType()).isEqualTo(ConversationFlowType.CONFIG);
    }

    @Test
    @DisplayName("Deve mostrar menu de configuracao no inicio")
    void deveExibirMenu() {
        FlowResult result = handler.handle(ctx(null, "config"));

        assertThat(result.responseText()).contains("Coach Amigo");
        assertThat(result.responseText()).contains("Filósofo Estoico");
        assertThat(result.responseText()).contains("Sargento");
        assertThat(result.responseText()).contains("Atleta");
        assertThat(result.responseText()).contains("Monge");
        assertThat(result.responseText()).contains("Cientista");
    }

    @Test
    @DisplayName("Deve aceitar escolha de persona por numero")
    void deveAceitarPersonaPorNumero() {
        when(userRepository.save(any(User.class))).thenReturn(user);

        FlowResult result = handler.handle(ctx(1, "2"));

        assertThat(result.responseText()).contains("Filósofo Estoico");
        assertThat(result.responseText()).contains("intensidade");
        assertThat(user.getPersona()).isEqualTo(PersonaType.ESTOICO);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Deve aceitar escolha de intensidade e salvar")
    void deveAceitarIntensidadeESalvar() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.DRILL_SERGEANT);

        FlowResult result = handler.handle(ctx(2, "3"));

        assertThat(result.responseText()).contains("Intenso");
        assertThat(result.responseText()).contains("Preview");
        assertThat(user.getIntensityLevel()).isEqualTo(IntensityLevel.INTENSO);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Deve aceitar intensidade por frase natural de áudio")
    void deveAceitarIntensidadePorFraseNaturalAudio() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.DRILL_SERGEANT);

        FlowResult result = handler.handle(ctx(2, "Pode ser o intenso"));

        assertThat(result.responseText()).contains("Intenso");
        assertThat(user.getIntensityLevel()).isEqualTo(IntensityLevel.INTENSO);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Deve rejeitar persona invalida")
    void deveRejeitarPersonaInvalida() {
        FlowResult result = handler.handle(ctx(1, "abc"));

        assertThat(result.responseText()).contains("1 a 6");
    }

    @Test
    @DisplayName("Deve reiniciar menu ao receber /config dentro do fluxo")
    void deveReiniciarMenuComComandoConfig() {
        FlowResult result = handler.handle(ctx(1, "/config"));

        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.CONFIG);
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("Configurações");
        assertThat(result.responseText()).contains("Persona atual");
    }

    @Test
    @DisplayName("Deve rejeitar intensidade invalida")
    void deveRejeitarIntensidadeInvalida() {
        FlowResult result = handler.handle(ctx(2, "xyz"));

        assertThat(result.responseText()).contains("1");
    }

    @Test
    @DisplayName("Preview do Sargento Intenso deve ser hardcore")
    void previewSargentoIntenso() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.DRILL_SERGEANT);

        FlowResult result = handler.handle(ctx(2, "3"));

        assertThat(result.responseText()).contains("desistir");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Preview do Estoico Leve deve ser gentil")
    void previewEstoicoLeve() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.ESTOICO);

        FlowResult result = handler.handle(ctx(2, "1"));

        assertThat(result.responseText()).contains("constância");
        verify(userRepository).save(user);
    }

    private FlowContext ctx(Integer step, String text) {
        ConversationState state = ConversationState.builder()
                .id(1L)
                .user(user)
                .currentFlow(ConversationFlowType.CONFIG)
                .currentStep(step)
                .partialData("{}")
                .updatedAt(LocalDateTime.now())
                .build();

        return new FlowContext(
                12345L,
                user,
                state,
                text,
                IntentType.CONFIG,
                LocalDateTime.now()
        );
    }
}
