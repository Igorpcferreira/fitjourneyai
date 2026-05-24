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
                    Ei, que bom te ver por aqui! 😄
                    
                    Antes de a gente começar, preciso te conhecer melhor para personalizar tudo para você.
                    
                    Manda um /start e, em menos de 1 minuto, a gente resolve isso! 🚀""",
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
                "Fluxo cancelado! 😉 O que quer fazer agora? Manda /menu para ver as opções.",
                "Use /menu para ver as opções."
        );
    }

    private FlowResult handleMenu(FlowContext context) {
        String nome = safe(context.user().getNome());
        return FlowResult.done(
                String.format("""
                        Oi, %s! 💪 Que bom que você está por aqui!
                        
                        Olha tudo que posso fazer por você:
                        
                        📝 /registro — Registrar peso e medidas
                        ⚖️ /peso — Registro rápido só do peso
                        🏋️ /treino — Pedir um treino personalizado com IA
                        ✅ /treino_feito — Registrar uma atividade que você fez
                        📈 /progresso — Ver sua evolução com gráficos
                        📊 /resumo — Resumo inteligente do período
                        ⚙️ /config — Ajustar suas preferências
                        ❓ /ajuda — Entender melhor como funciono
                        
                        Me conta, o que você quer fazer agora? 😉""", nome),
                "Escolha uma opção ou me diga com suas palavras o que precisa!"
        );
    }

    private FlowResult handleAjuda(FlowContext context) {
        String nome = safe(context.user().getNome());
        return FlowResult.done(
                String.format("""
                        Oi, %s! 🙋 Deixa eu me apresentar melhor!
                        
                        Eu sou o FitJourneyAI, seu parceiro de treinos e evolução física. Fui feito para te ajudar a manter o foco e acompanhar cada conquista! 🏆
                        
                        📝 Registro de corpo — Anoto seu peso e medidas para acompanhar a evolução.
                        
                        🤖 Treinos com IA — Monto treinos personalizados com base no seu objetivo, nível e histórico. Cada exercício vem com link de vídeo para o YouTube!
                        
                        🎥 Vídeos de exercícios — Pergunta "como fazer agachamento?" ou "técnica do supino" que eu te mando referência em vídeo. Funciona para qualquer exercício!
                        
                        📈 Progresso visual — Gero gráficos bonitos dark-mode para ver o quanto você evoluiu.
                        
                        📊 Resumos inteligentes — Analiso seus dados e a IA te dá um feedback sobre como está indo.
                        
                        💬 Conversa livre — Pode me perguntar sobre exercícios, nutrição, descanso, qualquer dúvida fitness!
                        
                        🔔 Lembretes — Se você sumir, eu mando um lembrete motivacional para te trazer de volta.
                        
                        💡 Dica: você pode conversar comigo naturalmente! Exemplos:
                        "quero registrar meu peso"
                        "me manda um treino de pernas"
                        "como está meu progresso?"
                        "como fazer supino reto?"
                        "me mostra vídeo de calistenia"
                        
                        Use /menu para ver todos os comandos! 🚀""", nome),
                "Me diga o que precisa ou use /menu para ver as opções!"
        );
    }

    private FlowResult handleConfig(FlowContext context) {
        return FlowResult.done(
                """
                ⚙️ Suas configurações atuais:
                
                🔔 Lembretes de inatividade: ativados
                
                Em breve você vai poder personalizar mais coisas por aqui, como frequência de lembretes e estilo das mensagens! 😉
                
                Use /menu para voltar ao menu principal.""",
                "Use /menu para ver as opções."
        );
    }

    private FlowResult handleUnknown(FlowContext context) {
        String nome = safe(context.user().getNome());
        return FlowResult.done(
                String.format("""
                        Hmm, %s, não tenho certeza se entendi o que você quis dizer 🤔
                        
                        Mas fica tranquilo! Posso te ajudar com várias coisas:
                        
                        📝 /registro — Registrar peso e medidas
                        🏋️ /treino — Treino personalizado com IA
                        ✅ /treino_feito — Registrar um treino que você fez
                        📈 /progresso — Ver sua evolução
                        📊 /resumo — Resumo do período
                        ❓ /ajuda — Entender como funciono
                        
                        Tenta me contar com mais detalhes ou usa um dos comandos acima! 😉""", nome),
                "Use /menu ou /ajuda se precisar de orientação!"
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "amigo(a)" : value;
    }
}
