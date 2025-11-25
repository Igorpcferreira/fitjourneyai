# Fluxo de Resumo – FitJourneyAI

Este fluxo descreve como o usuário solicita um resumo consolidado das informações registradas (peso, medidas, treinos), recebendo uma visão rápida do estado atual.

> Diagrama visual: `fluxo_resumo.png`

---

## 1. Objetivo

- Exibir um painel simplificado com:
    - último peso registrado,
    - principais medidas recentes,
    - treinos realizados em determinado período.
- Ajudar o usuário a ter uma visão geral rápida da situação atual.

---

## 2. Disparador

- Usuário seleciona **“Ver resumo”** no menu **ou**
- Usa comando (ex.: `/resumo`).

---

## 3. Etapas do fluxo

1. **Usuário – Telegram**
    - Solicita o resumo.

2. **Telegram Bot API → WebhookController**
    - Envia o *update* para o backend.

3. **WebhookController → MessageOrchestrator**
    - O orquestrador identifica a intenção como “exibir resumo”.

4. **Consultas ao banco de dados**  
   Utilizando os repositórios:
    - `MeasurementRepository`:
        - busca o último peso;
        - busca o último conjunto de medidas.
    - `WorkoutRepository`:
        - busca os treinos realizados em um intervalo (por exemplo, últimos 7 ou 30 dias).

5. **Montagem do resumo**
    - O orquestrador agrega as informações em uma resposta estruturada, por exemplo:

        - Peso atual e diferença em relação ao registro anterior;
        - Principais medidas (cintura, peito, quadril) com comparações simples;
        - Quantidade de t
