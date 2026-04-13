package br.edu.puc.fitjourneyai.core.port;

/**
 * Porta de saída para envio de mensagens ao canal de comunicação (Telegram).
 * <p>
 * Abstrai o envio para que o core não conheça a API do Telegram.
 * A implementação concreta fica em {@code adapter.telegram.TelegramGateway}.
 */
public interface MessageGateway {

    /**
     * Envia uma mensagem de texto para o chat.
     *
     * @param chatId identificador do chat no Telegram
     * @param text   texto da mensagem
     */
    void sendText(Long chatId, String text);

    /**
     * Envia uma imagem (PNG) com legenda para o chat.
     * Usado para gráficos de progresso gerados por JFreeChart.
     *
     * @param chatId  identificador do chat no Telegram
     * @param image   bytes da imagem PNG
     * @param caption legenda da imagem (pode ser null)
     */
    void sendPhoto(Long chatId, byte[] image, String caption);

    /**
     * Envia mensagem de texto formatada com HTML (negrito, itálico, etc.).
     * Usa parse_mode=HTML no Telegram.
     *
     * @param chatId identificador do chat no Telegram
     * @param html   texto com tags HTML suportadas pelo Telegram
     */
    void sendHtmlText(Long chatId, String html);

    /**
     * Envia múltiplas imagens como álbum (sendMediaGroup) no Telegram.
     * O Telegram agrupa as fotos visualmente numa grid compacta.
     *
     * @param chatId  identificador do chat
     * @param images  lista de imagens (bytes PNG)
     * @param caption legenda da primeira imagem (aparece no álbum)
     */
    void sendPhotoAlbum(Long chatId, java.util.List<byte[]> images, String caption);
}
