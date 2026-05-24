package br.edu.puc.fitjourneyai.infrastructure.chart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProgressChartService")
class ProgressChartServiceTest {

    private ProgressChartService service;

    @BeforeEach
    void setUp() {
        service = new ProgressChartService();
    }

    // ===================== generateWeightChart =====================

    @Test
    @DisplayName("Deve retornar null quando dataPoints é null")
    void weightChart_retornaNullParaNull() {
        assertThat(service.generateWeightChart(null, "Igor")).isNull();
    }

    @Test
    @DisplayName("Deve retornar null quando dataPoints está vazio")
    void weightChart_retornaNullParaVazio() {
        assertThat(service.generateWeightChart(Map.of(), "Igor")).isNull();
    }

    @Test
    @DisplayName("Deve gerar PNG com um ponto de peso")
    void weightChart_geraComUmPonto() {
        Map<LocalDate, Double> data = Map.of(LocalDate.now(), 75.0);
        byte[] result = service.generateWeightChart(data, "Igor");
        assertThat(result).isNotNull().isNotEmpty();
        // PNG começa com 0x89 0x50 0x4E 0x47
        assertThat(result[0]).isEqualTo((byte) 0x89);
        assertThat(result[1]).isEqualTo((byte) 0x50);
    }

    @Test
    @DisplayName("Deve gerar PNG com múltiplos pontos de peso")
    void weightChart_geraComMultiplosPontos() {
        Map<LocalDate, Double> data = new LinkedHashMap<>();
        LocalDate base = LocalDate.now().minusDays(10);
        for (int i = 0; i < 10; i++) {
            data.put(base.plusDays(i), 70.0 + i * 0.3);
        }
        byte[] result = service.generateWeightChart(data, "Maria");
        assertThat(result).isNotNull().hasSizeGreaterThan(100);
    }

    @Test
    @DisplayName("Deve gerar peso com margem mínima quando variação < 1kg")
    void weightChart_geraComVariacaoMinima() {
        Map<LocalDate, Double> data = new LinkedHashMap<>();
        data.put(LocalDate.now().minusDays(1), 70.0);
        data.put(LocalDate.now(), 70.1);
        byte[] result = service.generateWeightChart(data, "Igor");
        assertThat(result).isNotNull().isNotEmpty();
    }

    // ===================== generateTrainingFrequencyChart =====================

    @Test
    @DisplayName("Deve retornar null quando weeklyData é null")
    void frequencyChart_retornaNullParaNull() {
        assertThat(service.generateTrainingFrequencyChart(null, "Igor")).isNull();
    }

    @Test
    @DisplayName("Deve retornar null quando weeklyData está vazio")
    void frequencyChart_retornaNullParaVazio() {
        assertThat(service.generateTrainingFrequencyChart(Map.of(), "Igor")).isNull();
    }

    @Test
    @DisplayName("Deve gerar PNG de frequência com uma semana")
    void frequencyChart_geraComUmaSemana() {
        byte[] result = service.generateTrainingFrequencyChart(Map.of("Semana 1", 3), "Igor");
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Deve gerar PNG de frequência com múltiplas semanas")
    void frequencyChart_geraComMultiplasSemanas() {
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("Semana 1", 4);
        data.put("Semana 2", 3);
        data.put("Semana 3", 5);
        data.put("Semana 4", 2);
        byte[] result = service.generateTrainingFrequencyChart(data, "Igor");
        assertThat(result).isNotNull().hasSizeGreaterThan(100);
    }

    // ===================== generateMuscleGroupChart =====================

    @Test
    @DisplayName("Deve retornar null quando distribution é null")
    void muscleChart_retornaNullParaNull() {
        assertThat(service.generateMuscleGroupChart(null, "Igor")).isNull();
    }

    @Test
    @DisplayName("Deve retornar null quando distribution está vazio")
    void muscleChart_retornaNullParaVazio() {
        assertThat(service.generateMuscleGroupChart(Map.of(), "Igor")).isNull();
    }

    @Test
    @DisplayName("Deve gerar PNG de distribuição muscular com um grupo")
    void muscleChart_geraComUmGrupo() {
        byte[] result = service.generateMuscleGroupChart(Map.of("Peito", 5), "Igor");
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Deve gerar PNG de distribuição com múltiplos grupos")
    void muscleChart_geraComMultiplosGrupos() {
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("Peito", 8);
        data.put("Costas", 6);
        data.put("Pernas", 10);
        data.put("Ombro", 4);
        data.put("Braços", 3);
        byte[] result = service.generateMuscleGroupChart(data, "Maria");
        assertThat(result).isNotNull().hasSizeGreaterThan(100);
    }

    @Test
    @DisplayName("Deve usar todos os 10 grupos na paleta de cores")
    void muscleChart_geraComDezGrupos() {
        Map<String, Integer> data = new LinkedHashMap<>();
        for (int i = 1; i <= 11; i++) {
            data.put("Grupo" + i, i);
        }
        byte[] result = service.generateMuscleGroupChart(data, "Igor");
        assertThat(result).isNotNull().isNotEmpty();
    }
}

