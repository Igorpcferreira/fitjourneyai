package br.edu.puc.fitjourneyai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    /**
     * Token do bot configurado no BotFather.
     */
    private String botToken;

    /**
     * URL base da API do Telegram.
     * Exemplo: https://api.telegram.org
     */
    private String baseUrl;

    /**
     * Caminho do webhook da aplicacao.
     * Exemplo: /telegram/webhook
     */
    private String webhookPath;
}
