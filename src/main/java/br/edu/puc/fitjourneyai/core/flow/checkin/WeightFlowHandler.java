package br.edu.puc.fitjourneyai.core.flow.checkin;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.MeasurementType;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Fluxo 3a — Check-in Corporal: Registro rápido de peso.
 * <p>
 * Gatilhos: comando /peso, número isolado (ex: "72.5"), ou texto com "peso".
 * <p>
 * Diferenças do MVP:
 * <ul>
 *   <li>Persiste como {@link Measurement} na tabela measurements (o MVP só atualizava peso_atual no User)</li>
 *   <li>Também atualiza User.pesoAtual para manter compatibilidade</li>
 *   <li>Compara com última Measurement do tipo PESO (não com campo do User)</li>
 * </ul>
 * <p>
 * Conforme Fig.8 do Pacote Consolidado: extração de valor, validação de faixa,
 * persistência com verificação de sucesso e próxima ação sugerida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeightFlowHandler implements FlowHandler {

    private static final int STEP_AWAITING_WEIGHT = 1;

    private final MeasurementRepository measurementRepository;
    private final UserRepository userRepository;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.WEIGHT_CHECKIN;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        // Pré-checagem: onboarding concluído?
        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Antes de registrar seu peso, preciso te conhecer melhor!\n\nUse /start para fazer o cadastro.",
                    "Use /start para iniciar o cadastro."
            );
        }

        Integer step = context.state().getCurrentStep();

        // Se está entrando no fluxo agora (sem step ativo), tenta extrair peso direto do texto
        if (step == null || !context.state().hasActiveFlow()) {
            return tryDirectExtraction(context);
        }

        // Está no step de aguardar peso
        if (step == STEP_AWAITING_WEIGHT) {
            return processWeight(context);
        }

        // Fallback: pede o peso
        return askForWeight();
    }

    /**
     * Tenta extrair o peso diretamente da mensagem que acionou o fluxo.
     * Ex: usuário mandou "72.5" ou "/peso" (sem valor).
     */
    private FlowResult tryDirectExtraction(FlowContext context) {
        String text = context.rawText();

        // Se é só o comando /peso sem valor, pede o peso
        if (text != null && text.trim().equalsIgnoreCase("/peso")) {
            return askForWeight();
        }

        // Tenta extrair número da mensagem
        Double peso = parseWeight(text);
        if (peso != null) {
            return saveAndRespond(context.user(), peso);
        }

        // Não conseguiu extrair — pede explicitamente
        return askForWeight();
    }

    /**
     * Processa a resposta do usuário quando estamos esperando o peso.
     */
    private FlowResult processWeight(FlowContext context) {
        Double peso = parseWeight(context.rawText());

        if (peso == null) {
            return FlowResult.text(
                    """
                    Não consegui entender esse peso.

                    Me manda algo como:
                    - 72
                    - 72.5
                    - 72,5
                    - 72kg""",
                    ConversationFlowType.WEIGHT_CHECKIN,
                    STEP_AWAITING_WEIGHT,
                    Map.of(),
                    null
            );
        }

        return saveAndRespond(context.user(), peso);
    }

    /**
     * Persiste o peso como Measurement, atualiza User.pesoAtual e monta resposta com diff.
     */
    private FlowResult saveAndRespond(User user, double novoPeso) {
        LocalDateTime now = LocalDateTime.now();

        // Busca último registro de peso para comparação
        Optional<Measurement> ultimoRegistro = measurementRepository
                .findTopByUserAndTipoOrderByDataRegistroDesc(user, MeasurementType.PESO);

        // Persiste como Measurement
        Measurement measurement = Measurement.builder()
                .user(user)
                .tipo(MeasurementType.PESO)
                .valor(novoPeso)
                .dataRegistro(now)
                .build();

        try {
            measurementRepository.save(measurement);
        } catch (Exception e) {
            log.error("Erro ao persistir peso para user={}: {}", user.getId(), e.getMessage(), e);
            return FlowResult.done(
                    "Tive um problema ao salvar seu peso. Tenta de novo com /peso.",
                    "Use /peso para tentar novamente."
            );
        }

        // Atualiza peso_atual no User (mantém compatibilidade)
        user.setPesoAtual(novoPeso);
        userRepository.save(user);

        // Monta mensagem de comparação
        String diffMsg = buildDiffMessage(ultimoRegistro, novoPeso);

        log.info("Peso registrado: user={}, peso={}, diff='{}'", user.getId(), novoPeso, diffMsg);

        String dataFormatada = now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));

        return FlowResult.done(
                String.format("""
                        Peso registrado: %.1f kg \u2705
                        \uD83D\uDCC5 %s
                        
                        %s""", novoPeso, dataFormatada, diffMsg),
                "Use /progresso para ver sua evolução ou /registro para medidas completas!"
        );
    }

    private FlowResult askForWeight() {
        return FlowResult.text(
                """
                \u2696\uFE0F Bora registrar seu peso!
                
                Me manda seu peso em kg.
                Ex: 72 ou 72.5 ou 72,5""",
                ConversationFlowType.WEIGHT_CHECKIN,
                STEP_AWAITING_WEIGHT,
                Map.of(),
                null
        );
    }

    // ========================================================================
    // UTILITÁRIOS
    // ========================================================================

    private String buildDiffMessage(Optional<Measurement> ultimoRegistro, double novoPeso) {
        if (ultimoRegistro.isEmpty()) {
            return "\uD83C\uDF1F Esse é o seu primeiro registro de peso! A partir de agora vou acompanhar sua evolução.";
        }

        double pesoAnterior = ultimoRegistro.get().getValor();
        double diff = novoPeso - pesoAnterior;

        if (Math.abs(diff) < 0.01) {
            return "\u27A1\uFE0F Peso estável em relação ao último registro. Consistência é tudo!";
        } else if (diff > 0) {
            return String.format("\u2B06\uFE0F +%.1f kg desde o último registro.", diff);
        } else {
            return String.format("\u2B07\uFE0F -%.1f kg desde o último registro. Mandou bem! \uD83D\uDD25", Math.abs(diff));
        }
    }

    /**
     * Extrai peso de texto livre. Aceita: "72", "72.5", "72,5", "72kg", "72.5 kg".
     * Faixa válida: 20–350 kg.
     */
    private Double parseWeight(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim()
                    .toLowerCase()
                    .replace("kg", "")
                    .replace(",", ".")
                    .trim();
            // Remove tudo que não é número ou ponto
            cleaned = cleaned.replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return null;

            double value = Double.parseDouble(cleaned);
            return (value >= 20 && value <= 350) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
