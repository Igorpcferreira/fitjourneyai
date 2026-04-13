package br.edu.puc.fitjourneyai.infrastructure.chart;

import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
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
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Serviço de geração de gráficos de progresso com design premium.
 * <p>
 * Gera imagens PNG com estética dark-mode moderna, otimizada para
 * visualização no Telegram (800x500px, fundo escuro, cores vibrantes,
 * fontes limpas, anti-aliasing completo).
 * <p>
 * Três tipos de gráfico:
 * <ol>
 *   <li>Evolução de peso (linha com pontos e gradiente)</li>
 *   <li>Frequência de treinos por semana (barras com gradiente)</li>
 *   <li>Distribuição por grupo muscular (donut/rosca)</li>
 * </ol>
 */
@Slf4j
@Service
public class ProgressChartService {

    // ==================== PALETA DE CORES PREMIUM ====================
    private static final Color BG_DARK = new Color(26, 27, 38);           // Fundo principal
    private static final Color BG_PLOT = new Color(35, 37, 50);           // Fundo do plot
    private static final Color GRID_COLOR = new Color(55, 58, 75);        // Linhas de grade sutis
    private static final Color TEXT_PRIMARY = new Color(230, 232, 240);   // Texto principal
    private static final Color TEXT_SECONDARY = new Color(160, 165, 185); // Texto secundário
    private static final Color ACCENT_CYAN = new Color(0, 210, 235);      // Cor principal (peso)
    private static final Color ACCENT_GREEN = new Color(50, 215, 130);    // Treinos/positivo
    private static final Color ACCENT_PURPLE = new Color(155, 100, 255);  // Secundária
    private static final Color ACCENT_ORANGE = new Color(255, 165, 50);   // Destaque
    private static final Color ACCENT_PINK = new Color(255, 100, 150);    // Alerta
    private static final Color ACCENT_YELLOW = new Color(255, 220, 60);   // Extra

