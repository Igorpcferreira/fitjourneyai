package br.edu.puc.fitjourneyai.service.openai;

import br.edu.puc.fitjourneyai.config.OpenAiProperties;
import br.edu.puc.fitjourneyai.domain.entity.User;
import br.edu.puc.fitjourneyai.dto.openai.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final RestTemplate restTemplate;
    private final OpenAiProperties openAiProperties;

    private String buildChatCompletionsUrl() {
        // Ex: https://api.openai.com/v1/chat/completions
        return openAiProperties.getBaseUrl() + "/chat/completions";
    }

    /**
     * Chamada generica para o endpoint de chat completions.
     */
    private Optional<String> callChatCompletion(List<OpenAiChatMessage> messages,
                                                double temperature,
                                                int maxTokens) {
        OpenAiChatCompletionRequest requestBody = OpenAiChatCompletionRequest.builder()
                .model(openAiProperties.getModel())
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .topP(1.0)
                .stream(false)
                .presencePenalty(0.0)
                .frequencyPenalty(0.0)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiProperties.getApiKey());

        HttpEntity<OpenAiChatCompletionRequest> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<OpenAiChatCompletionResponse> responseEntity =
                    restTemplate.postForEntity(buildChatCompletionsUrl(), entity, OpenAiChatCompletionResponse.class);

            OpenAiChatCompletionResponse body = responseEntity.getBody();
            if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
                log.warn("Resposta da OpenAI sem choices: {}", body);
                return Optional.empty();
            }

            String content = body.getChoices().get(0).getMessage().getContent();
            return Optional.ofNullable(content);
        } catch (RestClientException ex) {
            log.error("Erro ao chamar OpenAI: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    /**
     * Gera um plano de treino personalizado com base no perfil do usuario
     * e em uma solicitacao especifica (por exemplo: "quero um treino de pernas").
     */
    public String generateWorkoutPlan(User user, String userRequest) {
        String userNome = user.getNome() != null ? user.getNome() : "usuario";
        String objetivo = user.getObjetivo() != null ? user.getObjetivo().name() : "NAO_INFORMADO";
        String nivel = user.getNivel() != null ? user.getNivel().name() : "NAO_INFORMADO";
        Integer freq = user.getFrequenciaTreinoEstimada();

        String systemPrompt = """
                Voce e um assistente de treinos chamado FitJourneyAI.
                Seu papel e montar treinos de musculacao e condicionamento fisico claros, seguros e objetivos.
                Sempre responda em portugues do Brasil, em formato de texto simples, usando marcadores e secoes.
                Considere o objetivo, nivel e frequencia semanal do usuario ao propor o treino.
                Evite linguagem tecnica excessiva e nunca recomende nada perigoso.
                """;

        String userPrompt = """
                Dados do usuario:
                - Nome: %s
                - Objetivo: %s
                - Nivel: %s
                - Frequencia semanal estimada de treinos: %s

                Pedido atual do usuario:
                %s

                Monte um treino detalhado, organizado por grupos musculares/dias,
                incluindo series, repeticoes e orientacoes gerais.
                """.formatted(
                userNome,
                objetivo,
                nivel,
                (freq != null ? freq + "x por semana" : "nao informado"),
                (userRequest != null ? userRequest : "")
        );

        List<OpenAiChatMessage> messages = List.of(
                new OpenAiChatMessage("system", systemPrompt),
                new OpenAiChatMessage("user", userPrompt)
        );

        return callChatCompletion(messages, 0.7, 800)
                .orElse("Nao consegui gerar um plano de treino no momento. Tente novamente em instantes.");
    }

    /**
     * Gera uma mensagem motivacional baseada em um resumo textual do progresso do usuario.
     *
     * Exemplo de progressSummary:
     * "Usuario perdeu 2.3 kg nas ultimas 4 semanas e realizou 10 treinos no periodo."
     */
    public String generateMotivationalMessage(User user, String progressSummary) {
        String userNome = user.getNome() != null ? user.getNome() : "usuario";

        String systemPrompt = """
                Voce e um coach motivacional focado em habitos saudaveis e consistencia.
                Sempre responda em portugues do Brasil, de forma empatica, encorajadora
                e realista (sem promessas milagrosas).
                Responda em no maximo 3 paragrafos curtos.
                """;

        String userPrompt = """
                Nome do usuario: %s

                Resumo do progresso:
                %s

                Gere uma mensagem motivacional personalizada, incentivando a continuar,
                reforcando a importancia da consistencia e celebrando as conquistas.
                """.formatted(userNome, (progressSummary != null ? progressSummary : "sem dados de progresso"));

        List<OpenAiChatMessage> messages = List.of(
                new OpenAiChatMessage("system", systemPrompt),
                new OpenAiChatMessage("user", userPrompt)
        );

        return callChatCompletion(messages, 0.85, 400)
                .orElse("Voce esta indo bem, mesmo que nem sempre pareca. O importante e continuar aparecendo por voce mesmo todos os dias. 💪");
    }

    /**
     * Opcional: classifica a intencao do usuario (ex: ONBOARDING, REGISTRO_PESO, TREINO, PROGRESSO, AJUDA, DESCONHECIDO).
     * Isso pode ajudar o MessageOrchestrator a decidir qual fluxo chamar.
     */
    public String classifyIntentIfNeeded(String textoUsuario) {
        if (textoUsuario == null || textoUsuario.isBlank()) {
            return "DESCONHECIDO";
        }

        String systemPrompt = """
                Voce deve atuar como um classificador de intencao para um bot de fitness.
                O usuario conversa em portugues.
                Sua tarefa e analisar a mensagem e responder SOMENTE com um dos seguintes rótulos (em MAIUSCULAS):

                - ONBOARDING
                - REGISTRO_PESO
                - REGISTRO_MEDIDAS
                - REGISTRO_TREINO
                - VER_PROGRESO
                - VER_RESUMO
                - GERAR_TREINO
                - AJUDA
                - DESCONHECIDO

                Nao explique, nao use frases completas, retorne apenas o rotulo.
                """;

        String userPrompt = "Mensagem do usuario: \"%s\"".formatted(textoUsuario);

        List<OpenAiChatMessage> messages = List.of(
                new OpenAiChatMessage("system", systemPrompt),
                new OpenAiChatMessage("user", userPrompt)
        );

        String result = callChatCompletion(messages, 0.0, 20)
                .orElse("DESCONHECIDO");

        // so pra garantir que nao venha com espacos/linhas
        return result.trim().toUpperCase();
    }
}
