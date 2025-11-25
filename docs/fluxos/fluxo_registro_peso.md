# Fluxo de Registro de Peso – FitJourneyAI

Este fluxo descreve como o usuário registra seu peso atual no FitJourneyAI e como essa informação é persistida para acompanhamento de evolução.

> Diagrama visual: `fluxo_registro_peso.png`

---

## 1. Objetivo

- Permitir que o usuário registre seu peso corporal atual.
- Manter histórico de pesos para uso em resumos e análise de progresso.
- Possibilitar feedbacks personalizados e motivacionais.

---

## 2. Disparador

- Usuário seleciona a opção de **registrar peso** no menu do bot **ou**
- Invoca um comando específico (por exemplo, `/peso`) **ou**
- Envia mensagem em formato reconhecido como peso (ex.: “78.5 kg”).

---

## 3. Etapas do fluxo

1. **Usuário – Telegram**
    - Solicita o registro de peso (menu, comando ou mensagem).

2. **Telegram Bot API → WebhookController**
    - Envia o *update* correspondente (JSON) para o backend.

3. **WebhookController → MessageOrchestrator**
    - O orquestrador identifica que a intenção é “registro de peso”.

4. **Pergunta ou confirmação de valor**
    - Se o usuário ainda não informou o valor do peso, o bot pergunta:
      > "Qual é seu peso atual (em kg)?"
    - Se o usuário já enviou na mesma mensagem, o orquestrador tenta extrair o valor numérico.

5. **Recebimento do valor do peso**
    - O usuário responde com um valor (ex.: `78.5`).
    - O orquestrador valida:
        - se é numérico,
        - se está dentro de uma faixa plausível (ex.: 30–300 kg).

6. **Persistência no banco**
    - O `MessageOrchestrator` aciona o `MeasurementRepository` (ou entidade equivalente) para criar um novo registro, contendo:
        - usuário
        - tipo de medida: peso
        - valor informado
        - data/hora do registro

7. **Resposta ao usuário**
    - O bot confirma o registro:
      > "Peso de 78.5 kg registrado com sucesso!"
    - Opcionalmente, pode comparar com o último valor registrado e informar variação:
      > "Você está 0.8 kg abaixo do último registro."

    - Mensagem enviada via `TelegramService`.

---

## 4. Dados armazenados

- Tabela `measurements` (ou equivalente):
    - id
    - user_id
    - tipo (ex.: "PESO")
    - valor
    - data_registro

---

## 5. Relação com outros fluxos

- Os dados deste fluxo são usados em:
    - **Fluxo de resumo** (mostrar últimos pesos)
    - **Fluxo de progresso** (curva de evolução e variações)
    - Mensagens motivacionais geradas pela OpenAI em momentos estratégicos.
