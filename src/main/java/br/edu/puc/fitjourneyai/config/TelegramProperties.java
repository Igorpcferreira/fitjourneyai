package br.edu.puc.fitjourneyai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração para integração com a Telegram Bot API.
 */
@Data
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    /** Token do bot configurado no BotFather. */
    private String botToken;

    /** URL base da API do Telegram. Padrão: https://api.telegram.org */
    private String baseUrl;

    /** Path do webhook que recebe updates. Padrão: /telegram/webhook */
    private String webhookPath;
}
