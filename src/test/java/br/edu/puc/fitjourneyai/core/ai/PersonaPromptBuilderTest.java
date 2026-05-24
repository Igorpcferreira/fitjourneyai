package br.edu.puc.fitjourneyai.core.ai;

import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.IntensityLevel;
import br.edu.puc.fitjourneyai.core.model.enums.PersonaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaPromptBuilderTest {

    @ParameterizedTest
    @EnumSource(PersonaType.class)
    @DisplayName("Deve gerar prompt conversacional para cada persona")
    void deveGerarPromptConversacionalParaCadaPersona(PersonaType persona) {
        User user = User.builder().persona(persona).intensityLevel(IntensityLevel.MODERADO).build();
        String prompt = PersonaPromptBuilder.buildConversationalPrompt(user);

        assertThat(prompt).contains("PERSONA:");
        assertThat(prompt).contains("INTENSIDADE");
        assertThat(prompt).contains(persona.getLabel());
        assertThat(prompt).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(IntensityLevel.class)
    @DisplayName("Deve gerar prompt com cada nivel de intensidade")
    void deveGerarPromptComCadaIntensidade(IntensityLevel intensity) {
        User user = User.builder().persona(PersonaType.COACH_AMIGO).intensityLevel(intensity).build();
        String prompt = PersonaPromptBuilder.buildConversationalPrompt(user);

        assertThat(prompt).contains(intensity.getLabel());
    }

    @Test
    @DisplayName("Deve usar defaults quando persona e null")
    void deveUsarDefaultsQuandoNull() {
        User user = User.builder().build();
        String prompt = PersonaPromptBuilder.buildConversationalPrompt(user);

        assertThat(prompt).contains("Coach Amigo");
        assertThat(prompt).contains("Moderado");
    }

    @ParameterizedTest
    @EnumSource(PersonaType.class)
    @DisplayName("Deve gerar prompt de treino para cada persona")
    void deveGerarPromptTreinoParaCadaPersona(PersonaType persona) {
        User user = User.builder().persona(persona).intensityLevel(IntensityLevel.INTENSO).build();
        String prompt = PersonaPromptBuilder.buildWorkoutPrompt(user);

        assertThat(prompt).contains("ESTILO DA PERSONA:");
        assertThat(prompt).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(PersonaType.class)
    @DisplayName("Deve gerar prompt de nudge para cada persona")
    void deveGerarPromptNudgeParaCadaPersona(PersonaType persona) {
        User user = User.builder().persona(persona).intensityLevel(IntensityLevel.LEVE).build();
        String prompt = PersonaPromptBuilder.buildNudgePrompt(user);

        assertThat(prompt).contains("persona:");
        assertThat(prompt).contains("Exemplo:");
    }

    @ParameterizedTest
    @EnumSource(PersonaType.class)
    @DisplayName("Deve gerar prompt pos-treino para cada persona")
    void deveGerarPromptPosTreinoParaCadaPersona(PersonaType persona) {
        User user = User.builder().persona(persona).build();
        String prompt = PersonaPromptBuilder.buildPostWorkoutMotivation(user);

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Português do Brasil");
    }

    @Test
    @DisplayName("Drill Sergeant intenso deve mencionar desafio")
    void drillSergeantIntensoDeveDesafiar() {
        User user = User.builder().persona(PersonaType.DRILL_SERGEANT)
                .intensityLevel(IntensityLevel.INTENSO).build();
        String prompt = PersonaPromptBuilder.buildWorkoutPrompt(user);

        assertThat(prompt).contains("desafiador");
    }

    @Test
    @DisplayName("Estoico deve mencionar disciplina")
    void estoicoDeveMencionarDisciplina() {
        User user = User.builder().persona(PersonaType.ESTOICO)
                .intensityLevel(IntensityLevel.MODERADO).build();
        String prompt = PersonaPromptBuilder.buildNudgePrompt(user);

        assertThat(prompt).containsIgnoringCase("disciplina");
    }
}
