package br.edu.puc.fitjourneyai.adapter.openai;

import br.edu.puc.fitjourneyai.adapter.openai.dto.OpenAiDtos;
import br.edu.puc.fitjourneyai.adapter.openai.dto.OpenAiDtos.*;
import br.edu.puc.fitjourneyai.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Gateway HTTP para a API da OpenAI.
 * Centraliza chamadas ao endpoint /v1/chat/completions com tratamento de erros.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiGateway {

    private final RestTemplate restTemplate;
    private final OpenAiProperties properties;

    /**
     * Faz uma chamada ao endpoint de chat completions.
     *
     * @param messages    lista de mensagens (system + user)
     * @param temperature criatividade (0.0–1.0)
     * @param maxTokens   limite de tokens na resposta
     * @return conteúdo da resposta, ou empty se falhar
     */
    public Optional<String> chatCompletion(List<ChatMessage> messages,
                                           double temperature,
                                           int maxTokens) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(properties.getModel())
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        String url = properties.getBaseUrl() + "/chat/completions";

        try {
            ResponseEntity<ChatCompletionResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, headers), ChatCompletionResponse.class);

            ChatCompletionResponse body = response.getBody();
            if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
                log.warn("Resposta da OpenAI sem choices");
                return Optional.empty();
            }

            Choice choice = body.getChoices().get(0);
            if ("length".equalsIgnoreCase(choice.getFinishReason())) {
                log.warn("OpenAI interrompeu a resposta por limite de tokens; descartando conteúdo truncado");
                return Optional.empty();
            }

            String content = choice.getMessage().getContent();

            if (body.getUsage() != null) {
                log.debug("OpenAI usage: prompt={}, completion={}, total={}",
                        body.getUsage().getPromptTokens(),
                        body.getUsage().getCompletionTokens(),
                        body.getUsage().getTotalTokens());
            }

            return Optional.ofNullable(content);

        } catch (RestClientException ex) {
            log.error("Erro ao chamar OpenAI: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
