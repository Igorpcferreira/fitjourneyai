# Fluxo de Progresso – FitJourneyAI

Este fluxo descreve como o usuário consulta sua evolução ao longo do tempo, com foco em comparação de dados (peso, medidas, treinos) e, opcionalmente, comentários motivacionais gerados pela OpenAI.

> Diagrama visual: `fluxo_progresso.png`

---

## 1. Objetivo

- Mostrar ao usuário como ele evoluiu entre dois momentos no tempo.
- Evidenciar mudanças em peso e medidas corporais.
- Apresentar a consistência de treinos em determinado período.
- Complementar com feedback motivacional.

---

## 2. Disparador

- Usuário seleciona **“Ver progresso”** no menu **ou**
- Usa comando (ex.: `/progresso`).

---

## 3. Etapas do fluxo

1. **Usuário – Telegram**
    - Solicita ver o progresso.

2. **Telegram Bot API → WebhookController**
    - Envia o *update* correspondente.

3. **WebhookController → MessageOrchestrator**
    - O orquestrador identifica a intenção como “progresso”.

4. **Definição do período de análise**
    - Pode ser fixo (ex.: últimos 30 dias) ou perguntar ao usuário:
      > "Você quer ver seu progresso dos últimos 30 dias ou desde o início?"

5. **Consultas ao banco de dados**  
   Utilizando:
    - `MeasurementRepository`:
        - busca pesos e medidas em duas datas de referência (início e fim do período).
    - `WorkoutRepository`:
        - conta a quantidade de treinos realizados no período.

6. **Cálculo de variações**
    - Diferença de peso (kg) entre as duas datas.
    - Diferença de medidas (cm) para cintura, peito, quadril etc.
    - Frequência de treinos (ex.: X treinos em 30 dias → média de Y treinos/semana).

7. **Geração opcional de texto motivacional via OpenAI**
    - O `MessageOrchestrator` pode montar um prompt para o `OpenAiService` contendo:
        - dados de variação de peso e medidas,
        - frequência de treinos,
        - objetivo do usuário.

    - A OpenAI gera um texto curto de incentivo, por exemplo:
      > "Você reduziu 3 cm de cintura e manteve uma ótima frequência de treinos. Continue assim que os resultados tendem a acelerar!"

8. **Resposta ao usuário**
    - O orquestrador combina:
        - números objetivos (variações),
        - resumo textual,
        - mensagem motivacional (quando usada).
    - O `TelegramService` envia a resposta formatada.

---

## 4. Dados utilizados

- `measurements`: histórico de peso e medidas.
- `workouts`: histórico de treinos.

Nenhum dado novo é criado; apenas leitura e cálculo são feitos a partir dos registros existentes.

---

## 5. Relação com outros fluxos

- Complementa o:
    - **Fluxo de resumo** (que é mais pontual),
    - **Fluxo de registro de peso**,
    - **Fluxo de registro de medidas**,
    - **Fluxo de registro de treino**.

É um dos fluxos mais importantes para reforçar o propósito do FitJourneyAI: **ajudar o usuário a enxergar sua evolução real ao longo do tempo**, não apenas o estado atual.
