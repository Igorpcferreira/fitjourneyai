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

        if (step == null || step < STEP_CHOOSE_PERSONA) {
            return showConfigMenu(user);
        }

        return switch (step) {
            case STEP_CHOOSE_PERSONA -> handlePersonaChoice(context, user);
            case STEP_CHOOSE_INTENSITY -> handleIntensityChoice(context, user);
            default -> showConfigMenu(user);
        };
    }

    private FlowResult showConfigMenu(User user) {
        String currentPersona = user.getPersona() != null ? user.getPersona().getLabel() : "Coach Amigo";
        String currentIntensity = user.getIntensityLevel() != null ? user.getIntensityLevel().getLabel() : "Moderado";

        return FlowResult.text(
                String.format("""
                        \u2699\uFE0F Configuracoes do FitJourneyAI
                        
                        \uD83C\uDFAD Persona atual: %s
                        \uD83D\uDD25 Intensidade atual: %s
                        
                        Escolha sua persona motivacional:
                        
                        1 - \uD83E\uDD1D Coach Amigo - Seu parceiro de treino
                        2 - \uD83C\uDFDB Filosofo Estoico - Disciplina e sabedoria
                        3 - \uD83C\uDF96 Sargento de Treinamento - Sem desculpas
                        4 - \uD83C\uDFC6 Atleta de Elite - Performance e evolucao
                        5 - \u2694\uFE0F Monge Guerreiro - Corpo e mente
                        6 - \uD83E\uDDEC Cientista do Corpo - Dados e evidencias
                        
                        Manda o numero!""", currentPersona, currentIntensity),
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
                    "Nao entendi. Manda o numero de 1 a 6 pra escolher sua persona!",
                    ConversationFlowType.CONFIG, STEP_CHOOSE_PERSONA, Map.of(), null
            );
        }

        user.setPersona(persona);
        userRepository.save(user);

        log.info("Persona atualizada: user={}, persona={}", user.getId(), persona);

        return FlowResult.text(
                String.format("""
                        \uD83C\uDFAD Persona definida: %s
                        %s
                        
                        Agora escolha o nivel de intensidade:
                        
                        1 - \uD83C\uDF3F Leve - Motivacao gentil e acolhedora
                        2 - \u2696\uFE0F Moderado - Equilibrio entre apoio e cobranca
                        3 - \uD83D\uDD25 Intenso - Cobranca direta e sem desculpas
                        
                        Manda o numero!""", persona.getLabel(), persona.getSubtitle()),
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
                    "Nao entendi. Manda 1 (Leve), 2 (Moderado) ou 3 (Intenso)!",
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
                        \u2705 Configuracao salva!
                        
                        \uD83C\uDFAD Persona: %s
                        \uD83D\uDD25 Intensidade: %s
                        
                        %s
                        
                        A partir de agora, todas as minhas mensagens vao seguir esse estilo!
                        
                        Use /menu pra ver as opcoes ou /treino pra testar sua nova persona!""",
                        persona.getLabel(), intensity.getLabel(), preview),
                "Teste sua persona com /treino ou /peso!"
        );
    }

    /**
     * Gera um preview da combinacao persona + intensidade pra o usuario ver como fica.
     */
    private String generatePreview(PersonaType persona, IntensityLevel intensity) {
        return switch (persona) {
            case ESTOICO -> switch (intensity) {
                case LEVE -> "\uD83D\uDCAC Preview: \"A constancia e a mae de todas as virtudes. Cada treino e um tijolo no templo do seu corpo.\"";
                case MODERADO -> "\uD83D\uDCAC Preview: \"Nao espere motivacao. A disciplina e o que te leva quando a vontade falta. Treine.\"";
                case INTENSO -> "\uD83D\uDCAC Preview: \"O sofrimento e inevitavel. Escolha o sofrimento que te constroi. Levante esse peso.\"";
            };
            case DRILL_SERGEANT -> switch (intensity) {
                case LEVE -> "\uD83D\uDCAC Preview: \"Bom trabalho, soldado. Mas nao relaxe. Amanha tem mais.\"";
                case MODERADO -> "\uD83D\uDCAC Preview: \"Voce veio aqui pra que? Pra ficar olhando? Bota peso nessa barra!\"";
                case INTENSO -> "\uD83D\uDCAC Preview: \"Voce vai desistir? Vai perder pra si mesmo? Enquanto voce descansa, alguem esta treinando pra te ultrapassar.\"";
            };
            case ATLETA -> switch (intensity) {
                case LEVE -> "\uD83D\uDCAC Preview: \"Grandes atletas nao nasceram prontos. Cada treino e uma evolucao. Siga firme.\"";
                case MODERADO -> "\uD83D\uDCAC Preview: \"Performance se constroi nos detalhes. Foco na tecnica, foco na recuperacao. O resultado vem.\"";
                case INTENSO -> "\uD83D\uDCAC Preview: \"Nenhum campeao foi feito nos dias de folga. Ou voce treina como profissional, ou aceita resultados de amador.\"";
            };
            case MONGE_GUERREIRO -> switch (intensity) {
                case LEVE -> "\uD83D\uDCAC Preview: \"A agua que flui e mais forte que a rocha. Seja constante e o resultado virah.\"";
                case MODERADO -> "\uD83D\uDCAC Preview: \"O guerreiro nao busca a briga. Ele busca a preparacao. Treine hoje para o desafio de amanha.\"";
                case INTENSO -> "\uD83D\uDCAC Preview: \"Dor e o mestre que poucos aceitam. Abrace-a. Do outro lado esta a versao que voce quer ser.\"";
            };
            case CIENTISTA -> switch (intensity) {
                case LEVE -> "\uD83D\uDCAC Preview: \"Sabia que 30 minutos de treino liberam endorfina equivalente a um comprimido de bom humor? Seu corpo agradece.\"";
                case MODERADO -> "\uD83D\uDCAC Preview: \"A sintese proteica atinge o pico 24-48h pos-treino. Aproveite essa janela. Treine e alimente-se bem.\"";
                case INTENSO -> "\uD83D\uDCAC Preview: \"Voce perde 1-3% de forca por semana de inatividade. A atrofia nao espera sua motivacao. Os dados nao mentem.\"";
            };
            default -> switch (intensity) {
                case LEVE -> "\uD83D\uDCAC Preview: \"Fala, parceiro! Bora treinar? Cada passo conta nessa jornada!\"";
                case MODERADO -> "\uD83D\uDCAC Preview: \"E ai, bora manter o ritmo? Consistencia e o segredo. Nao precisa ser perfeito, precisa ser constante!\"";
                case INTENSO -> "\uD83D\uDCAC Preview: \"Voce prometeu pra si mesmo. Vai cumprir ou vai inventar desculpa? Bora, sem mimimi!\"";
            };
        };
    }
}
