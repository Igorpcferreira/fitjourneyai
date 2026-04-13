# Fluxo de Onboarding – FitJourneyAI

Este fluxo descreve o primeiro contato do usuário com o FitJourneyAI, momento em que são coletadas informações básicas para personalização dos treinos e mensagens.

> Diagrama visual: `fluxo_onboarding.png`

---

## 1. Objetivo

- Apresentar o FitJourneyAI ao usuário.
- Coletar dados iniciais (nome, objetivo, nível de treino, disponibilidade).
- Criar o registro do usuário no banco de dados.
- Preparar o contexto para fluxos futuros (peso, medidas, treinos).

---

## 2. Disparador

- O usuário inicia conversa com o bot no Telegram pela primeira vez **ou**
- Envia o comando `/start`.

---

## 3. Etapas do fluxo

1. **Usuário – Telegram**
    - Abre o chat com o bot e envia `/start` ou uma primeira mensagem qualquer.

2. **Telegram Bot API → WebhookController**
    - A API do Telegram envia um *update* (JSON) para o endpoint HTTP configurado como webhook.

3. **WebhookController → MessageOrchestrator**
    - O controller normaliza os dados do update (chatId, texto, data) e encaminha para o orquestrador.

4. **Verificação de existência do usuário**
    - O `MessageOrchestrator` utiliza o `UserRepository` para verificar se já existe registro com aquele `chatId`.
    - Se **já existir**, o fluxo de onboarding é pulado e o usuário segue para o menu principal.
    - Se **não existir**, inicia-se o onboarding.

5. **Perguntas guiadas (diálogo passo a passo)**  
   O bot faz perguntas sequenciais, por exemplo:
    - Nome do usuário
    - Objetivo principal (ex.: emagrecimento, hipertrofia, condicionamento)
    - Nível atual (iniciante, intermediário, avançado)
    - Frequência desejada de treinos por semana
    - Restrições básicas (se houver)

   Cada resposta:
    - é recebida pelo `WebhookController`,
    - tratada pelo `MessageOrchestrator`,
    - associada à etapa atual do fluxo (estado da conversa).

6. **Persistência dos dados**
    - Ao final de cada pergunta, o `MessageOrchestrator` atualiza o registro do usuário via `UserRepository`.
    - Ao final do fluxo, o usuário está totalmente cadastrado com um perfil mínimo.

7. **Mensagem de fechamento do onboarding**
    - O orquestrador envia uma mensagem de boas-vindas consolidada, por exemplo:
      > "Valeu, Igor! Já registrei seu objetivo de hipertrofia com treinos 4x por semana. A partir de agora posso te ajudar a registrar peso, medidas, treinos e gerar planos personalizados."
    - Essa mensagem é enviada via `TelegramService`.

8. **Transição para o menu principal**
    - O bot apresenta as próximas opções: registrar peso, registrar medidas, pedir treino, ver resumo etc.

---

## 4. Dados armazenados

- Tabela `users`:
    - chatId (Telegram)
    - nome
    - objetivo
    - nivel
    - frequencia_treinos
    - data_cadastro
    - demais campos definidos na modelagem

---

## 5. Relação com outros fluxos

- É o **ponto de entrada** para:
    - fluxo de registro de peso
    - fluxo de registro de medidas
    - fluxo de geração de treino
    - fluxo de resumo e progresso

Sem o onboarding, o usuário teria experiência limitada e sem contexto adequado para treinos personalizados.
