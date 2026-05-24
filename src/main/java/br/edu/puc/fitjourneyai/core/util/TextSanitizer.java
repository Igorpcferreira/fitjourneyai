package br.edu.puc.fitjourneyai.core.util;

public final class TextSanitizer {

    private TextSanitizer() {
        // utility class
    }

    public static String stripHtmlTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder out = new StringBuilder(text.length());
        boolean inTag = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                inTag = true;
                continue;
            }
            if (c == '>') {
                inTag = false;
                continue;
            }
            if (!inTag) {
                out.append(c);
            }
        }

        return out.toString();
    }
}
