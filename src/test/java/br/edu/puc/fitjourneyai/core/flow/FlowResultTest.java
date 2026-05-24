package br.edu.puc.fitjourneyai.core.flow;

import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowResultTest {

    @Test
    @DisplayName("Deve considerar imageData por conteudo em equals/hashCode")
    void deveCompararImageDataPorConteudo() {
        FlowResult a = new FlowResult("ok", new byte[]{1, 2, 3}, "cap",
                ConversationFlowType.NONE, null, Map.of("k", "v"), "next");
        FlowResult b = new FlowResult("ok", new byte[]{1, 2, 3}, "cap",
                ConversationFlowType.NONE, null, Map.of("k", "v"), "next");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Deve diferenciar quando imageData muda")
    void deveDiferenciarQuandoImageDataMuda() {
        FlowResult a = new FlowResult("ok", new byte[]{1, 2, 3}, "cap",
                ConversationFlowType.NONE, null, Map.of(), "next");
        FlowResult b = new FlowResult("ok", new byte[]{1, 2, 4}, "cap",
                ConversationFlowType.NONE, null, Map.of(), "next");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("toString deve incluir conteudo do imageData")
    void toStringDeveIncluirConteudoDoArray() {
        FlowResult result = new FlowResult("ok", new byte[]{1, 2, 3}, "cap",
                ConversationFlowType.NONE, null, Map.of(), "next");

        assertThat(result.toString()).contains("imageData=[1, 2, 3]");
    }
}

