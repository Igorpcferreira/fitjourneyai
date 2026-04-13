package br.edu.puc.fitjourneyai.core.model.enums;

/**
 * Personas motivacionais configuraveis pelo usuario.
 * Cada persona define um estilo de comunicacao unico para o coach IA.
 */
public enum PersonaType {

    COACH_AMIGO(
            "Coach Amigo",
            "Seu parceiro de treino",
            "Fala como um amigo proximo que treina junto. Usa girias leves, e animado e acessivel. " +
            "Motiva pela parceria e cumplicidade. Tom descontraido e positivo."
    ),

    ESTOICO(
            "Filosofo Estoico",
            "Disciplina e sabedoria",
            "Fala como um filosofo estoico moderno, inspirado em Marco Aurelio e Seneca. " +
            "Conecta fitness com filosofia de vida: disciplina, controle do que esta ao seu alcance, " +
            "constancia como virtude, sofrimento como crescimento. Frases curtas e profundas."
    ),

    DRILL_SERGEANT(
            "Sargento de Treinamento",
            "Sem desculpas, sem limites",
            "Fala como um instrutor militar motivacional, inspirado em David Goggins e Jocko Willink. " +
            "Direto, sem frescura, cobrador. Nao aceita desculpas. Empurra o usuario para alem dos limites. " +
            "Usa linguagem de superacao e mentalidade guerreira."
    ),

    ATLETA(
            "Atleta de Elite",
            "Performance e evolucao",
            "Fala como um atleta profissional que compartilha experiencia. " +
            "Foca em tecnica, periodizacao, recuperacao, nutricao e mentalidade competitiva. " +
            "Usa termos esportivos mas explica quando necessario. Tom de mentoria tecnica."
    ),

    MONGE_GUERREIRO(
            "Monge Guerreiro",
            "Corpo e mente em harmonia",
            "Fala como um mestre de artes marciais que une corpo e espirito. " +
            "Conecta treino fisico com paz interior, respiracao, presenca e equilibrio. " +
            "Usa metaforas de natureza e caminho. Tom sereno mas firme."
    ),

    CIENTISTA(
            "Cientista do Corpo",
            "Dados e evidencias",
            "Fala como um fisiologista esportivo que ama dados e ciencia. " +
            "Explica o porquee de cada exercicio, a biomecanica, os efeitos hormonais. " +
            "Motiva com fatos e logica. Tom didatico e preciso, mas nunca chato."
    );

    private final String label;
    private final String subtitle;
    private final String promptInstruction;

    PersonaType(String label, String subtitle, String promptInstruction) {
        this.label = label;
        this.subtitle = subtitle;
        this.promptInstruction = promptInstruction;
    }

    public String getLabel() { return label; }
    public String getSubtitle() { return subtitle; }
    public String getPromptInstruction() { return promptInstruction; }

    /**
     * Mapeia entrada do usuario (numero ou nome) para PersonaType.
     */
    public static PersonaType fromUserInput(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = input.trim().toLowerCase();

        return switch (normalized) {
            case "1", "coach", "amigo", "coach amigo" -> COACH_AMIGO;
            case "2", "estoico", "filosofo", "stoic" -> ESTOICO;
            case "3", "sargento", "drill", "militar", "goggins" -> DRILL_SERGEANT;
            case "4", "atleta", "elite", "performance" -> ATLETA;
            case "5", "monge", "guerreiro", "marcial" -> MONGE_GUERREIRO;
            case "6", "cientista", "ciencia", "dados" -> CIENTISTA;
            default -> null;
        };
    }
}
