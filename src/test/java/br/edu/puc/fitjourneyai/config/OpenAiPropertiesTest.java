package br.edu.puc.fitjourneyai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPropertiesTest {

    @Test
    @DisplayName("Deve criar properties com todos os campos")
    void deveCriarProperties() {
        OpenAiProperties props = new OpenAiProperties();
        props.setApiKey("sk-test");
        props.setBaseUrl("https://api.openai.com/v1");
        props.setModel("gpt-5.4-mini");
        props.setTimeoutSeconds(30);

        assertThat(props.getApiKey()).isEqualTo("sk-test");
        assertThat(props.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(props.getModel()).isEqualTo("gpt-5.4-mini");
        assertThat(props.getTimeoutSeconds()).isEqualTo(30);
    }
}
