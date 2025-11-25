package br.edu.puc.fitjourneyai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    /**
     * Chave de API da OpenAI.
     */
    private String apiKey;

    /**
     * URL base da API da OpenAI.
     * Exemplo: https://api.openai.com/v1
     */
    private String baseUrl;

    /**
     * Modelo principal usado para gerar treinos/mensagens.
     * Exemplo: gpt-4.1-mini
     */
    private String model;
}
