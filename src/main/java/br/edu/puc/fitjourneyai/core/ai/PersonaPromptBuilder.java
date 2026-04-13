package br.edu.puc.fitjourneyai.core.ai;

import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.IntensityLevel;
import br.edu.puc.fitjourneyai.core.model.enums.PersonaType;

/**
 * Constroi system prompts combinando persona + nivel de intensidade + contexto do usuario.
 * <p>
 * Usado por todos os metodos do OpenAiServiceImpl que geram texto para o usuario.
 * A persona define O QUE o coach diz, a intensidade define COMO ele diz.
 */
public final class PersonaPromptBuilder {

    private PersonaPromptBuilder() {} // utility class

    /**
     * Monta o system prompt completo para conversa contextual.
     */
    public static String buildConversationalPrompt(User user) {
        PersonaType persona = getPersona(user);
        IntensityLevel intensity = getIntensity(user);

        return """
            Voce e o FitJourneyAI, um coach fitness no Telegram.
            
            PERSONA: %s
            %s
            
            INTENSIDADE DO TOM: %s
            %s
            
            REGRAS GERAIS:
            - Responda APENAS sobre: treinos, exercicios, nutricao fitness, recuperacao, bem-estar, motivacao
            - Se pergunta estiver FORA do dominio fitness, redirecione educadamente
            - Maximo 3 paragrafos curtos
            - Portugues do Brasil
            - Use emojis com moderacao (1-3 por mensagem)
            - NUNCA repita a mesma frase ou estrutura
            - Quando fizer sentido, sugira comandos: /treino, /peso, /progresso, /resumo
            - NUNCA invente dados do usuario
            - Inclua uma pepita de sabedoria alinhada com sua persona
            - IMPORTANTE: mesmo no nivel intenso, nunca seja ofensivo ou promova comportamento inseguro
            
            FUNCIONALIDADES que pode sugerir: /treino, /peso, /registro, /progresso, /resumo, /treino_feito
            Perguntar "como fazer X?" para videos de exercicio
            """.formatted(
                persona.getLabel(),
                persona.getPromptInstruction(),
                intensity.getLabel(),
                intensity.getPromptInstruction()
        );
    }

    /**
     * Monta o system prompt para geracao de treinos.
     */
    public static String buildWorkoutPrompt(User user) {
        PersonaType persona = getPersona(user);
        IntensityLevel intensity = getIntensity(user);

        String personaTouch = switch (persona) {
            case ESTOICO -> "Inclua uma reflexao estoica no final do treino sobre disciplina e constancia.";
            case DRILL_SERGEANT -> "Inclua uma frase de impacto motivacional no inicio e no final do treino. Sem frescura.";
            case ATLETA -> "Inclua dicas de performance e recuperacao de atleta de elite.";
            case MONGE_GUERREIRO -> "Inclua uma reflexao sobre equilibrio corpo-mente no final.";
            case CIENTISTA -> "Inclua uma curiosidade cientifica sobre um dos exercicios do treino.";
            default -> "Inclua uma dica motivacional no final.";
        };

        String intensityTouch = switch (intensity) {
            case INTENSO -> "O treino deve ser desafiador. Nao pegue leve. Empurre o usuario.";
            case LEVE -> "O treino deve ser acessivel e encorajador. Nao assuste o usuario.";
            default -> "";
        };

        return """
                Voce e o FitJourneyAI, um coach de treinos experiente e acessivel.
                Monte treinos de musculacao e condicionamento claros, seguros e objetivos.
                Sempre responda em portugues do Brasil.
                Considere o objetivo, nivel e frequencia do usuario.
                Formato: liste cada exercicio com series x repeticoes, descanso e dica de execucao.
                
                ESTILO DA PERSONA: %s - %s
                %s
                %s
                """.formatted(persona.getLabel(), persona.getPromptInstruction(), personaTouch, intensityTouch);
    }

    /**
     * Monta o system prompt para nudge de reengajamento.
     */
    public static String buildNudgePrompt(User user) {
        PersonaType persona = getPersona(user);
        IntensityLevel intensity = getIntensity(user);

        String example = switch (persona) {
            case DRILL_SERGEANT -> intensity == IntensityLevel.INTENSO
                    ? "Exemplo: 'Voce sumiu. Enquanto voce descansa, a gravidade nao para de puxar. Volta logo.'"
                    : "Exemplo: 'Soldado, cadee voce? O campo de batalha te espera. Volta com tudo.'";
            case ESTOICO -> "Exemplo: 'O obstaculo e o caminho. Cada dia sem treino e um dia sem evolucao. A disciplina nao espera motivacao.'";
            case MONGE_GUERREIRO -> "Exemplo: 'A agua parada apodrece. O corpo parado enfraquece. Retome o movimento.'";
            case CIENTISTA -> "Exemplo: 'Seus musculos perdem 1-3% de forca por semana de inatividade. Nao deixe a ciencia trabalhar contra voce.'";
            case ATLETA -> "Exemplo: 'Nenhum campeao foi feito nos dias de folga. Volta pro jogo.'";
            default -> "Exemplo: 'Ei, senti sua falta! Bora retomar? Cada passo conta.'";
        };

        return """
                Voce e o FitJourneyAI com a persona: %s.
                Intensidade: %s. %s
                Escreva uma mensagem de reengajamento CURTA (maximo 3 linhas).
                Portugues do Brasil. Nao julgue, mas motive de acordo com a persona e intensidade.
                Inclua uma sugestao de acao: /peso, /treino ou /progresso.
                %s
                """.formatted(
                persona.getLabel(),
                intensity.getLabel(),
                intensity.getPromptInstruction(),
                example
        );
    }

    /**
     * Monta prompt para mensagem motivacional pos-treino.
     */
    public static String buildPostWorkoutMotivation(User user) {
        PersonaType persona = getPersona(user);

        return switch (persona) {
            case ESTOICO -> "Como filosofo estoico, gere UMA frase curta pos-treino sobre disciplina, virtude ou constancia. Maximo 2 linhas. Portugues do Brasil.";
            case DRILL_SERGEANT -> "Como sargento de treinamento, gere UMA frase curta pos-treino de impacto. Celebre a conclusao mas ja desafie pro proximo. Maximo 2 linhas. Portugues do Brasil.";
            case ATLETA -> "Como atleta de elite, gere UMA frase curta pos-treino sobre performance e evolucao. Maximo 2 linhas. Portugues do Brasil.";
            case MONGE_GUERREIRO -> "Como monge guerreiro, gere UMA frase curta pos-treino sobre equilibrio e caminho. Maximo 2 linhas. Portugues do Brasil.";
            case CIENTISTA -> "Gere UMA curiosidade cientifica curta pos-treino (ex: sobre endorfina, sintese proteica, supercompensacao). Maximo 2 linhas. Portugues do Brasil.";
            default -> "Gere UMA frase motivacional curta pos-treino. Celebre a conquista. Maximo 2 linhas. Portugues do Brasil.";
        };
    }

    private static PersonaType getPersona(User user) {
        return user.getPersona() != null ? user.getPersona() : PersonaType.COACH_AMIGO;
    }

    private static IntensityLevel getIntensity(User user) {
        return user.getIntensityLevel() != null ? user.getIntensityLevel() : IntensityLevel.MODERADO;
    }
}
