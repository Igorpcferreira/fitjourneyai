package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIntentDetectorTest {

    private final KeywordIntentDetector detector = new KeywordIntentDetector();

    @ParameterizedTest
    @CsvSource({
            "72, REGISTRO_PESO",
            "72.5, REGISTRO_PESO",
            "'72,5', REGISTRO_PESO",
            "72kg, REGISTRO_PESO",
            "'meu peso hoje', REGISTRO_PESO",
            "'quero registrar medidas', REGISTRO_MEDIDAS",
            "'minha cintura', REGISTRO_MEDIDAS",
            "'quero um treino', TREINO",
            "'fiz treino hoje', TREINO_FEITO",
            "'terminei o treino', TREINO_FEITO",
            "'ver progresso', PROGRESSO",
            "'meu gráfico', PROGRESSO",
            "'resumo da semana', RESUMO",
            "'como funciona', AJUDA",
            "'quero cancelar', CANCELAR"
    })
    @DisplayName("Deve detectar palavras-chave")
    void deveDetectarPalavrasChave(String text, String expectedIntent) {
        Optional<IntentType> result = detector.detect(text);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(IntentType.valueOf(expectedIntent));
    }

    @Test
    @DisplayName("Deve retornar conversa para reações casuais")
    void deveRetornarEmptyParaTextoSemKeywords() {
        assertThat(detector.detect("bom dia")).contains(IntentType.CONVERSA);
        assertThat(detector.detect("obrigado")).contains(IntentType.CONVERSA);
    }

    @Test
    @DisplayName("Deve retornar empty para texto realmente sem keywords")
    void deveRetornarEmptyParaTextoRealmenteSemKeywords() {
        assertThat(detector.detect("como vai")).isEmpty();
    }

    @Test
    @DisplayName("Perguntas práticas de execução devem ir para conversa")
    void perguntasPraticasDevemIrParaConversa() {
        assertThat(detector.detect("Como faço supino?")).contains(IntentType.CONVERSA);
        assertThat(detector.detect("Como conseguir correr 5km?")).contains(IntentType.CONVERSA);
    }

    @Test
    @DisplayName("Deve ter prioridade 10 (segundo na cadeia)")
    void deveTerPrioridade10() {
        assertThat(detector.priority()).isEqualTo(10);
    }
}
