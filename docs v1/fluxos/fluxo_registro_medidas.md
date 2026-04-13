# Fluxo de Registro de Medidas Corporais – FitJourneyAI

Este fluxo descreve como o usuário registra medidas corporais (ex.: cintura, peito, quadril, braço) para acompanhar de forma mais detalhada sua evolução física.

> Diagrama visual: `fluxo_registro_medidas.png`

---

## 1. Objetivo

- Coletar medidas corporais relevantes para acompanhamento de composição física.
- Manter histórico das medidas para comparação ao longo do tempo.
- Fornecer base para feedbacks mais específicos sobre progresso.

---

## 2. Disparador

- Usuário seleciona a opção **“Registrar medidas”** no menu do bot **ou**
- Envia comando específico (ex.: `/medidas`).

---

## 3. Etapas do fluxo

1. **Usuário – Telegram**
    - Escolhe registrar medidas corporais.

2. **Telegram Bot API → WebhookController**
    - Envia o *update* com a interação.

3. **WebhookController → MessageOrchestrator**
    - O orquestrador identifica a intenção como “registro de medidas”.

4. **Perguntas sequenciais de medidas**  
   O bot solicita, uma a uma, medidas predefinidas, por exemplo:
    - Cintura (cm)
    - Peito / tórax (cm)
    - Quadril (cm)
    - Braço direito (cm)
    - Coxa direita (cm)

   A cada pergunta:
    - o usuário responde com um valor numérico;
    - o orquestrador valida o valor (numérico + faixa razoável);
    - o valor é armazenado temporariamente até o fim da sequência.

5. **Persistência no banco**
    - Ao final da coleta, o `MessageOrchestrator` persiste as medidas via `MeasurementRepository`, criando registros como:
        - user_id
        - tipo_medida (ex.: CINTURA, PEITO, QUADRIL, etc.)
        - valor
        - data_registro

    - Pode ser utilizada a mesma tabela de `measurements` com diferentes tipos.

6. **Mensagem de confirmação**
    - O bot confirma que as medidas foram registradas:
      >
