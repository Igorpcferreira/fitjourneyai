package br.edu.puc.fitjourneyai.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TextSanitizer")
class TextSanitizerTest {

    @Test
    @DisplayName("Deve retornar null quando input é null")
    void deveRetornarNullParaNull() {
        assertThat(TextSanitizer.stripHtmlTags(null)).isNull();
    }

    @Test
    @DisplayName("Deve retornar string vazia para input vazio")
    void deveRetornarVazioParaVazio() {
        assertThat(TextSanitizer.stripHtmlTags("")).isEmpty();
    }

    @Test
    @DisplayName("Deve manter texto sem tags HTML intacto")
    void deveManterTextoSemTags() {
        assertThat(TextSanitizer.stripHtmlTags("Olá mundo")).isEqualTo("Olá mundo");
    }

    @Test
    @DisplayName("Deve remover tag <b>")
    void deveRemoverTagBold() {
        assertThat(TextSanitizer.stripHtmlTags("<b>negrito</b>")).isEqualTo("negrito");
    }

    @Test
    @DisplayName("Deve remover múltiplas tags")
    void deveRemoverMultiplasTags() {
        assertThat(TextSanitizer.stripHtmlTags("<b>Título</b> e <i>itálico</i>"))
                .isEqualTo("Título e itálico");
    }

    @Test
    @DisplayName("Deve remover tag com atributos")
    void deveRemoverTagComAtributos() {
        assertThat(TextSanitizer.stripHtmlTags("<a href=\"url\">link</a>")).isEqualTo("link");
    }

    @Test
    @DisplayName("Deve preservar texto entre tags")
    void devePreservarTextoEntreTags() {
        assertThat(TextSanitizer.stripHtmlTags("<code>print('hello')</code>"))
                .isEqualTo("print('hello')");
    }

    @ParameterizedTest(name = "input={0} → esperado={1}")
    @CsvSource({
            "'<b>bold</b>', bold",
            "'<i>italic</i>', italic",
            "'sem tag', sem tag",
            "'<br/>', ''",
            "'A<b>B</b>C', ABC"
    })
    @DisplayName("Deve strip HTML em vários cenários")
    void deveStripHtmlEmVariosCenarios(String input, String expected) {
        assertThat(TextSanitizer.stripHtmlTags(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Deve lidar com tag incompleta (sem fechamento)")
    void deveLidarComTagIncompleta() {
        // tudo após < vira tag, sem crash
        String result = TextSanitizer.stripHtmlTags("texto<sem fechar");
        assertThat(result).isEqualTo("texto");
    }

    @Test
    @DisplayName("Deve lidar com > isolado (sem abertura) — inTag=false, char é emitido")
    void deveLidarComFechamentoIsolado() {
        // '>' sem '<' anterior: inTag já é false, então '>' é ignorado (consumido)
        String result = TextSanitizer.stripHtmlTags("texto>mais");
        assertThat(result).isEqualTo("textomais");
    }
}

