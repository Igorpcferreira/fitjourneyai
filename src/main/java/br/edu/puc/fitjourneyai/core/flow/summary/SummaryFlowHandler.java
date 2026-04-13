package br.edu.puc.fitjourneyai.core.flow.summary;

import br.edu.puc.fitjourneyai.core.ai.AiService;
import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.MeasurementType;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fluxo 7 — Resumos Inteligentes.
 * <p>
 * Conforme Fig.12 do Pacote Consolidado:
 * Síntese de período com agregação determinística, interpretação de padrões
 * por IA, recomendação prática e fallback factual mínimo.
 * <p>
 * Dois modos:
 * <ul>
 *   <li>/resumo → resumo da última semana (padrão)</li>
 *   <li>/resumo mensal ou "resumo do mês" → resumo dos últimos 30 dias</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryFlowHandler implements FlowHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

    private final MeasurementRepository measurementRepository;
    private final WorkoutRepository workoutRepository;
    private final AiService aiService;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.SUMMARY;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Antes de ver seu resumo, preciso te conhecer! Use /start pra fazer o cadastro \uD83D\uDE09",
                    "Use /start para iniciar o cadastro."
            );
        }

        // Detecta período: "mensal" / "mês" → 30 dias, senão 7 dias
        String text = context.rawText() != null ? context.rawText().toLowerCase() : "";
        boolean mensal = text.contains("mensal") || text.contains("mês") || text.contains("mes")
                || text.contains("30 dias") || text.contains("último mês");
        int dias = mensal ? 30 : 7;
        String labelPeriodo = mensal ? "últimos 30 dias" : "última semana";

        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime inicio = fim.minusDays(dias);
        String nome = user.getNome() != null ? user.getNome() : "amigo(a)";

        // ==================== COLETA DE DADOS ====================

        List<Measurement> pesoData = measurementRepository
                .findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(
                        user, MeasurementType.PESO, inicio, fim);

        List<Workout> treinos = workoutRepository
                .findByUserAndDataRealizacaoBetween(user, inicio, fim);

        if (pesoData.isEmpty() && treinos.isEmpty()) {
            return FlowResult.done(
                    String.format("""
                            %s, não encontrei registros nos %s \uD83D\uDE14
                            
                            Registra seu peso com /peso e seus treinos com /treino_feito que na próxima vez monto um resumo completo pra você! \uD83D\uDE80""",
                            nome, labelPeriodo),
                    "Use /peso ou /treino_feito para começar a registrar."
            );
        }

        // ==================== INDICADORES DETERMINÍSTICOS ====================

        Map<String, Object> indicators = new LinkedHashMap<>();
        indicators.put("periodo", labelPeriodo);
        indicators.put("dataInicio", inicio.toLocalDate().format(DATE_FMT));
        indicators.put("dataFim", fim.toLocalDate().format(DATE_FMT));

        StringBuilder factual = new StringBuilder();
        factual.append(String.format("\uD83D\uDCCB Resumo — %s (%s a %s)\n\n",
                labelPeriodo,
                inicio.toLocalDate().format(DATE_FMT),
                fim.toLocalDate().format(DATE_FMT)));

        // ---- Peso ----
        if (!pesoData.isEmpty()) {
            double pesoAtual = pesoData.get(pesoData.size() - 1).getValor();
            double pesoInicial = pesoData.get(0).getValor();
            double variacao = pesoAtual - pesoInicial;
            String varSign = variacao >= 0 ? "+" : "";
            String varIcon = variacao < -0.1 ? "\u2B07\uFE0F" : variacao > 0.1 ? "\u2B06\uFE0F" : "\u27A1\uFE0F";

            factual.append(String.format("\u2696\uFE0F Peso: %.1f kg → %.1f kg (%s %s%.1f kg)\n",
                    pesoInicial, pesoAtual, varIcon, varSign, variacao));
            factual.append(String.format("   %d registros no período\n\n", pesoData.size()));

            indicators.put("pesoInicial", pesoInicial);
            indicators.put("pesoAtual", pesoAtual);
            indicators.put("variacaoPeso", variacao);
            indicators.put("registrosPeso", pesoData.size());
        }

        // ---- Treinos ----
        if (!treinos.isEmpty()) {
            int totalTreinos = treinos.size();
            int semanas = Math.max(1, dias / 7);
            double mediaPerWeek = (double) totalTreinos / semanas;

            int totalMinutos = treinos.stream()
                    .filter(w -> w.getDuracaoMinutos() != null)
                    .mapToInt(Workout::getDuracaoMinutos)
                    .sum();

            OptionalDouble mediaIntensidade = treinos.stream()
                    .filter(w -> w.getIntensidadePercebida() != null)
                    .mapToInt(Workout::getIntensidadePercebida)
                    .average();

            // Distribuição por grupo
            Map<String, Long> dist = treinos.stream()
                    .filter(w -> w.getGrupoMuscular() != null)
                    .collect(Collectors.groupingBy(w -> w.getGrupoMuscular().name(), Collectors.counting()));

            // Treino mais recente
            Workout ultimo = treinos.get(treinos.size() - 1);
            String ultimoLabel = ultimo.getDescricaoTreino() != null ? ultimo.getDescricaoTreino() : "treino";
            String ultimaData = ultimo.getDataRealizacao() != null
                    ? ultimo.getDataRealizacao().toLocalDate().format(DATE_FMT) : "-";

            factual.append(String.format("\uD83C\uDFCB\uFE0F Treinos: %d no período (%.1f/semana)\n", totalTreinos, mediaPerWeek));
            factual.append(String.format("   Tempo total: %s\n", formatMinutes(totalMinutos)));

            if (mediaIntensidade.isPresent()) {
                factual.append(String.format("   Intensidade média: %.1f/10\n", mediaIntensidade.getAsDouble()));
            }

            factual.append(String.format("   Último treino: %s em %s\n", ultimoLabel, ultimaData));

            if (!dist.isEmpty()) {
                String topGrupo = dist.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(e -> e.getKey().toLowerCase() + " (" + e.getValue() + "x)")
                        .orElse("-");
                factual.append(String.format("   Mais treinado: %s\n", topGrupo));
            }

            factual.append("\n");

            indicators.put("totalTreinos", totalTreinos);
            indicators.put("mediaPerWeek", mediaPerWeek);
            indicators.put("totalMinutos", totalMinutos);
            indicators.put("distribuicao", dist);
            indicators.put("ultimoTreino", ultimoLabel + " em " + ultimaData);
            mediaIntensidade.ifPresent(v -> indicators.put("intensidadeMedia", v));
        }

        // ---- Consistência vs meta ----
        Integer meta = user.getFrequenciaTreinoEstimada();
        if (meta != null && meta > 0 && !treinos.isEmpty()) {
            int semanas = Math.max(1, dias / 7);
            double mediaReal = (double) treinos.size() / semanas;
            double percentual = (mediaReal / meta) * 100;

            String emoji = percentual >= 90 ? "\uD83C\uDF1F" : percentual >= 60 ? "\uD83D\uDCAA" : "\u23F0";
            factual.append(String.format("%s Meta semanal: %dx | Real: %.1fx (%.0f%%)\n\n",
                    emoji, meta, mediaReal, percentual));

            indicators.put("metaSemanal", meta);
            indicators.put("percentualMeta", percentual);
        }

        // ==================== INTERPRETAÇÃO POR IA ====================

        String aiAnalysis = generateAiSummary(user, indicators);

        String fullResponse = factual.toString();
        if (aiAnalysis != null && !aiAnalysis.isBlank()) {
            fullResponse += "\uD83E\uDD16 Análise do FitJourneyAI:\n\n" + aiAnalysis;
        }

        return FlowResult.done(
                fullResponse,
                "Use /progresso pra ver gráficos ou /treino pra pedir um treino novo!"
        );
    }

    // ========================================================================
    // IA
    // ========================================================================

    private String generateAiSummary(User user, Map<String, Object> indicators) {
        try {
            return aiService.generateSummary(user, indicators);
        } catch (Exception e) {
            log.warn("Fallback: IA indisponível para resumo, user={}", user.getId());
            return buildFallbackAnalysis(user, indicators);
        }
    }

    private String buildFallbackAnalysis(User user, Map<String, Object> indicators) {
        StringBuilder fb = new StringBuilder();

        if (indicators.containsKey("percentualMeta")) {
            double pct = (double) indicators.get("percentualMeta");
            if (pct >= 90) fb.append("Consistência excelente! Continue assim.\n");
            else if (pct >= 60) fb.append("Bom ritmo, você está no caminho certo!\n");
            else fb.append("Tem espaço pra mais treinos — que tal encaixar mais um dia?\n");
        }

        if (indicators.containsKey("variacaoPeso")) {
            double var = (double) indicators.get("variacaoPeso");
            String obj = user.getObjetivo() != null ? user.getObjetivo().name() : "";
            if (obj.equals("EMAGRECER") && var < -0.3) {
                fb.append("Peso descendo — coerente com seu objetivo!\n");
            } else if (obj.equals("GANHAR_MUSCULO") && var > 0.3) {
                fb.append("Peso subindo — pode ser ganho de massa!\n");
            }
        }

        return fb.isEmpty() ? "Continue registrando pra eu te dar insights cada vez melhores!" : fb.toString();
    }

    private String formatMinutes(int totalMinutos) {
        if (totalMinutos < 60) return totalMinutos + " min";
        int hours = totalMinutos / 60;
        int mins = totalMinutos % 60;
        return hours + "h" + (mins > 0 ? mins + "min" : "");
    }
}
