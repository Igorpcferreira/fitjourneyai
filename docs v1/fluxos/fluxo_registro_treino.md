# Fluxo de Registro de Treino Realizado – FitJourneyAI

Este fluxo descreve como o usuário informa que realizou um treino (seja um treino sugerido pelo bot ou um treino próprio), permitindo o acompanhamento de consistência e frequência.

> Diagrama visual: `fluxo_registro_treino.png`

---

## 1. Objetivo

- Registrar que o usuário concluiu um treino em uma determinada data.
- Relacionar o treino realizado a um plano sugerido (quando for o caso).
- Manter histórico de treinos para uso em resumos e progresso.

---

## 2. Disparador

- Usuário seleciona **“Registrar treino realizado”** no menu **ou**
- Usa um comando como `/treino_feito` **ou**
- Responde positivamente a uma mensagem do bot (ex.: “Concluí o treino de hoje”).

---

## 3. Etapas do fluxo

1. **Usuário – Telegram**
    - Indica que realizou um treino.

2. **Telegram Bot API → WebhookController**
    - Envia o *update* com a interação.

3. **WebhookController → MessageOrchestrator**
    - O orquestrador identifica a intenção como “registro de treino realizado”.

4. **Identificação do treino**
    - O sistema verifica, via `WorkoutRepository`:
        - se existe um treino sugerido recentemente para aquele usuário (por exemplo, nas últimas 24h);
        - ou se o usuário precisa informar o tipo de treino (pernas, costas, peito, etc.).

5. **Coleta de detalhes adicionais (opcional)**
    - O bot pode perguntar:
        - duração aproximada (minutos),
        - percepção de esforço (baixa, média, alta).

6. **Persistência no banco**
    - Cria ou atualiza um registro em `workouts`:
        - user_id
        - tipo_treino (caso aplicável)
        - referencia_plano_gerado (se foi baseado em treino da IA)
        - data_realizacao
        - duracao (opcional)
        - esforco_percebido (opcional)

7. **Resposta ao usuário**
    - O bot confirma:
      > "Boa! Seu treino de hoje foi registrado. Continuar consistente e o resultado vem!"
    - Mensagem enviada via `TelegramService`.

---

## 4. Dados armazenados

- Tabela `workouts`:
    - id
    - user_id
    - tipo_treino
    - data_realizacao
    - referencia_plano (quando for um treino gerado pelo bot)
    - outros campos opcionais conforme modelagem

---

## 5. Relação com outros fluxos

- Usado em:
    - **Fluxo de resumo** (mostrar treinos recentes)
    - **Fluxo de progresso** (frequência semanal/mensal)
    - Geração de mensagens motivacionais pela OpenAI (“Você treinou X vezes essa semana!”).
