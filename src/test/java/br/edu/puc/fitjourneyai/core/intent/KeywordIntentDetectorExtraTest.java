package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIntentDetectorExtraTest {

    private final KeywordIntentDetector detector = new KeywordIntentDetector();

    @Test
    @DisplayName("retorna empty para null e blank")
    void retornaEmptyParaNullEBlank() {
        assertThat(detector.detect(null)).isEmpty();
        assertThat(detector.detect(" ")).isEmpty();
    }

    @Test
    @DisplayName("saudacao com pontuacao cai em casual reaction")
    void saudacaoComPontuacao() {
        assertThat(detector.detect("oi, tudo bem?")).contains(IntentType.CONVERSA);
    }

    @Test
    @DisplayName("prefixo parecido nao deve ser saudacao")
    void prefixoParecidoNaoEhSaudacao() {
        assertThat(detector.detect("oitava repeticao")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"72 kg", "100"})
    @DisplayName("detecta numero isolado valido")
    void detectaNumeroValido(String text) {
        assertThat(detector.detect(text)).contains(IntentType.REGISTRO_PESO);
    }

    @Test
    @DisplayName("detecta numero com virgula como peso")
    void detectaNumeroComVirgula() {
        assertThat(detector.detect("80,5kg")).contains(IntentType.REGISTRO_PESO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"72.", ",72", "72kgx", "abc123"})
    @DisplayName("nao detecta numero isolado invalido")
    void naoDetectaNumeroInvalido(String text) {
        assertThat(detector.detect(text)).isEmpty();
    }

    @Test
    @DisplayName("frase com treino+medidas nao cai em REGISTRO_MEDIDAS")
    void treinoComMedidasNaoCaiEmRegistroMedidas() {
        assertThat(detector.detect("treino de cintura")).contains(IntentType.TREINO);
    }

    @Test
    @DisplayName("detecta pedido de treino por startsWith 'um treino'")
    void detectaTreinoStartsWithUmTreino() {
        assertThat(detector.detect("um treino para peito")).contains(IntentType.TREINO);
    }

    @Test
    @DisplayName("detecta ajuda por 'me ajuda'")
    void detectaAjudaMeAjuda() {
        assertThat(detector.detect("me ajuda com o bot")).contains(IntentType.AJUDA);
    }

    @Test
    @DisplayName("nao detecta ajuda para agradecimento de ajuda")
    void naoDetectaAjudaAgradecimento() {
        assertThat(detector.detect("obrigado pela ajuda")).contains(IntentType.CONVERSA);
    }

    @Test
    @DisplayName("compromisso de treinar e registrar deve cair em conversa, nao gerar treino")
    void compromissoDeTreinarNaoGeraTreino() {
        assertThat(detector.detect("Beleza, pode deixar que hoje eu treino sem falta e faço o registro aqui!"))
                .contains(IntentType.CONVERSA);
        assertThat(detector.detect("Pode deixar que vou treinar e depois registrar aqui"))
                .contains(IntentType.CONVERSA);
    }

    @Test
    @DisplayName("pedido explicito com prefixo casual continua funcional")
    void pedidoExplicitoComPrefixoCasualContinuaFuncional() {
        assertThat(detector.detect("Valeu, me manda um treino de costas"))
                .contains(IntentType.TREINO);
        assertThat(detector.detect("Show, fiz treino hoje"))
                .contains(IntentType.TREINO_FEITO);
        assertThat(detector.detect("Beleza, quero ver meu progresso"))
                .contains(IntentType.PROGRESSO);
        assertThat(detector.detect("Obrigado, me ajuda com o bot"))
                .contains(IntentType.AJUDA);
    }

    @Test
    @DisplayName("detecta conversa por suplemento")
    void detectaConversaPorSuplemento() {
        assertThat(detector.detect("qual suplemento tomar")).contains(IntentType.CONVERSA);
    }

    @Test
    @DisplayName("detecta cancelar por 'parar' e 'sair'")
    void detectaCancelarPararESair() {
        assertThat(detector.detect("parar")).contains(IntentType.CANCELAR);
        assertThat(detector.detect("sair")).contains(IntentType.CANCELAR);
    }
}


