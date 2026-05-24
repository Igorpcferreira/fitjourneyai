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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigFlowHandler — cobertura adicional de previews e personas")
class ConfigFlowHandlerExtraTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ConfigFlowHandler handler;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).telegramChatId(12345L).nome("Igor")
                .onboardingConcluido(true)
                .persona(PersonaType.COACH_AMIGO)
                .intensityLevel(IntensityLevel.MODERADO)
                .build();
    }

    // ─── Personas — todos os 6 valores ────────────────────────────────────

    @ParameterizedTest(name = "Persona #{0} deve ser aceita")
    @CsvSource({"1,COACH_AMIGO", "2,ESTOICO", "3,DRILL_SERGEANT", "4,ATLETA", "5,MONGE_GUERREIRO", "6,CIENTISTA"})
    @DisplayName("Deve aceitar todos os números de persona (1-6)")
    void deveAceitarTodasPersonas(String input, String expectedPersona) {
        when(userRepository.save(any(User.class))).thenReturn(user);
        FlowResult result = handler.handle(ctx(1, input));
        assertThat(result.nextStep()).isEqualTo(2);
        assertThat(user.getPersona().name()).isEqualTo(expectedPersona);
    }

    // ─── Intensidades — todos os 3 valores ────────────────────────────────

    @ParameterizedTest(name = "Intensidade #{0}")
    @CsvSource({"1,LEVE", "2,MODERADO", "3,INTENSO"})
    @DisplayName("Deve aceitar todas as intensidades (1-3)")
    void deveAceitarTodasIntensidades(String input, String expectedLevel) {
        when(userRepository.save(any(User.class))).thenReturn(user);
        FlowResult result = handler.handle(ctx(2, input));
        assertThat(result.nextFlow()).isEqualTo(ConversationFlowType.NONE);
        assertThat(user.getIntensityLevel().name()).isEqualTo(expectedLevel);
    }

    // ─── Previews de cada combinação persona × intensidade ────────────────

    @Test
    @DisplayName("Preview Atleta Leve deve conter 'atletas'")
    void previewAtletaLeve() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.ATLETA);
        FlowResult result = handler.handle(ctx(2, "1"));
        assertThat(result.responseText()).containsIgnoringCase("atleta");
    }

    @Test
    @DisplayName("Preview Atleta Moderado deve conter 'performance'")
    void previewAtletaModerado() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.ATLETA);
        FlowResult result = handler.handle(ctx(2, "2"));
        assertThat(result.responseText()).containsIgnoringCase("performance");
    }

    @Test
    @DisplayName("Preview Atleta Intenso deve conter 'campeão'")
    void previewAtletaIntenso() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.ATLETA);
        FlowResult result = handler.handle(ctx(2, "3"));
        assertThat(result.responseText()).containsIgnoringCase("campeão");
    }

    @Test
    @DisplayName("Preview Monge Guerreiro Leve deve conter 'água'")
    void previewMongeGuerreiroLeve() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.MONGE_GUERREIRO);
        FlowResult result = handler.handle(ctx(2, "1"));
        assertThat(result.responseText()).containsIgnoringCase("gua");
    }

    @Test
    @DisplayName("Preview Monge Guerreiro Moderado deve conter 'guerreiro'")
    void previewMongeGuerreiroModerado() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.MONGE_GUERREIRO);
        FlowResult result = handler.handle(ctx(2, "2"));
        assertThat(result.responseText()).containsIgnoringCase("guerreiro");
    }

    @Test
    @DisplayName("Preview Monge Guerreiro Intenso deve conter 'dor'")
    void previewMongeGuerreiroIntenso() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.MONGE_GUERREIRO);
        FlowResult result = handler.handle(ctx(2, "3"));
        assertThat(result.responseText()).containsIgnoringCase("dor");
    }

    @Test
    @DisplayName("Preview Cientista Leve deve conter 'endorfina'")
    void previewCientistaLeve() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.CIENTISTA);
        FlowResult result = handler.handle(ctx(2, "1"));
        assertThat(result.responseText()).containsIgnoringCase("endorfina");
    }

    @Test
    @DisplayName("Preview Cientista Moderado deve conter 'síntese'")
    void previewCientistaModerado() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.CIENTISTA);
        FlowResult result = handler.handle(ctx(2, "2"));
        assertThat(result.responseText()).containsIgnoringCase("ntese");
    }

    @Test
    @DisplayName("Preview Cientista Intenso deve conter 'dados'")
    void previewCientistaIntenso() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.CIENTISTA);
        FlowResult result = handler.handle(ctx(2, "3"));
        assertThat(result.responseText()).containsIgnoringCase("dados");
    }

    @Test
    @DisplayName("Preview Coach Amigo (default) Leve deve conter 'parceiro'")
    void previewCoachAmigoLeve() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.COACH_AMIGO);
        FlowResult result = handler.handle(ctx(2, "1"));
        assertThat(result.responseText()).containsIgnoringCase("parceiro");
    }

    @Test
    @DisplayName("Preview Estoico Moderado deve conter 'disciplina'")
    void previewEstoicoModerado() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.ESTOICO);
        FlowResult result = handler.handle(ctx(2, "2"));
        assertThat(result.responseText()).containsIgnoringCase("disciplina");
    }

    @Test
    @DisplayName("Preview Sargento Moderado deve conter 'bota peso'")
    void previewSargentoModerado() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.DRILL_SERGEANT);
        FlowResult result = handler.handle(ctx(2, "2"));
        assertThat(result.responseText()).containsIgnoringCase("peso");
    }

    @Test
    @DisplayName("Preview Sargento Leve deve conter 'soldado'")
    void previewSargentoLeve() {
        when(userRepository.save(any(User.class))).thenReturn(user);
        user.setPersona(PersonaType.DRILL_SERGEANT);
        FlowResult result = handler.handle(ctx(2, "1"));
        assertThat(result.responseText()).containsIgnoringCase("soldado");
    }

    // ─── step default (>2) ────────────────────────────────────────────────

    @Test
    @DisplayName("Step inválido (>2) deve exibir menu de config")
    void stepInvalidoExibeMenu() {
        FlowResult result = handler.handle(ctx(99, "algo"));
        assertThat(result.nextStep()).isEqualTo(1);
        assertThat(result.responseText()).contains("Configurações");
    }

    // ─── HELPER ───────────────────────────────────────────────────────────

    private FlowContext ctx(Integer step, String text) {
        ConversationState state = ConversationState.builder()
                .id(1L).user(user)
                .currentFlow(ConversationFlowType.CONFIG)
                .currentStep(step).partialData("{}")
                .updatedAt(LocalDateTime.now()).build();
        return new FlowContext(12345L, user, state, text, IntentType.CONFIG, LocalDateTime.now());
    }
}

