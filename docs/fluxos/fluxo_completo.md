# Fluxo Completo – FitJourneyAI

Este documento descreve, em alto nível, o fluxo completo de uma interação típica do usuário com o FitJourneyAI, desde o envio da mensagem no Telegram até a resposta final, passando pelo backend em Spring Boot, integração com a OpenAI e persistência no PostgreSQL.

> Para a visão gráfica deste fluxo, consulte o diagrama `fluxo_completo.png` neste mesmo diretório.

---

## 1. Visão geral

O fluxo completo segue a seguinte cadeia de comunicação:

1. **Usuário** envia uma mensagem ao bot pelo **Telegram**.
2. A **Telegram Bot API** encaminha um *update* (webhook) para o backend **FitJourneyAI**.
3. O backend, através do **WebhookController**, delega o processamento ao **MessageOrchestrator**.
4. O **MessageOrchestrator** interpreta a intenção e aciona os serviços necessários:
    - consultas e gravações via repositórios JPA (PostgreSQL);
    - geração de conteúdo personalizado via **OpenAiService** (OpenAI API);
    - formatação e envio de resposta via **TelegramService**.
5. O usuário recebe a resposta final diretamente no Telegram.

Esse fluxo se repete para cada interação, variando apenas a **intenção** identificada (onboarding, registro de peso, medidas, treino, resumo, progresso etc.).

---

## 2. Etapas detalhadas de uma interação típica

A seguir, um exemplo de fluxo completo para uma mensagem do tipo “Quero um treino de pernas”.

### 2.1. Envio da mensagem pelo usuário

1. O usuário abre a conversa com o bot no Telegram.
2. Digita uma mensagem, por exemplo:
   > "Quero um treino de pernas"

3. A mensagem é enviada para a **Telegram Bot API**.

---

### 2.2. Recebimento do webhook no backend

4. A **Telegram Bot API** envia um JSON (*update*) para o endpoint HTTP configurado como webhook do FitJourneyAI.
5. O **WebhookController** recebe o JSON, extrai as informações relevantes (ID do usuário, texto da mensagem, data/hora) e monta um objeto de requisição interno.
6. O controller encaminha essa requisição para o **MessageOrchestrator**.

---

### 2.3. Orquestração e identificação da intenção

7. O **MessageOrchestrator**:

    - Verifica se o usuário já existe no banco (via `UserRepository`);
    - Caso não exista, cria o registro inicial (onboarding implícito);
    - Analisa o conteúdo da mensagem para identificar a intenção (ex.: pedir treino, registrar peso, consultar progresso etc.).

8. Para o exemplo “Quero um treino de pernas”, a intenção é classificada como **gerar plano de treino**.

---

### 2.4. Consulta à OpenAI e geração de resposta

9. O orquestrador monta um **prompt estruturado** com:

    - texto enviado pelo usuário;
    - contexto do usuário (nível, objetivo, restrições, histórico);
    - instruções de formato para a resposta (ex.: lista de exercícios, séries, repetições, tempo estimado, alertas de cuidado).

10. O orquestrador chama o **OpenAiService**, que:

    - monta a requisição HTTP para a **OpenAI API**;
    - envia o prompt;
    - recebe o texto de resposta com o plano de treino gerado pela IA.

11. O **OpenAiService** devolve ao orquestrador o conteúdo já tratado (ex.: texto puro ou estruturado em Markdown).

---

### 2.5. Persistência dos dados no PostgreSQL

12. Antes de responder ao usuário, o orquestrador:

    - registra a mensagem recebida em `MessageRepository`;
    - cria um registro em `WorkoutRepository` com o plano de treino gerado;
    - associa o treino ao usuário correspondente.

13. Dessa forma, o sistema mantém histórico de:

    - mensagens;
    - treinos sugeridos;
    - data/hora de cada interação.

---

### 2.6. Formatação e envio da resposta ao usuário

14. O orquestrador monta um texto amigável para o usuário (eventualmente com Markdown, emojis, destaques etc.).
15. O orquestrador chama o **TelegramService**, que:

    - constrói a requisição HTTP para a **Telegram Bot API**;
    - envia a mensagem com o plano de treino formatado.

16. A Telegram Bot API entrega a resposta ao usuário dentro da conversa do bot.

---

## 3. Relação com fluxos específicos

O fluxo descrito acima é a **espinha dorsal** do FitJourneyAI. A partir dele, surgem variações para diferentes intenções:

- **Fluxo de onboarding**  
  Quando o usuário interage pela primeira vez, o orquestrador dispara um diálogo guiado (perguntas sobre nome, objetivo, nível, disponibilidade semanal) antes de permitir registros ou geração de treinos.

- **Fluxo de registro de peso**  
  A mensagem do usuário é interpretada como atualização de peso atual. O orquestrador valida o dado e registra a medida em uma tabela específica, retornando uma confirmação.

- **Fluxo de registro de medidas**  
  Similar ao registro de peso, mas com múltiplos campos (ex.: cintura, peito, quadril). Os dados são armazenados e o usuário recebe feedback.

- **Fluxo de registro de treino realizado**  
  O usuário informa que concluiu um treino; o sistema marca o treino como realizado e pode enviar mensagem motivacional.

- **Fluxo de resumo e progresso**  
  Quando o usuário pede um resumo ou progresso, o orquestrador consulta o histórico (pesos, medidas, treinos) e monta uma resposta agregada, possivelmente complementada por comentários gerados via OpenAI.

---

## 4. Uso deste documento na implementacao

Durante a implementacao do backend, este `fluxo_completo.md` serve como:

- **Roteiro de desenvolvimento**: cada seção corresponde a partes do código (controller, orquestrador, serviços, repositórios).
- **Guia para testes manuais**: é possível seguir as etapas e validar se o comportamento real do bot corresponde ao fluxo descrito.
- **Referência para o TCC**: trechos deste documento podem ser reutilizados e adaptados para os capítulos de Metodologia e Descricao do Sistema.
