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
import br.edu.puc.fitjourneyai.infrastructure.chart.ProgressChartService.ProgressDashboardData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Fluxo de progresso: gera painel visual, graficos complementares e uma leitura
 * textual pratica sobre consistencia, volume, intensidade, peso e proximas acoes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressFlowHandler implements FlowHandler {

    private static final int PERIOD_DAYS = 30;
    private static final int TELEGRAM_ALBUM_LIMIT = 10;

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

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolvePeriodStart(user, end, PERIOD_DAYS);
        String name = displayName(user);

        List<Measurement> weightData = measurementRepository
                .findByUserAndTipoAndDataRegistroBetweenOrderByDataRegistroAsc(
                        user, MeasurementType.PESO, start, end);

        List<Workout> workouts = workoutRepository
                .findByUserAndDataRealizacaoBetween(user, start, end)
                .stream()
                .filter(w -> w.getDataRealizacao() != null)
                .sorted(Comparator.comparing(Workout::getDataRealizacao))
                .toList();

        if (weightData.isEmpty() && workouts.isEmpty()) {
            return FlowResult.done(
                    "Ainda não tenho dados suficientes para montar seu painel de progresso.\n\n" +
                    "Registre seu peso com /peso e seus treinos com /treino_feito. Depois disso eu consigo montar gráficos, tendências e metas.",
                    "Use /peso para registrar seu peso ou /treino_feito para registrar um treino."
            );
        }

        ProgressStats stats = buildStats(user, weightData, workouts, start.toLocalDate(), end.toLocalDate());
        List<byte[]> charts = buildCharts(user, name, weightData, workouts, stats, start.toLocalDate(), end.toLocalDate());
        sendCharts(context.chatId(), name, charts);

        return FlowResult.done(
                buildAnalysisHtml(user, weightData, workouts, stats),
                "Use /treino_feito para registrar o proximo treino ou /peso para atualizar seu check-in."
        );
    }

    private List<byte[]> buildCharts(
            User user,
            String name,
            List<Measurement> weightData,
            List<Workout> workouts,
            ProgressStats stats,
            LocalDate start,
            LocalDate end
    ) {
        List<byte[]> charts = new ArrayList<>();

        addChart(charts, chartService.generateProgressDashboard(new ProgressDashboardData(
                name,
                workouts.size(),
                stats.totalMinutes(),
                stats.workoutsPerWeek(),
                stats.consistencyPercent(),
                stats.currentTrainingStreak(),
                stats.topGroup(),
                stats.weightChange(),
                stats.dashboardInsight()
        )));

        if (!workouts.isEmpty()) {
            addChart(charts, chartService.generateTrainingFrequencyChart(
                    buildWeeklyFrequency(workouts, start, end), name, user.getFrequenciaTreinoEstimada()));
            addChart(charts, chartService.generateWorkoutVolumeChart(buildWeeklyVolume(workouts, start, end), name));
            addChart(charts, chartService.generateWorkoutCalendarHeatmap(buildDailyWorkoutCounts(workouts, start, end), name));

            Map<LocalDate, Double> intensityTrend = buildIntensityTrend(workouts);
            if (intensityTrend.size() >= 2) {
                addChart(charts, chartService.generateIntensityTrendChart(intensityTrend, name));
            }

            Map<String, Integer> distribution = buildMuscleGroupDistribution(workouts);
            if (distribution.size() >= 2) {
                addChart(charts, chartService.generateMuscleGroupChart(distribution, name));
            }
        }

        if (weightData.size() >= 2) {
            addChart(charts, chartService.generateWeightChart(buildWeightMap(weightData), name));
        }

        if (charts.size() > TELEGRAM_ALBUM_LIMIT) {
            return charts.subList(0, TELEGRAM_ALBUM_LIMIT);
        }
        return charts;
    }

    private void addChart(List<byte[]> charts, byte[] chart) {
        if (chart != null && chart.length > 0) {
            charts.add(chart);
        }
    }

    private void sendCharts(Long chatId, String name, List<byte[]> charts) {
        if (charts.isEmpty()) {
            return;
        }

        try {
            String caption = "📊 <b>Progresso de " + escapeHtml(name) + "</b> - ultimos " + PERIOD_DAYS + " dias";
            messageGateway.sendPhotoAlbum(chatId, charts, caption);
        } catch (Exception e) {
            log.error("Erro ao enviar graficos de progresso para chatId={}: {}", chatId, e.getMessage());
        }
    }

    private String buildAnalysisHtml(User user, List<Measurement> weightData, List<Workout> workouts, ProgressStats stats) {
        String name = displayName(user);
        int trackedDays = effectiveDays(user, PERIOD_DAYS);
        String periodLabel = trackedDays < PERIOD_DAYS
                ? "desde que você começou comigo (" + trackedDays + (trackedDays == 1 ? " dia" : " dias") + ")"
                : "dos últimos " + PERIOD_DAYS + " dias";

        StringBuilder html = new StringBuilder();
        html.append("📊 <b>").append(escapeHtml(name)).append(", seu painel de progresso ").append(periodLabel).append("</b>\n\n");

        html.append("<b>Resumo executivo</b>\n");
        if (!workouts.isEmpty()) {
            html.append("<b>TREINOS</b>\n");
            html.append("• Consistencia: ").append(stats.consistencyPercent()).append("% da meta");
            if (stats.plannedWorkouts() > 0) {
                html.append(" (").append(workouts.size()).append("/").append(stats.plannedWorkouts()).append(" treinos previstos)");
            }
            html.append("\n");
            if (trackedDays < 7) {
                html.append("• Ritmo inicial: ").append(workouts.size()).append(workouts.size() == 1 ? " treino" : " treinos")
                        .append(" em ").append(trackedDays).append(trackedDays == 1 ? " dia" : " dias").append("\n");
            }
            html.append("• Volume: ").append(formatMinutes(stats.totalMinutes()))
                    .append(" em ").append(workouts.size()).append(workouts.size() == 1 ? " treino" : " treinos").append("\n");
            html.append("• Media: ").append(String.format("%.1f", stats.workoutsPerWeek())).append(" treinos/semana");
            if (stats.averageDuration().isPresent()) {
                html.append(" | ").append(Math.round(stats.averageDuration().getAsDouble())).append(" min por treino");
            }
            html.append("\n");
            if (stats.averageIntensity().isPresent()) {
                html.append("• Intensidade media: ").append(String.format("%.1f", stats.averageIntensity().getAsDouble())).append("/10\n");
            }
            if (stats.currentTrainingStreak() > 0) {
                html.append("• Sequencia recente: ").append(stats.currentTrainingStreak()).append(" dias de treino consecutivos\n");
            }
            if (stats.topGroup() != null) {
                html.append("• Grupo mais trabalhado: ").append(escapeHtml(stats.topGroup())).append("\n");
            }
            html.append("\n");
        }

        if (!weightData.isEmpty()) {
            double currentWeight = weightData.get(weightData.size() - 1).getValor();
            double firstWeight = weightData.get(0).getValor();
            double change = currentWeight - firstWeight;
            double minWeight = weightData.stream().mapToDouble(Measurement::getValor).min().orElse(currentWeight);
            double maxWeight = weightData.stream().mapToDouble(Measurement::getValor).max().orElse(currentWeight);

            html.append("<b>PESO</b>\n");
            html.append("• Atual: ").append(String.format("%.1f kg", currentWeight)).append("\n");
            html.append("• Variação: ").append(changeIcon(change)).append(" ")
                    .append(String.format("%+.1f kg", change)).append("\n");
            html.append("• Faixa: ").append(String.format("%.1f", minWeight))
                    .append(" - ").append(String.format("%.1f kg", maxWeight)).append("\n");
            html.append("• Registros: ").append(weightData.size()).append("\n\n");
        }

        html.append("<b>Leitura pratica</b>\n");
        html.append("• ").append(escapeHtml(stats.textInsight())).append("\n");
        html.append("• ").append(escapeHtml(nextAction(user, workouts, stats))).append("\n");

        return html.toString();
    }

    private ProgressStats buildStats(User user, List<Measurement> weightData, List<Workout> workouts, LocalDate start, LocalDate end) {
        int trackedDays = effectiveDays(user, PERIOD_DAYS);
        int weeks = Math.max(1, (int) Math.ceil(trackedDays / 7.0));
        int totalWorkouts = workouts.size();
        int totalMinutes = workouts.stream()
                .filter(w -> w.getDuracaoMinutos() != null)
                .mapToInt(Workout::getDuracaoMinutos)
                .sum();

        OptionalDouble averageDuration = workouts.stream()
                .filter(w -> w.getDuracaoMinutos() != null)
                .mapToInt(Workout::getDuracaoMinutos)
                .average();

        OptionalInt longestWorkout = workouts.stream()
                .filter(w -> w.getDuracaoMinutos() != null)
                .mapToInt(Workout::getDuracaoMinutos)
                .max();

        OptionalDouble averageIntensity = workouts.stream()
                .filter(w -> w.getIntensidadePercebida() != null)
                .mapToInt(Workout::getIntensidadePercebida)
                .average();

        double workoutsPerWeek = (double) totalWorkouts / weeks;
        int plannedWorkouts = user.getFrequenciaTreinoEstimada() != null && user.getFrequenciaTreinoEstimada() > 0
                ? user.getFrequenciaTreinoEstimada() * weeks
                : 0;
        int consistencyPercent = plannedWorkouts > 0
                ? (int) Math.round((totalWorkouts * 100.0) / plannedWorkouts)
                : Math.min(100, (int) Math.round(workoutsPerWeek * 25));

        Map<String, Integer> distribution = buildMuscleGroupDistribution(workouts);
        String topGroup = distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + "x)")
                .orElse(null);

        Double weightChange = weightData.size() >= 2
                ? weightData.get(weightData.size() - 1).getValor() - weightData.get(0).getValor()
                : null;

        int streak = calculateTrainingStreak(workouts);
        String textInsight = buildTextInsight(user, consistencyPercent, weightChange, averageIntensity, workouts, trackedDays);
        String dashboardInsight = buildDashboardInsight(consistencyPercent, totalMinutes, topGroup, weightChange);

        return new ProgressStats(
                totalMinutes,
                workoutsPerWeek,
                plannedWorkouts,
                consistencyPercent,
                averageDuration,
                longestWorkout,
                averageIntensity,
                topGroup,
                weightChange,
                streak,
                textInsight,
                dashboardInsight
        );
    }

    private String buildTextInsight(
            User user,
            int consistencyPercent,
            Double weightChange,
            OptionalDouble averageIntensity,
            List<Workout> workouts,
            int trackedDays
    ) {
        if (workouts.isEmpty()) {
            if (weightChange != null && weightChange < -0.3) {
                return "Seu peso está caindo; registre treinos para eu conectar essa tendência com consistência, volume e intensidade.";
            }
            return "Seu historico corporal ja existe; agora registre treinos para conectar peso, rotina e consistencia.";
        }

        if (trackedDays < 7) {
            return "Começo registrado. Você já colocou treino no histórico; agora vale completar a semana antes de eu cobrar ritmo com mais rigor.";
        }

        if (consistencyPercent >= 100) {
            return "Voce bateu ou superou a meta de frequencia. O foco agora e sustentar o ritmo sem negligenciar recuperacao.";
        }
        if (consistencyPercent >= 75) {
            return "Sua rotina esta bem encaminhada. Um treino extra em uma semana mais fraca ja aproxima voce da meta cheia.";
        }
        if (consistencyPercent >= 45) {
            return "Existe base de habito, mas a frequencia ainda oscila. Priorize horarios fixos e treinos mais curtos nos dias apertados.";
        }
        if (weightChange != null && user.getObjetivo() != null && user.getObjetivo().name().equals("EMAGRECER") && weightChange < -0.3) {
            return "O peso esta caindo, mas a frequencia de treino pode crescer para preservar massa magra e melhorar condicionamento.";
        }
        if (averageIntensity.isPresent() && averageIntensity.getAsDouble() >= 8.5) {
            return "A intensidade esta alta. Se a frequencia cair, reduza um pouco a carga percebida para manter regularidade.";
        }
        return "Voce esta treinando menos do que planejou. O melhor proximo passo e marcar dois dias fixos para retomar tracao.";
    }

    private String buildDashboardInsight(int consistencyPercent, int totalMinutes, String topGroup, Double weightChange) {
        if (consistencyPercent >= 100) {
            return "Meta semanal no alvo. Continue variando grupos musculares e preserve descanso para transformar volume em progresso.";
        }
        if (totalMinutes >= 600) {
            return "Bom volume acumulado. Oportunidade principal: distribuir melhor as sessoes ao longo das semanas.";
        }
        if (topGroup != null) {
            return "Seu historico mostra preferencia por " + topGroup + ". Equilibre a rotina com grupos menos treinados.";
        }
        if (weightChange != null) {
            return "A tendencia de peso ja aparece. Combine novos check-ins com registros de treino para leitura mais completa.";
        }
        return "Dados iniciais registrados. Continue alimentando o historico para o painel ganhar precisao.";
    }

    private String nextAction(User user, List<Workout> workouts, ProgressStats stats) {
        if (workouts.isEmpty()) {
            return "Proxima acao: registre o primeiro treino realizado com /treino_feito.";
        }
        if (user.getFrequenciaTreinoEstimada() != null && user.getFrequenciaTreinoEstimada() > 0
                && stats.consistencyPercent() < 100) {
            int missing = Math.max(1, stats.plannedWorkouts() - workouts.size());
            return "Proxima acao: encaixe " + missing + (missing == 1 ? " treino" : " treinos") +
                    " no periodo para chegar mais perto da sua meta.";
        }
        if (stats.topGroup() != null && stats.topGroup().toLowerCase(Locale.ROOT).contains("pernas")) {
            return "Proxima acao: mantenha pernas na rotina, mas garanta alternancia com superiores e core.";
        }
        return "Proxima acao: registre o proximo treino com duracao e intensidade para melhorar a analise.";
    }

    private Map<LocalDate, Double> buildWeightMap(List<Measurement> weightData) {
        Map<LocalDate, Double> data = new LinkedHashMap<>();
        weightData.stream()
                .filter(m -> m.getDataRegistro() != null)
                .forEach(m -> data.put(m.getDataRegistro().toLocalDate(), m.getValor()));
        return data;
    }

    private Map<String, Integer> buildWeeklyFrequency(List<Workout> workouts, LocalDate start, LocalDate end) {
        Map<LocalDate, Integer> byWeek = initializeWeekBuckets(start, end);
        workouts.forEach(w -> {
            LocalDate weekStart = weekStart(w.getDataRealizacao().toLocalDate());
            byWeek.computeIfPresent(weekStart, (key, value) -> value + 1);
        });
        return toWeekLabelMap(byWeek);
    }

    private Map<String, Integer> buildWeeklyVolume(List<Workout> workouts, LocalDate start, LocalDate end) {
        Map<LocalDate, Integer> byWeek = initializeWeekBuckets(start, end);
        workouts.stream()
                .filter(w -> w.getDuracaoMinutos() != null)
                .forEach(w -> {
                    LocalDate weekStart = weekStart(w.getDataRealizacao().toLocalDate());
                    byWeek.computeIfPresent(weekStart, (key, value) -> value + w.getDuracaoMinutos());
                });
        return toWeekLabelMap(byWeek);
    }

    private Map<LocalDate, Integer> buildDailyWorkoutCounts(List<Workout> workouts, LocalDate start, LocalDate end) {
        Map<LocalDate, Integer> daily = new LinkedHashMap<>();
        LocalDate chartStart = end.minusDays(PERIOD_DAYS - 1L);
        if (start.isAfter(chartStart)) {
            chartStart = start;
        }
        for (LocalDate date = chartStart; !date.isAfter(end); date = date.plusDays(1)) {
            daily.put(date, 0);
        }
        workouts.forEach(w -> {
            LocalDate date = w.getDataRealizacao().toLocalDate();
            daily.computeIfPresent(date, (key, value) -> value + 1);
        });
        return daily;
    }

    private Map<LocalDate, Double> buildIntensityTrend(List<Workout> workouts) {
        Map<LocalDate, List<Workout>> byDate = workouts.stream()
                .filter(w -> w.getIntensidadePercebida() != null)
                .collect(Collectors.groupingBy(
                        w -> w.getDataRealizacao().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        Map<LocalDate, Double> trend = new LinkedHashMap<>();
        byDate.forEach((date, dayWorkouts) -> dayWorkouts.stream()
                .mapToInt(Workout::getIntensidadePercebida)
                .average()
                .ifPresent(avg -> trend.put(date, avg)));
        return trend;
    }

    private Map<String, Integer> buildMuscleGroupDistribution(List<Workout> workouts) {
        return workouts.stream()
                .filter(w -> w.getGrupoMuscular() != null)
                .collect(Collectors.groupingBy(
                        w -> formatGroupName(w.getGrupoMuscular().name()),
                        LinkedHashMap::new,
                        Collectors.summingInt(w -> 1)));
    }

    private Map<LocalDate, Integer> initializeWeekBuckets(LocalDate start, LocalDate end) {
        Map<LocalDate, Integer> buckets = new LinkedHashMap<>();
        LocalDate cursor = weekStart(start);
        LocalDate last = weekStart(end);
        while (!cursor.isAfter(last)) {
            buckets.put(cursor, 0);
            cursor = cursor.plusWeeks(1);
        }
        return buckets;
    }

    private Map<String, Integer> toWeekLabelMap(Map<LocalDate, Integer> byWeek) {
        Map<String, Integer> labeled = new LinkedHashMap<>();
        byWeek.forEach((week, value) -> labeled.put("Sem " + week.getDayOfMonth() + "/" + week.getMonthValue(), value));
        return labeled;
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private int calculateTrainingStreak(List<Workout> workouts) {
        Set<LocalDate> days = workouts.stream()
                .filter(w -> w.getDataRealizacao() != null)
                .map(w -> w.getDataRealizacao().toLocalDate())
                .collect(Collectors.toSet());
        if (days.isEmpty()) {
            return 0;
        }

        LocalDate cursor = days.stream().max(LocalDate::compareTo).orElse(LocalDate.now());
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private String formatGroupName(String enumName) {
        return switch (enumName) {
            case "PEITO" -> "Peito";
            case "COSTAS" -> "Costas";
            case "PERNAS" -> "Pernas";
            case "OMBRO" -> "Ombro";
            case "BRACOS" -> "Bracos";
            case "ABDOMEN" -> "Abdomen";
            case "FULLBODY" -> "Full Body";
            case "CARDIO" -> "Cardio";
            case "CORRIDA" -> "Corrida";
            case "OUTRO" -> "Outro";
            default -> enumName;
        };
    }

    private String formatMinutes(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + " min";
        }
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours + "h" + (minutes > 0 ? minutes + "min" : "");
    }

    private String changeIcon(double change) {
        if (change < -0.1) {
            return "⬇️";
        }
        if (change > 0.1) {
            return "⬆️";
        }
        return "➡️";
    }

    private String displayName(User user) {
        return user.getNome() != null && !user.getNome().isBlank() ? user.getNome() : "Voce";
    }

    private LocalDateTime resolvePeriodStart(User user, LocalDateTime end, int maxDays) {
        LocalDateTime defaultStart = end.minusDays(maxDays);
        if (user.getCreatedAt() == null || user.getCreatedAt().isBefore(defaultStart)) {
            return defaultStart;
        }
        return user.getCreatedAt();
    }

    private int effectiveDays(User user, int fallbackDays) {
        if (user.getCreatedAt() == null) {
            return fallbackDays;
        }
        long days = ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
        return Math.max(1, Math.min(fallbackDays, (int) days));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record ProgressStats(
            int totalMinutes,
            double workoutsPerWeek,
            int plannedWorkouts,
            int consistencyPercent,
            OptionalDouble averageDuration,
            OptionalInt longestWorkout,
            OptionalDouble averageIntensity,
            String topGroup,
            Double weightChange,
            int currentTrainingStreak,
            String textInsight,
            String dashboardInsight
    ) {
    }
}
