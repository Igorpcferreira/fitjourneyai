package br.edu.puc.fitjourneyai.infrastructure.chart;

import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Servico de geracao de graficos de progresso com design dark-mode.
 */
@Slf4j
@Service
public class ProgressChartService {

    public record ProgressDashboardData(
            String userName,
            int totalWorkouts,
            int totalMinutes,
            double workoutsPerWeek,
            int consistencyPercent,
            int currentStreak,
            String topGroup,
            Double weightChangeKg,
            String insight
    ) {
    }

    private static final Color BG_DARK = new Color(26, 27, 38);
    private static final Color BG_PLOT = new Color(35, 37, 50);
    private static final Color BG_CARD = new Color(40, 43, 58);
    private static final Color BG_CARD_ALT = new Color(31, 34, 47);
    private static final Color GRID_COLOR = new Color(55, 58, 75);
    private static final Color TEXT_PRIMARY = new Color(232, 235, 245);
    private static final Color TEXT_SECONDARY = new Color(160, 165, 185);
    private static final Color ACCENT_CYAN = new Color(0, 210, 235);
    private static final Color ACCENT_GREEN = new Color(50, 215, 130);
    private static final Color ACCENT_PURPLE = new Color(155, 100, 255);
    private static final Color ACCENT_ORANGE = new Color(255, 165, 50);
    private static final Color ACCENT_PINK = new Color(255, 100, 150);
    private static final Color ACCENT_YELLOW = new Color(255, 220, 60);

