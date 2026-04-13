package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommandIntentDetectorTest {

    private final CommandIntentDetector detector = new CommandIntentDetector();

    @ParameterizedTest
    @CsvSource({
            "/start, START",
            "/menu, MENU",
            "/ajuda, AJUDA",
            "/help, AJUDA",
            "/registro, REGISTRO",
            "/peso, REGISTRO_PESO",
            "/medidas, REGISTRO_MEDIDAS",
            "/treino, TREINO",
            "/treino_feito, TREINO_FEITO",
            "/progresso, PROGRESSO",
            "/resumo, RESUMO",
            "/config, CONFIG",
            "/cancelar, CANCELAR"
    })
    @DisplayName("Deve detectar comandos conhecidos")
    void deveDetectarComandos(String command, String expectedIntent) {
        Optional<IntentType> result = detector.detect(command);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(IntentType.valueOf(expectedIntent));
    }

    @Test
    @DisplayName("Deve ignorar comandos com @botname")
    void deveIgnorarBotname() {
        Optional<IntentType> result = detector.detect("/start@FitJourneyAIBot");
        assertThat(result).contains(IntentType.START);
    }

    @Test
    @DisplayName("Deve retornar empty para texto sem /")
    void deveRetornarEmptyParaTextoComum() {
        assertThat(detector.detect("quero treinar")).isEmpty();
        assertThat(detector.detect("72.5")).isEmpty();
        assertThat(detector.detect("oi")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar empty para comando desconhecido")
    void deveRetornarEmptyParaComandoDesconhecido() {
        assertThat(detector.detect("/banana")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar empty para null e vazio")
    void deveRetornarEmptyParaNullEVazio() {
        assertThat(detector.detect(null)).isEmpty();
        assertThat(detector.detect("")).isEmpty();
        assertThat(detector.detect("  ")).isEmpty();
    }

    @Test
    @DisplayName("Deve ter prioridade 0 (primeiro na cadeia)")
    void deveTerPrioridadeZero() {
        assertThat(detector.priority()).isEqualTo(0);
    }
}
