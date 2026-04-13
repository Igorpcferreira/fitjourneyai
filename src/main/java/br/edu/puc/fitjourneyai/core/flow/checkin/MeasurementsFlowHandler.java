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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Fluxo 3b — Check-in Corporal: Registro guiado de medidas completas.
 * <p>
 * Gatilhos: comando /registro ou /medidas.
 * <p>
 * Conduz 5 passos de coleta + confirmação:
 * <ol>
 *   <li>Peso (kg)</li>
 *   <li>Cintura (cm)</li>
 *   <li>Quadril (cm)</li>
 *   <li>Peito (cm)</li>
 *   <li>Braço (cm)</li>
 *   <li>Confirmação</li>
 * </ol>
 * <p>
 * Diferenças do MVP:
 * <ul>
 *   <li>Dados parciais em ConversationState.partialData (JSONB), não em ConcurrentHashMap</li>
 *   <li>Cada medida vira um Measurement separado no banco</li>
 *   <li>Cada step permite "pular" para medidas opcionais</li>
 *   <li>Confirmação final antes de persistir</li>
 *   <li>Compara com últimas medidas do mesmo tipo</li>
 * </ul>
 * <p>
 * Conforme Fig.8: fluxo guiado, validação de faixas, persistência com verificação
 * de sucesso e próxima ação sugerida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeasurementsFlowHandler implements FlowHandler {

    private static final int STEP_PESO = 1;
    private static final int STEP_CINTURA = 2;
    private static final int STEP_QUADRIL = 3;
    private static final int STEP_PEITO = 4;
    private static final int STEP_BRACO = 5;
    private static final int STEP_CONFIRMACAO = 6;

    private final MeasurementRepository measurementRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.MEASUREMENTS_CHECKIN;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        // Pré-checagem: onboarding concluído?
        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Antes de registrar medidas, preciso te conhecer melhor!\n\nUse /start para fazer o cadastro.",
                    "Use /start para iniciar o cadastro."
            );
        }

        Integer step = context.state().getCurrentStep();

        // Primeira entrada → inicia o fluxo guiado
        if (step == null || !context.state().hasActiveFlow()) {
            return startFlow();
        }

        Map<String, String> partial = loadPartialData(context.state().getPartialData());

        return switch (step) {
            case STEP_PESO -> handlePeso(context, partial);
            case STEP_CINTURA -> handleCintura(context, partial);
            case STEP_QUADRIL -> handleQuadril(context, partial);
            case STEP_PEITO -> handlePeito(context, partial);
            case STEP_BRACO -> handleBraco(context, partial);
            case STEP_CONFIRMACAO -> handleConfirmacao(context, partial);
            default -> startFlow();
        };
    }

    // ========================================================================
    // STEP HANDLERS
    // ========================================================================

    private FlowResult startFlow() {
        return FlowResult.text(
                """
                Vamos registrar suas medidas corporais!

                Primeiro, me envie seu peso em kg.
                Exemplo: 72.5""",
                ConversationFlowType.MEASUREMENTS_CHECKIN,
                STEP_PESO,
                Map.of(),
                null
        );
    }

    private FlowResult handlePeso(FlowContext context, Map<String, String> partial) {
        Double peso = parseWeight(context.rawText());
        if (peso == null) {
            return stayOnStep(STEP_PESO, partial,
                    "Não consegui entender esse peso.\n\nMe manda algo como: 72 ou 72.5");
        }

        partial.put("peso", peso.toString());
        return FlowResult.text(
                String.format("""
                        Peso: %.1f kg

                        Agora, me envie a medida da CINTURA em centímetros.
                        (Digite "pular" para pular)
                        Exemplo: 82""", peso),
                ConversationFlowType.MEASUREMENTS_CHECKIN,
                STEP_CINTURA,
                partial,
                null
        );
    }

    private FlowResult handleCintura(FlowContext context, Map<String, String> partial) {
        if (isSkip(context.normalizedText())) {
            return advanceToQuadril(partial);
        }

        Double cintura = parseMeasurement(context.rawText());
        if (cintura == null) {
            return stayOnStep(STEP_CINTURA, partial,
                    "Não entendi essa medida de cintura.\n\nMe manda um valor em cm (ex: 82) ou \"pular\".");
        }

        partial.put("cintura", cintura.toString());
        return advanceToQuadril(partial);
    }

    private FlowResult advanceToQuadril(Map<String, String> partial) {
        String cinturaTxt = partial.containsKey("cintura")
                ? String.format("Cintura: %.1f cm\n\n", Double.parseDouble(partial.get("cintura")))
                : "";

        return FlowResult.text(
                cinturaTxt + """
                        Me envie a medida do QUADRIL em centímetros.
                        (Digite "pular" para pular)
                        Exemplo: 98""",
                ConversationFlowType.MEASUREMENTS_CHECKIN,
                STEP_QUADRIL,
                partial,
                null
        );
    }

    private FlowResult handleQuadril(FlowContext context, Map<String, String> partial) {
        if (isSkip(context.normalizedText())) {
            return advanceToPeito(partial);
        }

        Double quadril = parseMeasurement(context.rawText());
        if (quadril == null) {
            return stayOnStep(STEP_QUADRIL, partial,
                    "Não entendi essa medida de quadril.\n\nMe manda um valor em cm (ex: 98) ou \"pular\".");
        }

        partial.put("quadril", quadril.toString());
        return advanceToPeito(partial);
    }

    private FlowResult advanceToPeito(Map<String, String> partial) {
        String quadrilTxt = partial.containsKey("quadril")
                ? String.format("Quadril: %.1f cm\n\n", Double.parseDouble(partial.get("quadril")))
                : "";

        return FlowResult.text(
                quadrilTxt + """
                        Me envie a medida do PEITO em centímetros.
                        (Digite "pular" para pular)
                        Exemplo: 100""",
                ConversationFlowType.MEASUREMENTS_CHECKIN,
                STEP_PEITO,
                partial,
                null
        );
    }

    private FlowResult handlePeito(FlowContext context, Map<String, String> partial) {
        if (isSkip(context.normalizedText())) {
            return advanceToBraco(partial);
        }

        Double peito = parseMeasurement(context.rawText());
        if (peito == null) {
            return stayOnStep(STEP_PEITO, partial,
                    "Não entendi essa medida de peito.\n\nMe manda um valor em cm (ex: 100) ou \"pular\".");
        }

        partial.put("peito", peito.toString());
        return advanceToBraco(partial);
    }

    private FlowResult advanceToBraco(Map<String, String> partial) {
        String peitoTxt = partial.containsKey("peito")
                ? String.format("Peito: %.1f cm\n\n", Double.parseDouble(partial.get("peito")))
                : "";

        return FlowResult.text(
                peitoTxt + """
                        Me envie a medida do BRAÇO (em repouso) em centímetros.
                        (Digite "pular" para pular)
                        Exemplo: 35""",
                ConversationFlowType.MEASUREMENTS_CHECKIN,
                STEP_BRACO,
                partial,
                null
        );
    }

    private FlowResult handleBraco(FlowContext context, Map<String, String> partial) {
        if (!isSkip(context.normalizedText())) {
            Double braco = parseMeasurement(context.rawText());
            if (braco == null) {
                return stayOnStep(STEP_BRACO, partial,
                        "Não entendi essa medida de braço.\n\nMe manda um valor em cm (ex: 35) ou \"pular\".");
            }
            partial.put("braco", braco.toString());
        }

        // Monta resumo para confirmação
        String resumo = buildResumo(partial);

        return FlowResult.text(
                resumo + "\n\nPosso salvar essas medidas? (responda \"sim\" ou \"não\")",
                ConversationFlowType.MEASUREMENTS_CHECKIN,
                STEP_CONFIRMACAO,
                partial,
                null
        );
    }

    private FlowResult handleConfirmacao(FlowContext context, Map<String, String> partial) {
        String lower = context.normalizedText();

        if (lower.startsWith("sim") || lower.equals("s") || lower.equals("ok")
                || lower.equals("salvar") || lower.equals("confirmar")) {
            return persistAllMeasurements(context.user(), partial);
        }

        if (lower.startsWith("nao") || lower.startsWith("não") || lower.equals("n")
                || lower.equals("refazer")) {
            return FlowResult.text(
                    "Sem problema! Vamos refazer.\n\nMe envie seu peso em kg.\nExemplo: 72.5",
                    ConversationFlowType.MEASUREMENTS_CHECKIN,
                    STEP_PESO,
                    Map.of(),
                    null
            );
        }

        return stayOnStep(STEP_CONFIRMACAO, partial,
                "Responda \"sim\" para salvar ou \"não\" para refazer.");
    }

    // ========================================================================
    // PERSISTÊNCIA
    // ========================================================================

    /**
     * Persiste todas as medidas coletadas como Measurements individuais.
     */
    private FlowResult persistAllMeasurements(User user, Map<String, String> partial) {
        LocalDateTime now = LocalDateTime.now();
        List<String> saved = new ArrayList<>();
        StringBuilder diffReport = new StringBuilder();

        try {
            // Peso (obrigatório)
            if (partial.containsKey("peso")) {
                double peso = Double.parseDouble(partial.get("peso"));
                saveMeasurement(user, MeasurementType.PESO, peso, now, diffReport);
                user.setPesoAtual(peso);
                userRepository.save(user);
                saved.add(String.format("Peso: %.1f kg", peso));
            }

            // Medidas opcionais
            saveMeasurementIfPresent(user, partial, "cintura", MeasurementType.CINTURA, "Cintura", "cm", now, saved, diffReport);
            saveMeasurementIfPresent(user, partial, "quadril", MeasurementType.QUADRIL, "Quadril", "cm", now, saved, diffReport);
            saveMeasurementIfPresent(user, partial, "peito", MeasurementType.PEITO, "Peito", "cm", now, saved, diffReport);
            saveMeasurementIfPresent(user, partial, "braco", MeasurementType.BRACO, "Braço", "cm", now, saved, diffReport);

        } catch (Exception e) {
            log.error("Erro ao persistir medidas para user={}: {}", user.getId(), e.getMessage(), e);
            return FlowResult.done(
                    "Tive um problema ao salvar suas medidas. Tenta de novo com /registro.",
                    "Use /registro para tentar novamente."
            );
        }

        log.info("Medidas registradas: user={}, items={}", user.getId(), saved.size());

        String savedList = String.join("\n", saved);
        String diffText = diffReport.length() > 0
                ? "\n\nComparação com último registro:\n" + diffReport
                : "";

        String dataFormatada = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));

        return FlowResult.done(
                String.format("""
                        Medidas registradas com sucesso! \u2705
                        \uD83D\uDCC5 %s
                        
                        %s%s""", dataFormatada, savedList, diffText),
                "Use /progresso para ver sua evolução ou /treino para pedir um treino!"
        );
    }

    private void saveMeasurement(User user, MeasurementType tipo, double valor,
                                 LocalDateTime timestamp, StringBuilder diffReport) {
        // Busca anterior para comparação
        Optional<Measurement> anterior = measurementRepository
                .findTopByUserAndTipoOrderByDataRegistroDesc(user, tipo);

        // Persiste novo
        Measurement m = Measurement.builder()
                .user(user)
                .tipo(tipo)
                .valor(valor)
                .dataRegistro(timestamp)
                .build();
        measurementRepository.save(m);

        // Calcula diff
        if (anterior.isPresent()) {
            double diff = valor - anterior.get().getValor();
            if (Math.abs(diff) >= 0.1) {
                String unit = tipo == MeasurementType.PESO ? "kg" : "cm";
                String direction = diff > 0 ? "+" : "";
                diffReport.append(String.format("  %s: %s%.1f %s\n",
                        tipo.name().toLowerCase(), direction, diff, unit));
            }
        }
    }

    private void saveMeasurementIfPresent(User user, Map<String, String> partial,
                                          String key, MeasurementType tipo, String label,
                                          String unit, LocalDateTime timestamp,
                                          List<String> saved, StringBuilder diffReport) {
        if (partial.containsKey(key)) {
            double valor = Double.parseDouble(partial.get(key));
            saveMeasurement(user, tipo, valor, timestamp, diffReport);
            saved.add(String.format("%s: %.1f %s", label, valor, unit));
        }
    }

    // ========================================================================
    // UTILITÁRIOS
    // ========================================================================

    private String buildResumo(Map<String, String> partial) {
        StringBuilder sb = new StringBuilder("Suas medidas:\n\n");

        if (partial.containsKey("peso"))
            sb.append(String.format("Peso: %.1f kg\n", Double.parseDouble(partial.get("peso"))));
        if (partial.containsKey("cintura"))
            sb.append(String.format("Cintura: %.1f cm\n", Double.parseDouble(partial.get("cintura"))));
        if (partial.containsKey("quadril"))
            sb.append(String.format("Quadril: %.1f cm\n", Double.parseDouble(partial.get("quadril"))));
        if (partial.containsKey("peito"))
            sb.append(String.format("Peito: %.1f cm\n", Double.parseDouble(partial.get("peito"))));
        if (partial.containsKey("braco"))
            sb.append(String.format("Braço: %.1f cm\n", Double.parseDouble(partial.get("braco"))));

        return sb.toString().trim();
    }

    private FlowResult stayOnStep(int step, Map<String, String> partial, String message) {
        return FlowResult.text(message,
                ConversationFlowType.MEASUREMENTS_CHECKIN, step, partial, null);
    }

    private boolean isSkip(String text) {
        return text != null && (text.equals("pular") || text.equals("pula")
                || text.equals("skip") || text.equals("p"));
    }

    private Double parseWeight(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim().toLowerCase()
                    .replace("kg", "").replace(",", ".").trim()
                    .replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return null;
            double value = Double.parseDouble(cleaned);
            return (value >= 20 && value <= 350) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseMeasurement(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.trim().toLowerCase()
                    .replace("cm", "").replace(",", ".").trim()
                    .replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return null;
            double value = Double.parseDouble(cleaned);
            return (value >= 10 && value <= 300) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> loadPartialData(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Erro ao deserializar partialData: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