    private static final Color[] DONUT_PALETTE = {
            ACCENT_GREEN, ACCENT_CYAN, ACCENT_PURPLE, ACCENT_ORANGE,
            ACCENT_PINK, ACCENT_YELLOW, new Color(100, 180, 255),
            new Color(180, 130, 255), new Color(255, 140, 100), new Color(130, 230, 200)
    };

    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 24);
    private static final Font SUBTITLE_FONT = new Font("SansSerif", Font.PLAIN, 15);
    private static final Font AXIS_LABEL_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Font TICK_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font LEGEND_FONT = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font CARD_LABEL_FONT = new Font("SansSerif", Font.PLAIN, 18);
    private static final Font CARD_VALUE_FONT = new Font("SansSerif", Font.BOLD, 34);
    private static final Font CARD_SMALL_FONT = new Font("SansSerif", Font.PLAIN, 15);

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 650;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");

    public byte[] generateProgressDashboard(ProgressDashboardData data) {
        if (data == null) {
            return null;
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        applyQualityHints(g);

        paintBackground(g);
        drawTitle(g, "Painel de Progresso", safeUser(data.userName()) + " - ultimos 30 dias");

        drawMetricCard(g, 60, 120, 260, 150, "Treinos", String.valueOf(data.totalWorkouts()),
                "sessoes registradas", ACCENT_GREEN);
        drawMetricCard(g, 370, 120, 260, 150, "Tempo total", formatDuration(data.totalMinutes()),
                "volume acumulado", ACCENT_ORANGE);
        drawMetricCard(g, 680, 120, 260, 150, "Sequencia", data.currentStreak() + "d",
                "dias ativos seguidos", ACCENT_CYAN);

        drawConsistencyPanel(g, data);
        drawInsightPanel(g, data);

        g.dispose();
        return imageToBytes(image);
    }

    public byte[] generateWeightChart(Map<LocalDate, Double> dataPoints, String userName) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return null;
        }

        TimeSeries series = new TimeSeries("Peso (kg)");
        dataPoints.forEach((date, weight) ->
                series.add(new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear()), weight));

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, dataset, false, false, false);

        chart.setTitle(createTitle("Evolucao de Peso"));
        chart.addSubtitle(createSubtitle(safeUser(userName) + " - tendencia corporal"));
        applyDarkTheme(chart);

        XYPlot plot = chart.getXYPlot();
        styleXYPlot(plot);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, ACCENT_CYAN);
        renderer.setSeriesStroke(0, new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        renderer.setSeriesShape(0, new Ellipse2D.Double(-5, -5, 10, 10));
        renderer.setSeriesShapesFilled(0, true);
        plot.setRenderer(renderer);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("dd/MM"));
        styleDateAxis(domainAxis);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabel("Peso (kg)");
        styleNumberAxis(rangeAxis);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setLowerMargin(0.12);
        rangeAxis.setUpperMargin(0.12);

        return toBytes(chart);
    }

    public byte[] generateTrainingFrequencyChart(Map<String, Integer> weeklyData, String userName) {
        return generateTrainingFrequencyChart(weeklyData, userName, null);
    }

    public byte[] generateTrainingFrequencyChart(Map<String, Integer> weeklyData, String userName, Integer targetPerWeek) {
        if (weeklyData == null || weeklyData.isEmpty()) {
            return null;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        weeklyData.forEach((week, count) -> dataset.addValue(count, "Treinos", week));

        JFreeChart chart = ChartFactory.createBarChart(null, null, null, dataset,
                PlotOrientation.VERTICAL, false, false, false);

        chart.setTitle(createTitle("Frequencia de Treinos"));
        chart.addSubtitle(createSubtitle(safeUser(userName) + " - sessoes por semana"));
        applyDarkTheme(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        styleCategoryPlot(plot);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        styleBarRenderer(renderer, ACCENT_GREEN);
        renderer.setMaximumBarWidth(0.18);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(TICK_FONT);
        renderer.setDefaultItemLabelPaint(TEXT_PRIMARY);

        styleCategoryAxis(plot.getDomainAxis());
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabel("Treinos");
        styleNumberAxis(rangeAxis);
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setRangeWithMargins(0, Math.max(maxValue(weeklyData), targetPerWeek == null ? 0 : targetPerWeek) + 1);

        if (targetPerWeek != null && targetPerWeek > 0) {
            org.jfree.chart.plot.ValueMarker marker = new org.jfree.chart.plot.ValueMarker(targetPerWeek);
            marker.setPaint(ACCENT_YELLOW);
            marker.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1.0f, new float[]{8.0f, 6.0f}, 0.0f));
            marker.setLabel("meta " + targetPerWeek + "x");
            marker.setLabelPaint(TEXT_PRIMARY);
            marker.setLabelFont(TICK_FONT);
            plot.addRangeMarker(marker);
        }

        return toBytes(chart);
    }

    public byte[] generateWorkoutVolumeChart(Map<String, Integer> weeklyMinutes, String userName) {
        if (weeklyMinutes == null || weeklyMinutes.isEmpty()) {
            return null;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        weeklyMinutes.forEach((week, minutes) -> dataset.addValue(minutes, "Minutos", week));

        JFreeChart chart = ChartFactory.createBarChart(null, null, null, dataset,
                PlotOrientation.VERTICAL, false, false, false);

        chart.setTitle(createTitle("Volume de Treino"));
        chart.addSubtitle(createSubtitle(safeUser(userName) + " - minutos acumulados por semana"));
        applyDarkTheme(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        styleCategoryPlot(plot);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        styleBarRenderer(renderer, ACCENT_ORANGE);
        renderer.setMaximumBarWidth(0.20);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(TICK_FONT);
        renderer.setDefaultItemLabelPaint(TEXT_PRIMARY);

        styleCategoryAxis(plot.getDomainAxis());
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabel("Minutos");
        styleNumberAxis(rangeAxis);
        rangeAxis.setRangeWithMargins(0, maxValue(weeklyMinutes) * 1.20 + 20);

        return toBytes(chart);
    }

    public byte[] generateIntensityTrendChart(Map<LocalDate, Double> intensityData, String userName) {
        if (intensityData == null || intensityData.isEmpty()) {
            return null;
        }

        TimeSeries series = new TimeSeries("Intensidade");
        intensityData.forEach((date, value) ->
                series.add(new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear()), value));

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, dataset, false, false, false);

        chart.setTitle(createTitle("Intensidade Percebida"));
        chart.addSubtitle(createSubtitle(safeUser(userName) + " - escala 1 a 10"));
        applyDarkTheme(chart);

        XYPlot plot = chart.getXYPlot();
        styleXYPlot(plot);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, ACCENT_PURPLE);
        renderer.setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        renderer.setSeriesShape(0, new Ellipse2D.Double(-4.5, -4.5, 9, 9));
        renderer.setSeriesShapesFilled(0, true);
        plot.setRenderer(renderer);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("dd/MM"));
        styleDateAxis(domainAxis);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabel("Intensidade");
        styleNumberAxis(rangeAxis);
        rangeAxis.setRange(0, 10);

        return toBytes(chart);
    }

    public byte[] generateMuscleGroupChart(Map<String, Integer> distribution, String userName) {
        if (distribution == null || distribution.isEmpty()) {
            return null;
        }

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        distribution.forEach(dataset::setValue);

        JFreeChart chart = ChartFactory.createRingChart(null, dataset, true, false, false);
        chart.setTitle(createTitle("Distribuicao de Treinos"));
        chart.addSubtitle(createSubtitle(safeUser(userName) + " - por grupo muscular"));
        applyDarkTheme(chart);

        RingPlot plot = (RingPlot) chart.getPlot();
        plot.setBackgroundPaint(BG_DARK);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelFont(TICK_FONT);
        plot.setLabelPaint(TEXT_PRIMARY);
        plot.setLabelGenerator(new org.jfree.chart.labels.StandardPieSectionLabelGenerator(
                "{0}: {1} ({2})", new DecimalFormat("0"), new DecimalFormat("0%")));
        plot.setSectionDepth(0.42);
        plot.setSeparatorsVisible(false);
        plot.setInteriorGap(0.08);

        List<String> keys = distribution.keySet().stream().toList();
        for (int i = 0; i < keys.size(); i++) {
            plot.setSectionPaint(keys.get(i), DONUT_PALETTE[i % DONUT_PALETTE.length]);
        }

        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(BG_DARK);
            legend.setItemFont(LEGEND_FONT);
            legend.setItemPaint(TEXT_PRIMARY);
            legend.setPosition(RectangleEdge.BOTTOM);
        }

        return toBytes(chart);
    }

    public byte[] generateWorkoutCalendarHeatmap(Map<LocalDate, Integer> workoutsByDate, String userName) {
        if (workoutsByDate == null || workoutsByDate.isEmpty()) {
            return null;
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        applyQualityHints(g);

        paintBackground(g);
        drawTitle(g, "Mapa de Consistencia", safeUser(userName) + " - treinos por dia");

        LocalDate start = workoutsByDate.keySet().stream().min(LocalDate::compareTo).orElse(LocalDate.now().minusDays(29));
        LocalDate end = workoutsByDate.keySet().stream().max(LocalDate::compareTo).orElse(LocalDate.now());

        int cell = 54;
        int gap = 12;
        int left = 115;
        int top = 135;
        String[] weekdays = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom"};

        g.setFont(CARD_SMALL_FONT);
        g.setColor(TEXT_SECONDARY);
        for (int i = 0; i < weekdays.length; i++) {
            g.drawString(weekdays[i], 60, top + i * (cell + gap) + 34);
        }

        int index = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            int week = index / 7;
            int dow = index % 7;
            int x = left + week * (cell + gap);
            int y = top + dow * (cell + gap);
            int count = workoutsByDate.getOrDefault(date, 0);
            Color fill = heatmapColor(count);

            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(x, y, cell, cell, 12, 12));
            g.setColor(new Color(255, 255, 255, 28));
            g.draw(new RoundRectangle2D.Double(x, y, cell, cell, 12, 12));

            if (count > 0) {
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                drawCenteredText(g, String.valueOf(count), x, y + 2, cell, cell, Color.WHITE);
            }
            index++;
        }

        g.setFont(CARD_SMALL_FONT);
        g.setColor(TEXT_SECONDARY);
        int monthY = top - 20;
        for (int week = 0; week <= (int) Math.ceil((index - 1) / 7.0); week++) {
            LocalDate labelDate = start.plusDays(week * 7L);
            if (!labelDate.isAfter(end)) {
                g.drawString(labelDate.format(SHORT_DATE), left + week * (cell + gap), monthY);
            }
        }

        drawLegend(g, left, 585);

        g.dispose();
        return imageToBytes(image);
    }

    private void styleBarRenderer(BarRenderer renderer, Color color) {
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setSeriesPaint(0, color);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
    }

    private void styleCategoryPlot(CategoryPlot plot) {
        plot.setBackgroundPaint(BG_PLOT);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(18, 18, 18, 22));
    }

    private void styleXYPlot(XYPlot plot) {
        plot.setBackgroundPaint(BG_PLOT);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(18, 18, 18, 22));
    }

    private void styleCategoryAxis(CategoryAxis axis) {
        axis.setTickLabelFont(TICK_FONT);
        axis.setTickLabelPaint(TEXT_SECONDARY);
        axis.setAxisLinePaint(GRID_COLOR);
        axis.setCategoryMargin(0.25);
    }

    private void styleDateAxis(DateAxis axis) {
        axis.setTickLabelFont(TICK_FONT);
        axis.setTickLabelPaint(TEXT_SECONDARY);
        axis.setLabelFont(AXIS_LABEL_FONT);
        axis.setLabelPaint(TEXT_SECONDARY);
        axis.setAxisLinePaint(GRID_COLOR);
    }

    private void styleNumberAxis(NumberAxis axis) {
        axis.setTickLabelFont(TICK_FONT);
        axis.setTickLabelPaint(TEXT_SECONDARY);
        axis.setLabelFont(AXIS_LABEL_FONT);
        axis.setLabelPaint(TEXT_SECONDARY);
        axis.setAxisLinePaint(GRID_COLOR);
        axis.setAutoRangeIncludesZero(true);
    }

    private TextTitle createTitle(String text) {
        return createTextTitle(text, TITLE_FONT, TEXT_PRIMARY);
    }

    private TextTitle createSubtitle(String text) {
        return createTextTitle(text, SUBTITLE_FONT, TEXT_SECONDARY);
    }

    private TextTitle createTextTitle(String text, Font font, Paint paint) {
        TextTitle textTitle = new TextTitle(text, font);
        textTitle.setPaint(paint);
        textTitle.setPosition(RectangleEdge.TOP);
        textTitle.setPadding(new RectangleInsets(6, 0, 4, 0));
        return textTitle;
    }

    private void applyDarkTheme(JFreeChart chart) {
        chart.setBackgroundPaint(BG_DARK);
        chart.setBorderVisible(false);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setPadding(new RectangleInsets(18, 22, 20, 22));
    }

    private byte[] toBytes(JFreeChart chart) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ChartUtils.writeChartAsPNG(out, chart, WIDTH, HEIGHT);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar PNG do grafico: {}", e.getMessage());
            return null;
        }
    }

    private byte[] imageToBytes(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar PNG customizado: {}", e.getMessage());
            return null;
        }
    }

    private void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void paintBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(22, 23, 34), WIDTH, HEIGHT, new Color(34, 36, 52)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(255, 255, 255, 10));
        for (int x = -120; x < WIDTH; x += 190) {
            g.draw(new Line2D.Double(x, 0, x + 260, HEIGHT));
        }
    }

    private void drawTitle(Graphics2D g, String title, String subtitle) {
        g.setFont(TITLE_FONT);
        drawCenteredText(g, title, 0, 35, WIDTH, 34, TEXT_PRIMARY);
        g.setFont(SUBTITLE_FONT);
        drawCenteredText(g, subtitle, 0, 70, WIDTH, 24, TEXT_SECONDARY);
    }

    private void drawMetricCard(Graphics2D g, int x, int y, int w, int h, String label, String value, String detail, Color accent) {
        drawCard(g, x, y, w, h, BG_CARD);
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Double(x + 22, y + 24, 52, 7, 7, 7));
        g.setFont(CARD_LABEL_FONT);
        g.setColor(TEXT_SECONDARY);
        g.drawString(label, x + 22, y + 62);
        g.setFont(CARD_VALUE_FONT);
        g.setColor(TEXT_PRIMARY);
        g.drawString(value, x + 22, y + 108);
        g.setFont(CARD_SMALL_FONT);
        g.setColor(TEXT_SECONDARY);
        g.drawString(detail, x + 22, y + 134);
    }

    private void drawConsistencyPanel(Graphics2D g, ProgressDashboardData data) {
        drawCard(g, 60, 315, 430, 220, BG_CARD_ALT);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(TEXT_PRIMARY);
        g.drawString("Consistencia", 90, 360);

        int percent = Math.max(0, Math.min(140, data.consistencyPercent()));
        int barX = 90;
        int barY = 410;
        int barW = 350;
        int barH = 24;
        g.setColor(new Color(255, 255, 255, 24));
        g.fill(new RoundRectangle2D.Double(barX, barY, barW, barH, 18, 18));
        g.setColor(consistencyColor(percent));
        g.fill(new RoundRectangle2D.Double(barX, barY, Math.min(barW, (int) (barW * (percent / 100.0))), barH, 18, 18));

        g.setFont(CARD_VALUE_FONT);
        g.setColor(TEXT_PRIMARY);
        g.drawString(percent + "%", 90, 488);
        g.setFont(CARD_SMALL_FONT);
        g.setColor(TEXT_SECONDARY);
        g.drawString(String.format("%.1f treinos/semana", data.workoutsPerWeek()), 220, 486);
        g.drawString("Grupo mais treinado: " + blankToDash(data.topGroup()), 90, 520);
    }

    private void drawInsightPanel(Graphics2D g, ProgressDashboardData data) {
        drawCard(g, 530, 315, 410, 220, BG_CARD_ALT);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(TEXT_PRIMARY);
        g.drawString("Leitura rapida", 560, 360);
        g.setFont(CARD_SMALL_FONT);
        g.setColor(TEXT_SECONDARY);
        drawWrappedText(g, data.insight() == null ? "Continue registrando para enxergar tendencias." : data.insight(),
                560, 402, 340, 26);

        if (data.weightChangeKg() != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(data.weightChangeKg() < 0 ? ACCENT_GREEN : data.weightChangeKg() > 0 ? ACCENT_ORANGE : TEXT_PRIMARY);
            g.drawString(String.format("Peso: %+,.1f kg", data.weightChangeKg()), 560, 505);
        }
    }

    private void drawCard(Graphics2D g, int x, int y, int w, int h, Color fill) {
        g.setColor(new Color(0, 0, 0, 55));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 6, w, h, 24, 24));
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 24, 24));
        g.setColor(new Color(255, 255, 255, 18));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 24, 24));
    }

    private void drawCenteredText(Graphics2D g, String text, int x, int y, int w, int h, Color color) {
        FontMetrics metrics = g.getFontMetrics();
        int textX = x + (w - metrics.stringWidth(text)) / 2;
        int textY = y + ((h - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setColor(color);
        g.drawString(text, textX, textY);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int currentY = y;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                g.drawString(line.toString(), x, currentY);
                line = new StringBuilder(word);
                currentY += lineHeight;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, currentY);
        }
    }

    private void drawLegend(Graphics2D g, int x, int y) {
        g.setFont(CARD_SMALL_FONT);
        g.setColor(TEXT_SECONDARY);
        g.drawString("Menos", x, y + 18);
        for (int i = 0; i < 4; i++) {
            g.setColor(heatmapColor(i));
            g.fill(new RoundRectangle2D.Double(x + 70 + i * 42, y, 28, 28, 8, 8));
        }
        g.setColor(TEXT_SECONDARY);
        g.drawString("Mais", x + 250, y + 18);
    }

    private Color heatmapColor(int count) {
        if (count <= 0) return new Color(51, 54, 70);
        if (count == 1) return new Color(47, 128, 105);
        if (count == 2) return ACCENT_GREEN;
        return new Color(105, 235, 165);
    }

    private Color consistencyColor(int percent) {
        if (percent >= 90) return ACCENT_GREEN;
        if (percent >= 60) return ACCENT_YELLOW;
        return ACCENT_PINK;
    }

    private int maxValue(Map<String, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + "min";
        }
        int hours = minutes / 60;
        int remaining = minutes % 60;
        return remaining == 0 ? hours + "h" : hours + "h" + remaining;
    }

    private String safeUser(String userName) {
        return userName == null || userName.isBlank() ? "Usuario" : userName;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
