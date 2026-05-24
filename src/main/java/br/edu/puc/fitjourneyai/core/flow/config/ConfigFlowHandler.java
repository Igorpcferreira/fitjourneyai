package br.edu.puc.fitjourneyai.core.flow.config;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.entity.User;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntensityLevel;
import br.edu.puc.fitjourneyai.core.model.enums.PersonaType;
import br.edu.puc.fitjourneyai.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fluxo de configuracao de persona e intensidade motivacional.
 * <p>
 * Steps:
 * 1 - Escolher persona
 * 2 - Escolher intensidade
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigFlowHandler implements FlowHandler {

    private static final int STEP_CHOOSE_PERSONA = 1;
    private static final int STEP_CHOOSE_INTENSITY = 2;

    private final UserRepository userRepository;

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.CONFIG;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        User user = context.user();
        Integer step = context.state().getCurrentStep();
        String text = context.rawText();

        if (isConfigCommand(text) || step == null || step < STEP_CHOOSE_PERSONA) {
            return showConfigMenu(user);
        }

        return switch (step) {
            case STEP_CHOOSE_PERSONA -> handlePersonaChoice(context, user);
            case STEP_CHOOSE_INTENSITY -> handleIntensityChoice(context, user);
            default -> showConfigMenu(user);
        };
    }

    private boolean isConfigCommand(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim().toLowerCase();
        return trimmed.equals("/config") || trimmed.startsWith("/config@");
    }

    private FlowResult showConfigMenu(User user) {
        String currentPersona = user.getPersona() != null ? user.getPersona().getLabel() : "Coach Amigo";
        String currentIntensity = user.getIntensityLevel() != null ? user.getIntensityLevel().getLabel() : "Moderado";

        return FlowResult.text(
                String.format("""
                        ⚙️ Configurações do FitJourneyAI
                        
                        🎭 Persona atual: %s
                        🔥 Intensidade atual: %s
                        
                        Escolha sua persona motivacional:
                        
                        1 - 🤝 Coach Amigo - Seu parceiro de treino
                        2 - 🏛 Filósofo Estoico - Disciplina e sabedoria
                        3 - 🎖 Sargento de Treinamento - Sem desculpas
                        4 - 🏆 Atleta de Elite - Performance e evolução
                        5 - ⚔️ Monge Guerreiro - Corpo e mente
                        6 - 🧬 Cientista do Corpo - Dados e evidências
                        
                        Manda o número!""", currentPersona, currentIntensity),
                ConversationFlowType.CONFIG,
                STEP_CHOOSE_PERSONA,
                Map.of(),
                null
        );
    }

    private FlowResult handlePersonaChoice(FlowContext context, User user) {
        PersonaType persona = PersonaType.fromUserInput(context.rawText());

        if (persona == null) {
            return FlowResult.text(
                    "Não entendi. Manda o número de 1 a 6 para escolher sua persona!",
                    ConversationFlowType.CONFIG, STEP_CHOOSE_PERSONA, Map.of(), null
            );
        }

        user.setPersona(persona);
        userRepository.save(user);

        log.info("Persona atualizada: user={}, persona={}", user.getId(), persona);

        return FlowResult.text(
                String.format("""
                        🎭 Persona definida: %s
                        %s
                        
                        Agora escolha o nível de intensidade:
                        
                        1 - 🌿 Leve - Motivação gentil e acolhedora
                        2 - ⚖️ Moderado - Equilíbrio entre apoio e cobrança
                        3 - 🔥 Intenso - Cobrança direta e sem desculpas
                        
                        Manda o número!""", persona.getLabel(), persona.getSubtitle()),
                ConversationFlowType.CONFIG,
                STEP_CHOOSE_INTENSITY,
                Map.of("persona", persona.name()),
                null
        );
    }

    private FlowResult handleIntensityChoice(FlowContext context, User user) {
        IntensityLevel intensity = IntensityLevel.fromUserInput(context.rawText());

        if (intensity == null) {
            return FlowResult.text(
                    "Não entendi. Manda 1 (Leve), 2 (Moderado) ou 3 (Intenso)!",
                    ConversationFlowType.CONFIG, STEP_CHOOSE_INTENSITY, Map.of(), null
            );
        }

        user.setIntensityLevel(intensity);
        userRepository.save(user);

        PersonaType persona = user.getPersona() != null ? user.getPersona() : PersonaType.COACH_AMIGO;

        log.info("Intensidade atualizada: user={}, intensity={}", user.getId(), intensity);

        // Gera preview da persona configurada
        String preview = generatePreview(persona, intensity);

        return FlowResult.done(
                String.format("""
                        ✅ Configuração salva!
                        
                        🎭 Persona: %s
                        🔥 Intensidade: %s
                        
                        %s
                        
                        A partir de agora, todas as minhas mensagens vão seguir esse estilo!
                        
                        Use /menu para ver as opções ou /treino para testar sua nova persona!""",
                        persona.getLabel(), intensity.getLabel(), preview),
                "Teste sua persona com /treino ou /peso!"
        );
    }

    /**
     * Gera um preview da combinação persona + intensidade para o usuário ver como fica.
     */
    private String generatePreview(PersonaType persona, IntensityLevel intensity) {
        return switch (persona) {
            case ESTOICO -> switch (intensity) {
                case LEVE -> "💬 Preview: \"A constância é a mãe de todas as virtudes. Cada treino é um tijolo no templo do seu corpo.\"";
                case MODERADO -> "💬 Preview: \"Não espere motivação. A disciplina é o que te leva quando a vontade falta. Treine.\"";
                case INTENSO -> "💬 Preview: \"O sofrimento é inevitável. Escolha o sofrimento que te constrói. Levante esse peso.\"";
            };
            case DRILL_SERGEANT -> switch (intensity) {
                case LEVE -> "💬 Preview: \"Bom trabalho, soldado. Mas não relaxe. Amanhã tem mais.\"";
                case MODERADO -> "💬 Preview: \"Você veio aqui para quê? Para ficar olhando? Bota peso nessa barra!\"";
                case INTENSO -> "💬 Preview: \"Você vai desistir? Vai perder para si mesmo? Enquanto você descansa, alguém está treinando para te ultrapassar.\"";
            };
            case ATLETA -> switch (intensity) {
                case LEVE -> "💬 Preview: \"Grandes atletas não nasceram prontos. Cada treino é uma evolução. Siga firme.\"";
                case MODERADO -> "💬 Preview: \"Performance se constrói nos detalhes. Foco na técnica, foco na recuperação. O resultado vem.\"";
                case INTENSO -> "💬 Preview: \"Nenhum campeão foi feito nos dias de folga. Ou você treina como profissional, ou aceita resultados de amador.\"";
            };
            case MONGE_GUERREIRO -> switch (intensity) {
                case LEVE -> "💬 Preview: \"A água que flui é mais forte que a rocha. Seja constante e o resultado virá.\"";
                case MODERADO -> "💬 Preview: \"O guerreiro não busca a briga. Ele busca a preparação. Treine hoje para o desafio de amanhã.\"";
                case INTENSO -> "💬 Preview: \"Dor é o mestre que poucos aceitam. Abrace-a. Do outro lado está a versão que você quer ser.\"";
            };
            case CIENTISTA -> switch (intensity) {
                case LEVE -> "💬 Preview: \"Sabia que 30 minutos de treino liberam endorfina equivalente a um comprimido de bom humor? Seu corpo agradece.\"";
                case MODERADO -> "💬 Preview: \"A síntese proteica atinge o pico 24-48h pós-treino. Aproveite essa janela. Treine e alimente-se bem.\"";
                case INTENSO -> "💬 Preview: \"Você perde 1-3% de força por semana de inatividade. A atrofia não espera sua motivação. Os dados não mentem.\"";
            };
            default -> switch (intensity) {
                case LEVE -> "💬 Preview: \"Fala, parceiro! Bora treinar? Cada passo conta nessa jornada!\"";
                case MODERADO -> "💬 Preview: \"E aí, bora manter o ritmo? Consistência é o segredo. Não precisa ser perfeito, precisa ser constante!\"";
                case INTENSO -> "💬 Preview: \"Você prometeu para si mesmo. Vai cumprir ou vai inventar desculpa? Bora, sem mimimi!\"";
            };
        };
    }
}