    // Paleta para gráfico de rosca
    private static final Color[] DONUT_PALETTE = {
            ACCENT_CYAN, ACCENT_GREEN, ACCENT_PURPLE, ACCENT_ORANGE,
            ACCENT_PINK, ACCENT_YELLOW, new Color(100, 180, 255),
            new Color(180, 130, 255), new Color(255, 140, 100), new Color(130, 230, 200)
    };

    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 18);
    private static final Font SUBTITLE_FONT = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font AXIS_LABEL_FONT = new Font("SansSerif", Font.BOLD, 13);
    private static final Font TICK_FONT = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font LEGEND_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;

    // ==================== GRÁFICO 1: EVOLUÇÃO DE PESO ====================

    /**
     * Gera gráfico de linha da evolução de peso ao longo do tempo.
     *
     * @param dataPoints mapa de data → peso (kg)
     * @param userName   nome do usuário para o título
     * @return bytes da imagem PNG
     */
    public byte[] generateWeightChart(Map<LocalDate, Double> dataPoints, String userName) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return null;
        }

        TimeSeries series = new TimeSeries("Peso (kg)");
        dataPoints.forEach((date, weight) ->
                series.add(new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear()), weight));

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, dataset, false, false, false);

        // Títulos
        chart.setTitle(createTitle("Evolução de Peso"));
        chart.addSubtitle(createSubtitle(userName + " — últimos 30 dias"));

        applyDarkTheme(chart);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(BG_PLOT);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(10, 15, 10, 20));

        // Renderer: linha grossa com pontos circulares
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, ACCENT_CYAN);
        renderer.setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        renderer.setSeriesShape(0, new Ellipse2D.Double(-5, -5, 10, 10));
        renderer.setSeriesShapesFilled(0, true);
        renderer.setSeriesShapesVisible(0, true);
        renderer.setDefaultItemLabelsVisible(false);
        plot.setRenderer(renderer);

        // Eixos
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new java.text.SimpleDateFormat("dd/MM"));
        domainAxis.setTickLabelFont(TICK_FONT);
        domainAxis.setTickLabelPaint(TEXT_SECONDARY);
        domainAxis.setLabelFont(AXIS_LABEL_FONT);
        domainAxis.setLabelPaint(TEXT_SECONDARY);
        domainAxis.setAxisLinePaint(GRID_COLOR);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabel("Peso (kg)");
        rangeAxis.setTickLabelFont(TICK_FONT);
        rangeAxis.setTickLabelPaint(TEXT_SECONDARY);
        rangeAxis.setLabelFont(AXIS_LABEL_FONT);
        rangeAxis.setLabelPaint(TEXT_SECONDARY);
        rangeAxis.setAxisLinePaint(GRID_COLOR);
        rangeAxis.setAutoRangeIncludesZero(false);

        // Margem vertical para não colar nos limites
        double margin = (rangeAxis.getUpperBound() - rangeAxis.getLowerBound()) * 0.15;
        if (margin < 1) {
            margin = 1;
        }
        rangeAxis.setLowerMargin(0.1);
        rangeAxis.setUpperMargin(0.1);

        return toBytes(chart);
    }

    // ==================== GRÁFICO 2: FREQUÊNCIA DE TREINOS ====================

    /**
     * Gera gráfico de barras da frequência de treinos por semana.
     *
     * @param weeklyData mapa de label da semana → quantidade de treinos
     * @param userName   nome do usuário
     * @return bytes da imagem PNG
     */
    public byte[] generateTrainingFrequencyChart(Map<String, Integer> weeklyData, String userName) {
        if (weeklyData == null || weeklyData.isEmpty()) {
            return null;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        weeklyData.forEach((week, count) -> dataset.addValue(count, "Treinos", week));

        JFreeChart chart = ChartFactory.createBarChart(null, null, null, dataset,
                PlotOrientation.VERTICAL, false, false, false);

        chart.setTitle(createTitle("Frequência de Treinos"));
        chart.addSubtitle(createSubtitle(userName + " — treinos por semana"));

        applyDarkTheme(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(BG_PLOT);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(10, 15, 10, 20));

        // Barras com gradiente
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter()); // Sem efeito 3D
        renderer.setSeriesPaint(0, ACCENT_GREEN);
        renderer.setShadowVisible(false);
        renderer.setMaximumBarWidth(0.12);
        renderer.setDrawBarOutline(false);

        // Eixos
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(TICK_FONT);
        domainAxis.setTickLabelPaint(TEXT_SECONDARY);
        domainAxis.setAxisLinePaint(GRID_COLOR);
        domainAxis.setCategoryMargin(0.3);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabel("Treinos");
        rangeAxis.setTickLabelFont(TICK_FONT);
        rangeAxis.setTickLabelPaint(TEXT_SECONDARY);
        rangeAxis.setLabelFont(AXIS_LABEL_FONT);
        rangeAxis.setLabelPaint(TEXT_SECONDARY);
        rangeAxis.setAxisLinePaint(GRID_COLOR);
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        return toBytes(chart);
    }

    // ==================== GRÁFICO 3: DISTRIBUIÇÃO POR GRUPO MUSCULAR ====================

    /**
     * Gera gráfico de rosca (donut) da distribuição de treinos por grupo muscular.
     *
     * @param distribution mapa de grupo muscular → quantidade
     * @param userName     nome do usuário
     * @return bytes da imagem PNG
     */
    public byte[] generateMuscleGroupChart(Map<String, Integer> distribution, String userName) {
        if (distribution == null || distribution.isEmpty()) {
            return null;
        }

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        distribution.forEach(dataset::setValue);

        JFreeChart chart = ChartFactory.createRingChart(null, dataset, true, false, false);

        chart.setTitle(createTitle("Distribuição de Treinos"));
        chart.addSubtitle(createSubtitle(userName + " — por grupo muscular"));

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
                "{0}: {1} ({2})", new java.text.DecimalFormat("0"), new java.text.DecimalFormat("0%")));
        plot.setSectionDepth(0.45); // Espessura do donut
        plot.setSeparatorsVisible(false);
        plot.setInnerSeparatorExtension(0);
        plot.setOuterSeparatorExtension(0);

        // Aplica paleta de cores
        List<String> keys = distribution.keySet().stream().toList();
        for (int i = 0; i < keys.size(); i++) {
            plot.setSectionPaint(keys.get(i), DONUT_PALETTE[i % DONUT_PALETTE.length]);
        }

        // Legenda
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(BG_DARK);
            legend.setItemFont(LEGEND_FONT);
            legend.setItemPaint(TEXT_PRIMARY);
            legend.setPosition(RectangleEdge.BOTTOM);
        }

        return toBytes(chart);
    }

    // ==================== HELPERS ====================

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
        textTitle.setPadding(TextTitle.DEFAULT_PADDING);
        return textTitle;
    }

    private void applyDarkTheme(JFreeChart chart) {
        chart.setBackgroundPaint(BG_DARK);
        chart.setBorderVisible(false);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);

        // Padding interno do chart
        chart.setPadding(new RectangleInsets(10, 10, 10, 10));
    }

    private byte[] toBytes(JFreeChart chart) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ChartUtils.writeChartAsPNG(out, chart, WIDTH, HEIGHT);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar PNG do gráfico: {}", e.getMessage());
            return null;
        }
    }
}
