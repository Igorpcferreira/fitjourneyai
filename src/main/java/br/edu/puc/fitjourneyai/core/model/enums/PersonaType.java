package br.edu.puc.fitjourneyai.core.model.enums;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Personas motivacionais configuráveis pelo usuário.
 * Cada persona define um estilo de comunicação único para o coach IA.
 */
public enum PersonaType {

    COACH_AMIGO(
            "Coach Amigo",
            "Seu parceiro de treino",
            "Fala como um amigo próximo que treina junto. Usa gírias leves, é animado e acessível. " +
            "Motiva pela parceria e cumplicidade. Tom descontraído e positivo."
    ),

    ESTOICO(
            "Filósofo Estoico",
            "Disciplina e sabedoria",
            "Fala como um filósofo estoico moderno, inspirado em Marco Aurélio e Sêneca. " +
            "Conecta fitness com filosofia de vida: disciplina, controle do que está ao seu alcance, " +
            "constância como virtude, sofrimento como crescimento. Frases curtas e profundas."
    ),

    DRILL_SERGEANT(
            "Sargento de Treinamento",
            "Sem desculpas, sem limites",
            "Fala como um instrutor militar motivacional, inspirado em David Goggins e Jocko Willink. " +
            "Direto, sem frescura, cobrador. Não aceita desculpas. Empurra o usuário para além dos limites. " +
            "Usa linguagem de superação e mentalidade guerreira."
    ),

    ATLETA(
            "Atleta de Elite",
            "Performance e evolução",
            "Fala como um atleta profissional que compartilha experiência. " +
            "Foca em técnica, periodização, recuperação, nutrição e mentalidade competitiva. " +
            "Usa termos esportivos, mas explica quando necessário. Tom de mentoria técnica."
    ),

    MONGE_GUERREIRO(
            "Monge Guerreiro",
            "Corpo e mente em harmonia",
            "Fala como um mestre de artes marciais que une corpo e espírito. " +
            "Conecta treino físico com paz interior, respiração, presença e equilíbrio. " +
            "Usa metáforas de natureza e caminho. Tom sereno, mas firme."
    ),

    CIENTISTA(
            "Cientista do Corpo",
            "Dados e evidências",
            "Fala como um fisiologista esportivo que ama dados e ciência. " +
            "Explica o porquê de cada exercício, a biomecânica, os efeitos hormonais. " +
            "Motiva com fatos e lógica. Tom didático e preciso, mas nunca chato."
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
     * Mapeia entrada do usuário (número ou nome) para PersonaType.
     */
    public static PersonaType fromUserInput(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = normalize(input);

        if (hasOption(normalized, "1", "um", "coach", "amigo")) return COACH_AMIGO;
        if (hasOption(normalized, "2", "dois", "estoico", "filosofo", "stoic")) return ESTOICO;
        if (hasOption(normalized, "3", "tres", "sargento", "drill", "militar", "goggins")) return DRILL_SERGEANT;
        if (hasOption(normalized, "4", "quatro", "atleta", "elite", "performance")) return ATLETA;
        if (hasOption(normalized, "5", "cinco", "monge", "guerreiro", "marcial")) return MONGE_GUERREIRO;
        if (hasOption(normalized, "6", "seis", "cientista", "ciencia", "dados")) return CIENTISTA;

        return null;
    }

    private static boolean hasOption(String input, String... options) {
        for (String option : options) {
            if (input.equals(option) || input.matches(".*\\b" + option + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
