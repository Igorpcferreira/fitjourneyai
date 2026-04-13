package br.edu.puc.fitjourneyai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramPropertiesTest {

    @Test
    @DisplayName("Deve criar properties com getters e setters")
    void deveCriarProperties() {
        TelegramProperties props = new TelegramProperties();
        props.setBotToken("test-token");
        props.setBaseUrl("https://api.telegram.org");
        props.setWebhookPath("/telegram/webhook");

        assertThat(props.getBotToken()).isEqualTo("test-token");
        assertThat(props.getBaseUrl()).isEqualTo("https://api.telegram.org");
        assertThat(props.getWebhookPath()).isEqualTo("/telegram/webhook");
    }
}
