package br.edu.puc.fitjourneyai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientConfigTest {

    @Test
    @DisplayName("Deve criar RestTemplate bean")
    void deveCriarRestTemplate() {
        HttpClientConfig config = new HttpClientConfig();
        RestTemplate restTemplate = config.restTemplate();
        assertThat(restTemplate).isNotNull();
    }
}
