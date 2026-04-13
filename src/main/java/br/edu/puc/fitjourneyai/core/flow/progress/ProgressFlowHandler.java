package br.edu.puc.fitjourneyai.core.flow.progress;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.Measurement;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.entity.Workout;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.MeasurementType;
import br.edu.puc.fitjourneyai.core.port.MeasurementRepository;
import br.edu.puc.fitjourneyai.core.port.MessageGateway;
import br.edu.puc.fitjourneyai.core.port.WorkoutRepository;
import br.edu.puc.fitjourneyai.infrastructure.chart.ProgressChartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fluxo 6 — Evolução e Leitura de Progresso.
 * <p>
 * Gatilhos: comando /progresso ou mensagens como "me mostra minha evolução".
 * <p>
 * Envia ao usuário:
 * <ol>
 *   <li>Álbum de gráficos (Telegram sendMediaGroup) com até 3 charts premium</li>
 *   <li>Análise textual rica formatada em HTML com indicadores calculados</li>
 *   <li>Próxima ação sugerida</li>
 * </ol>
 * <p>
 * Os gráficos são gerados via {@link ProgressChartService} com design dark-mode moderno.
 * O envio é feito diretamente pelo handler via {@link MessageGateway} (não pelo FlowResult),
 * porque o FlowResult suporta apenas 1 imagem, e aqui enviamos um álbum.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressFlowHandler implements FlowHandler {

    private static final int PERIOD_DAYS = 30;

    private final MeasurementRepository measurementRepository;
    private final WorkoutRepository workoutRepository;
    private final ProgressChartService chartService;
    private final MessageGateway messageGateway;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.PROGRESS;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();

        if (!user.isOnboardingConcluido()) {
            return FlowResult.done(
                    "Antes de ver seu progresso, preciso te conhecer melhor!\n\nUse /start para fazer o cadastro.",
                    "Use /start para iniciar o cadastro."
            );
        }

        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime inicio = fim.minusDays(PERIOD_DAYS);
        String nome = user.getNome() != null ? user.getNome() : "Usuário";

        // ==================== COLETA DE DADOS ====================

        List<Measurement> pesoData = measurementRepository
                .findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(
                        user, MeasurementType.PESO, inicio, fim);

        List<Workout> treinos = workoutRepository
                .findByUserAndDataRealizacaoBetween(user, inicio, fim);

        // Sem dados suficientes?
        if (pesoData.isEmpty() && treinos.isEmpty()) {
            return FlowResult.done(
                    "Ainda não tenho dados suficientes pra montar seu painel de progresso \uD83D\uDCCA\n\n" +
                    "Registra seu peso com /peso e seus treinos com /treino_feito que eu monto tudo pra você! \uD83D\uDE80",
                    "Use /peso para registrar seu peso ou /treino_feito para registrar um treino."
            );
        }

        // ==================== GERAÇÃO DE GRÁFICOS ====================

        List<byte[]> charts = new ArrayList<>();

        // Gráfico 1: Evolução de peso
        if (pesoData.size() >= 2) {
            Map<LocalDate, Double> weightMap = new LinkedHashMap<>();
            pesoData.forEach(m -> weightMap.put(m.getDataRegistro().toLocalDate(), m.getValor()));

            byte[] weightChart = chartService.generateWeightChart(weightMap, nome);
            if (weightChart != null) charts.add(weightChart);
        }

        // Gráfico 2: Frequência de treinos por semana
        if (!treinos.isEmpty()) {
            Map<String, Integer> weeklyFreq = buildWeeklyFrequency(treinos);
            byte[] freqChart = chartService.generateTrainingFrequencyChart(weeklyFreq, nome);
            if (freqChart != null) charts.add(freqChart);
        }

        // Gráfico 3: Distribuição por grupo muscular
        if (treinos.size() >= 3) {
            Map<String, Integer> distribution = buildMuscleGroupDistribution(treinos);
            if (distribution.size() >= 2) {
                byte[] distChart = chartService.generateMuscleGroupChart(distribution, nome);
                if (distChart != null) charts.add(distChart);
            }
        }

        // ==================== ANÁLISE TEXTUAL ====================

        String analysisHtml = buildAnalysisHtml(user, pesoData, treinos);

        // ==================== ENVIO ====================

        // Envia gráficos como álbum (ou individualmente) se houver
        if (!charts.isEmpty()) {
            try {
                String albumCaption = "\uD83D\uDCCA <b>Progresso de " + nome + "</b> — últimos " + PERIOD_DAYS + " dias";
                messageGateway.sendPhotoAlbum(context.chatId(), charts, albumCaption);
            } catch (Exception e) {
                log.error("Erro ao enviar gráficos para chatId={}: {}", context.chatId(), e.getMessage());
            }
        }

        // O texto é retornado via FlowResult (o Orchestrator envia)
        return FlowResult.done(
                analysisHtml,
                "Use /registro para atualizar medidas ou /treino para pedir um treino."
        );
    }

    // ========================================================================
    // CONSTRUÇÃO DA ANÁLISE HTML
    // ========================================================================

    private String buildAnalysisHtml(User user, List<Measurement> pesoData, List<Workout> treinos) {
        StringBuilder html = new StringBuilder();
        String nome = user.getNome() != null ? user.getNome() : "Você";

        html.append("\uD83D\uDCCA ").append(nome).append(", aqui está sua análise dos últimos ").append(PERIOD_DAYS).append(" dias!\n\n");

        // ---- Seção Peso ----
        if (!pesoData.isEmpty()) {
            double pesoAtual = pesoData.get(pesoData.size() - 1).getValor();
            double pesoInicial = pesoData.get(0).getValor();
            double variacao = pesoAtual - pesoInicial;
            double pesoMinimo = pesoData.stream().mapToDouble(Measurement::getValor).min().orElse(pesoAtual);
            double pesoMaximo = pesoData.stream().mapToDouble(Measurement::getValor).max().orElse(pesoAtual);

            String varIcon = variacao < -0.1 ? "\u2B07\uFE0F" : variacao > 0.1 ? "\u2B06\uFE0F" : "\u27A1\uFE0F";
            String varSign = variacao >= 0 ? "+" : "";

            html.append("\u2696\uFE0F PESO\n");
            html.append("Atual: ").append(String.format("%.1f kg", pesoAtual)).append("\n");
            html.append("Variação: ").append(varIcon).append(" ")
                    .append(String.format("%s%.1f kg", varSign, variacao)).append("\n");
            html.append("Faixa: ").append(String.format("%.1f", pesoMinimo))
                    .append(" — ").append(String.format("%.1f kg", pesoMaximo)).append("\n");
            html.append("Registros: ").append(pesoData.size()).append("\n\n");
        }

        // ---- Seção Treinos ----
        if (!treinos.isEmpty()) {
            int totalTreinos = treinos.size();
            int semanas = Math.max(1, (int) Math.ceil(PERIOD_DAYS / 7.0));
            double mediaPerWeek = (double) totalTreinos / semanas;

            OptionalDouble mediaIntensidade = treinos.stream()
                    .filter(w -> w.getIntensidadePercebida() != null)
                    .mapToInt(Workout::getIntensidadePercebida)
                    .average();

            OptionalInt mediaDuracao = treinos.stream()
                    .filter(w -> w.getDuracaoMinutos() != null)
                    .mapToInt(Workout::getDuracaoMinutos)
                    .max();

            int totalMinutos = treinos.stream()
                    .filter(w -> w.getDuracaoMinutos() != null)
                    .mapToInt(Workout::getDuracaoMinutos)
                    .sum();

            html.append("\uD83C\uDFCB\uFE0F TREINOS\n");
            html.append("Total: ").append(totalTreinos).append(" treinos\n");
            html.append("Média: ").append(String.format("%.1f", mediaPerWeek)).append(" treinos/semana\n");

            if (mediaIntensidade.isPresent()) {
                html.append("Intensidade média: ").append(String.format("%.1f", mediaIntensidade.getAsDouble())).append("/10\n");
            }

            html.append("Tempo total: ").append(formatMinutes(totalMinutos)).append("\n");

            if (mediaDuracao.isPresent()) {
                html.append("Treino mais longo: ").append(mediaDuracao.getAsInt()).append(" min\n");
            }

            // Grupo mais treinado
            Map<String, Integer> dist = buildMuscleGroupDistribution(treinos);
            if (!dist.isEmpty()) {
                String topGrupo = dist.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(e -> e.getKey() + " (" + e.getValue() + "x)")
                        .orElse("-");
                html.append("Mais treinado: ").append(topGrupo).append("\n");
            }

            html.append("\n");
        }

        // ---- Mensagem motivacional determinística ----
        html.append(buildMotivationalSuffix(user, pesoData, treinos));

        return html.toString();
    }

    private String buildMotivationalSuffix(User user, List<Measurement> pesoData, List<Workout> treinos) {
        int totalTreinos = treinos.size();
        Integer meta = user.getFrequenciaTreinoEstimada();

        if (totalTreinos == 0 && pesoData.isEmpty()) {
            return "Comece registrando dados para acompanhar sua evolução!";
        }

        StringBuilder msg = new StringBuilder();

        // Avalia consistência
        if (meta != null && meta > 0) {
            int semanas = Math.max(1, (int) Math.ceil(PERIOD_DAYS / 7.0));
            double mediaReal = (double) totalTreinos / semanas;
            double percentual = (mediaReal / meta) * 100;

            if (percentual >= 90) {
                msg.append("\uD83C\uDF1F Excelente consistência! Você está atingindo sua meta semanal.\n");
            } else if (percentual >= 60) {
                msg.append("\uD83D\uDCAA Bom ritmo! Você está no caminho certo.\n");
            } else if (percentual >= 30) {
                msg.append("\uD83D\uDE80 Tem espaço para mais treinos — que tal encaixar mais um dia na semana?\n");
            } else {
                msg.append("\u23F0 Você está treinando menos do que planejou. Bora retomar o ritmo!\n");
            }
        }

        // Avalia peso
        if (pesoData.size() >= 2) {
            double variacao = pesoData.get(pesoData.size() - 1).getValor() - pesoData.get(0).getValor();
            if (user.getObjetivo() != null) {
                String objetivo = user.getObjetivo().name();
                if (objetivo.equals("EMAGRECER") && variacao < -0.3) {
                    msg.append("\u2B07\uFE0F Seu peso está caindo — coerente com o objetivo de emagrecer!\n");
                } else if (objetivo.equals("GANHAR_MUSCULO") && variacao > 0.3) {
                    msg.append("\u2B06\uFE0F Peso subindo — pode ser ganho de massa! Ótimo sinal.\n");
                }
            }
        }

        if (msg.isEmpty()) {
            msg.append("Continue registrando para ver tendências mais claras!\n");
        }

        return msg.toString();
    }

    // ========================================================================
    // UTILITÁRIOS DE DADOS
    // ========================================================================

    private Map<String, Integer> buildWeeklyFrequency(List<Workout> treinos) {
        Map<String, Integer> weekly = new LinkedHashMap<>();
        WeekFields wf = WeekFields.of(Locale.getDefault());

        // Agrupa por semana
        Map<Integer, List<Workout>> byWeek = treinos.stream()
                .filter(w -> w.getDataRealizacao() != null)
                .collect(Collectors.groupingBy(
                        w -> w.getDataRealizacao().get(wf.weekOfWeekBasedYear()),
                        LinkedHashMap::new, Collectors.toList()));

        byWeek.forEach((weekNum, workouts) -> {
            if (!workouts.isEmpty()) {
                LocalDate firstDay = workouts.get(0).getDataRealizacao().toLocalDate();
                String label = String.format("%d/%d", firstDay.getDayOfMonth(), firstDay.getMonthValue());
                weekly.put("Sem " + label, workouts.size());
            }
        });

        return weekly;
    }

    private Map<String, Integer> buildMuscleGroupDistribution(List<Workout> treinos) {
        return treinos.stream()
                .filter(w -> w.getGrupoMuscular() != null)
                .collect(Collectors.groupingBy(
                        w -> formatGroupName(w.getGrupoMuscular().name()),
                        LinkedHashMap::new, Collectors.summingInt(w -> 1)));
    }

    private String formatGroupName(String enumName) {
        return switch (enumName) {
            case "PEITO" -> "Peito";
            case "COSTAS" -> "Costas";
            case "PERNAS" -> "Pernas";
            case "OMBRO" -> "Ombro";
            case "BRACOS" -> "Braços";
            case "ABDOMEN" -> "Abdômen";
            case "FULLBODY" -> "Full Body";
            case "CARDIO" -> "Cardio";
            case "CORRIDA" -> "Corrida";
            case "OUTRO" -> "Outro";
            default -> enumName;
        };
    }

    private String formatMinutes(int totalMinutos) {
        if (totalMinutos < 60) return totalMinutos + " min";
        int hours = totalMinutos / 60;
        int mins = totalMinutos % 60;
        return hours + "h" + (mins > 0 ? mins + "min" : "");
    }
}
