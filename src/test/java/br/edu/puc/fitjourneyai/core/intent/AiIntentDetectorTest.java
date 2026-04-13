package br.edu.puc.fitjourneyai.core.intent;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiIntentDetectorTest {

    @Mock private AiService aiService;
    @InjectMocks private AiIntentDetector detector;

    @Test
    @DisplayName("Deve ter prioridade 20")
    void deveTerPrioridade20() {
        assertThat(detector.priority()).isEqualTo(20);
    }

    @Test
    @DisplayName("Deve retornar vazio para texto null")
    void deveRetornarVazioParaNull() {
        assertThat(detector.detect(null)).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio para texto curto")
    void deveRetornarVazioParaTextoCurto() {
        assertThat(detector.detect("oi")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio para comandos (ja tratados)")
    void deveIgnorarComandos() {
        assertThat(detector.detect("/treino")).isEmpty();
        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Deve classificar via IA quando texto e ambiguo")
    void deveClassificarViaIA() {
        when(aiService.classifyIntent(any(), any())).thenReturn(IntentType.TREINO);
        Optional<IntentType> result = detector.detect("me manda um treino top");
        assertThat(result).contains(IntentType.TREINO);
    }

    @Test
    @DisplayName("Deve retornar vazio quando IA retorna UNKNOWN")
    void deveRetornarVazioQuandoIARetornaUnknown() {
        when(aiService.classifyIntent(any(), any())).thenReturn(IntentType.UNKNOWN);
        assertThat(detector.detect("blablabla")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando IA falha")
    void deveRetornarVazioQuandoIAFalha() {
        when(aiService.classifyIntent(any(), any())).thenThrow(new RuntimeException("API down"));
        assertThat(detector.detect("algo")).isEmpty();
    }
}
