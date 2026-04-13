package br.edu.puc.fitjourneyai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração para integração com a API da OpenAI.
 */
@Data
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    /** Chave de API da OpenAI. */
    private String apiKey;

    /** URL base da API. Padrão: https://api.openai.com/v1 */
    private String baseUrl;

    /** Modelo principal. Ex: gpt-4.1-mini */
    private String model;

    /** Timeout em segundos para chamadas à API. Padrão: 20 */
    private int timeoutSeconds = 20;
}
