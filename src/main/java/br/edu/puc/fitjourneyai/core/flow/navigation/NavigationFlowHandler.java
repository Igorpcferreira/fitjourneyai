package br.edu.puc.fitjourneyai.core.flow.navigation;

import br.edu.puc.fitjourneyai.core.flow.FlowContext;
import br.edu.puc.fitjourneyai.core.flow.FlowHandler;
import br.edu.puc.fitjourneyai.core.flow.FlowResult;
import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;
import br.edu.puc.fitjourneyai.core.model.enums.IntentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fluxo 2 — Navegação e Recuperação.
 * <p>
 * Handler de fallback universal com tom conversacional, caloroso e motivador.
 * Todas as mensagens usam linguagem natural com emojis para simular
 * uma conversa com um assistente inteligente, não um bot robótico.
 */
@Slf4j
@Component
public class NavigationFlowHandler implements FlowHandler {

    @Override
    public ConversationFlowType getFlowType() {
        return ConversationFlowType.NAVIGATION;
    }

    @Override
    public FlowResult handle(FlowContext context) {
        if (!context.user().isOnboardingConcluido()) {
            return FlowResult.text(
                    """
                    Ei, que bom te ver por aqui! \uD83D\uDE04
                    
                    Antes de a gente começar, preciso te conhecer melhor pra personalizar tudo pra você.
                    
                    Manda um /start e em menos de 1 minuto a gente resolve isso! \uD83D\uDE80""",
                    ConversationFlowType.NONE,
                    null,
                    java.util.Map.of(),
                    "Use /start para iniciar o cadastro."
            );
        }

        IntentType intent = context.detectedIntent();

        return switch (intent) {
            case MENU -> handleMenu(context);
            case AJUDA -> handleAjuda(context);
            case CONFIG -> handleConfig(context);
            case CANCELAR -> handleCancelar(context);
            default -> handleUnknown(context);
        };
    }

    private FlowResult handleCancelar(FlowContext context) {
        return FlowResult.done(
                "Fluxo cancelado! \uD83D\uDE09 O que quer fazer agora? Manda /menu pra ver as opções.",
                "Use /menu para ver as opções."
        );
    }

    private FlowResult handleMenu(FlowContext context) {
        String nome = safe(context.user().getNome());
        return FlowResult.done(
                String.format("""
                        Oi, %s! \uD83D\uDCAA Que bom que você tá por aqui!
                        
                        Olha tudo que posso fazer por você:
                        
                        \uD83D\uDCDD /registro — Registrar peso e medidas
                        \u2696\uFE0F /peso — Registro rápido só do peso
                        \uD83C\uDFCB\uFE0F /treino — Pedir um treino personalizado com IA
                        \u2705 /treino_feito — Registrar uma atividade que você fez
                        \uD83D\uDCC8 /progresso — Ver sua evolução com gráficos
                        \uD83D\uDCCA /resumo — Resumo inteligente do período
                        \u2699\uFE0F /config — Ajustar suas preferências
                        \u2753 /ajuda — Entender melhor como funciono
                        
                        Me conta, o que você quer fazer agora? \uD83D\uDE09""", nome),
                "Escolha uma opção ou me diga com suas palavras o que precisa!"
        );
    }

    private FlowResult handleAjuda(FlowContext context) {
        String nome = safe(context.user().getNome());
        return FlowResult.done(
                String.format("""
                        Oi, %s! \uD83D\uDE4B Deixa eu me apresentar melhor!
                        
                        Eu sou o FitJourneyAI, seu parceiro de treinos e evolução física. Fui feito pra te ajudar a manter o foco e acompanhar cada conquista! \uD83C\uDFC6
                        
                        \uD83D\uDCDD Registro de corpo — Anoto seu peso e medidas pra acompanhar a evolução.
                        
                        \uD83E\uDD16 Treinos com IA — Monto treinos personalizados com base no seu objetivo, nível e histórico. Cada exercício vem com link de vídeo pro YouTube!
                        
                        \uD83C\uDFA5 Vídeos de exercícios — Pergunta "como fazer agachamento?" ou "técnica do supino" que eu te mando referência em vídeo. Funciona pra qualquer exercício!
                        
                        \uD83D\uDCC8 Progresso visual — Gero gráficos bonitos dark-mode pra ver o quanto você evoluiu.
                        
                        \uD83D\uDCCA Resumos inteligentes — Analiso seus dados e a IA te dá um feedback sobre como tá indo.
                        
                        \uD83D\uDCAC Conversa livre — Pode me perguntar sobre exercícios, nutrição, descanso, qualquer dúvida fitness!
                        
                        \uD83D\uDD14 Lembretes — Se você sumir, eu mando um lembrete motivacional pra te trazer de volta.
                        
                        \uD83D\uDCA1 Dica: você pode conversar comigo naturalmente! Exemplos:
                        "quero registrar meu peso"
                        "me manda um treino de pernas"
                        "como tá meu progresso?"
                        "como fazer supino reto?"
                        "me mostra vídeo de calistenia"
                        
                        Use /menu pra ver todos os comandos! \uD83D\uDE80""", nome),
                "Me diga o que precisa ou use /menu pra ver as opções!"
        );
    }

    private FlowResult handleConfig(FlowContext context) {
        return FlowResult.done(
                """
                \u2699\uFE0F Suas configurações atuais:
                
                \uD83D\uDD14 Lembretes de inatividade: ativados
                
                Em breve você vai poder personalizar mais coisas por aqui, como frequência de lembretes e estilo das mensagens! \uD83D\uDE09
                
                Use /menu pra voltar ao menu principal.""",
                "Use /menu para ver as opções."
        );
    }

    private FlowResult handleUnknown(FlowContext context) {
        String nome = safe(context.user().getNome());
        return FlowResult.done(
                String.format("""
                        Hmm, %s, não tenho certeza se entendi o que você quis dizer \uD83E\uDD14
                        
                        Mas fica tranquilo! Posso te ajudar com várias coisas:
                        
                        \uD83D\uDCDD /registro — Registrar peso e medidas
                        \uD83C\uDFCB\uFE0F /treino — Treino personalizado com IA
                        \u2705 /treino_feito — Registrar um treino que você fez
                        \uD83D\uDCC8 /progresso — Ver sua evolução
                        \uD83D\uDCCA /resumo — Resumo do período
                        \u2753 /ajuda — Entender como funciono
                        
                        Tenta me contar com mais detalhes ou usa um dos comandos acima! \uD83D\uDE09""", nome),
                "Use /menu ou /ajuda se precisar de orientação!"
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "amigo(a)" : value;
    }
}
