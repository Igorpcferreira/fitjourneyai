package br.edu.puc.fitjourneyai.core.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GoalTypeTest {

    @ParameterizedTest
    @CsvSource({
            "1, EMAGRECER",
            "2, GANHAR_MUSCULO",
            "3, MELHORAR_CONDICIONAMENTO",
            "4, CORRER_5K_10K",
            "5, SAUDE_BEM_ESTAR",
            "6, GANHAR_FORCA",
            "emagrecer, EMAGRECER",
            "ganhar massa, GANHAR_MUSCULO",
            "hipertrofia, GANHAR_MUSCULO",
            "condicionamento, MELHORAR_CONDICIONAMENTO",
            "correr, CORRER_5K_10K",
            "saude, SAUDE_BEM_ESTAR",
            "forca, GANHAR_FORCA"
    })
    @DisplayName("Deve mapear entrada do usuario para GoalType")
    void deveMapearpEntrada(String input, GoalType expected) {
        assertThat(GoalType.fromUserInput(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Deve retornar null para entrada invalida")
    void deveRetornarNullParaInvalida() {
        assertThat(GoalType.fromUserInput("xyz")).isNull();
        assertThat(GoalType.fromUserInput(null)).isNull();
        assertThat(GoalType.fromUserInput("")).isNull();
    }

    @Test
    @DisplayName("Deve ter labels corretos")
    void deveRetornarLabels() {
        assertThat(GoalType.EMAGRECER.getLabel()).isEqualTo("Emagrecer");
        assertThat(GoalType.GANHAR_MUSCULO.getLabel()).contains("muscular");
        assertThat(GoalType.GANHAR_FORCA.getLabel()).contains("for");
    }

    @Test
    @DisplayName("Deve ter 6 valores")
    void deveTer6Valores() {
        assertThat(GoalType.values()).hasSize(6);
    }
}
