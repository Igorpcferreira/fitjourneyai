package br.edu.puc.fitjourneyai.core.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaTypeTest {

    @ParameterizedTest
    @CsvSource({
            "1, COACH_AMIGO",
            "2, ESTOICO",
            "3, DRILL_SERGEANT",
            "4, ATLETA",
            "5, MONGE_GUERREIRO",
            "6, CIENTISTA",
            "coach, COACH_AMIGO",
            "estoico, ESTOICO",
            "sargento, DRILL_SERGEANT",
            "goggins, DRILL_SERGEANT",
            "atleta, ATLETA",
            "monge, MONGE_GUERREIRO",
            "cientista, CIENTISTA"
    })
    @DisplayName("Deve mapear entrada do usuario para PersonaType")
    void deveMapearpEntrada(String input, PersonaType expected) {
        assertThat(PersonaType.fromUserInput(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Deve retornar null para entrada invalida")
    void deveRetornarNull() {
        assertThat(PersonaType.fromUserInput(null)).isNull();
        assertThat(PersonaType.fromUserInput("")).isNull();
        assertThat(PersonaType.fromUserInput("xyz")).isNull();
    }

    @Test
    @DisplayName("Deve ter 6 personas")
    void deveTer6Personas() {
        assertThat(PersonaType.values()).hasSize(6);
    }

    @Test
    @DisplayName("Cada persona deve ter label, subtitle e promptInstruction")
    void cadaPersonaDeveTerCampos() {
        for (PersonaType p : PersonaType.values()) {
            assertThat(p.getLabel()).isNotBlank();
            assertThat(p.getSubtitle()).isNotBlank();
            assertThat(p.getPromptInstruction()).isNotBlank();
        }
    }
}
