# Plano de Implementação – FitJourneyAI

Este documento descreve o plano de implementação do backend do **FitJourneyAI**, um bot de acompanhamento fitness com integração ao **Telegram Bot API**, **OpenAI** e **PostgreSQL**.

Ele serve como checklist de desenvolvimento para o TCC1, alinhado:

- Aos fluxos descritos em `docs/fluxos/*.md` (por exemplo, `fluxo_completo_fitjourneyai.pdf`);
- Aos diagramas em `docs/architecture/*.mmd` e `docs/img/*.png`.

Use as checkboxes para marcar o que já foi implementado.

---

## Fase 0 – Contexto e Itens Já Concluídos

- [x] Definição do escopo do TCC (bot fitness com IA).
- [x] Decisão pela migração definitiva de **WhatsApp Cloud API** → **Telegram Bot API** (devido à proibição e limitações da Meta).
- [x] Criação de documentação de fluxos funcionais:
    - [x] `docs/fluxos/fluxo_completo.md` + PNG
    - [x] `docs/fluxos/fluxo_onboarding.md` + PNG
    - [x] `docs/fluxos/fluxo_registro_peso.md` + PNG
    - [x] `docs/fluxos/fluxo_registro_medidas.md` + PNG
    - [x] `docs/fluxos/fluxo_registro_treino.md` + PNG
    - [x] `docs/fluxos/fluxo_resumo.md` + PNG
    - [x] `docs/fluxos/fluxo_progresso.md` + PNG
- [x] Criação de diagramas de arquitetura:
    - [x] Diagrama de contexto (C4-1) – `docs/architecture/context.mmd` / `docs/img/context.png`
    - [x] Diagrama de contêiner (C4-2) – `docs/architecture/container.mmd` / `docs/img/container.png`
    - [x] Diagrama de componentes (C4-3) – `docs/architecture/components.mmd` / `docs/img/components.png`
    - [x] Diagrama de sequência principal – `docs/architecture/sequence.mmd` / `docs/img/sequence.png`
- [x] Criação do projeto **Spring Boot + Maven** (`fitjourneyai`).
- [x] Configuração inicial do **PostgreSQL** via `docker-compose.yml`.
- [x] Verificação de que a aplicação sobe e conecta no banco.

---

## Fase 1 – Organização de Pacotes e Configurações Básicas

### 1.1 Estrutura de pacotes

- [ ] Definir estrutura de pacotes (pode ser ajustada, mas ter um padrão base):

    - `br.edu.puc.fitjourneyai.config`
    - `br.edu.puc.fitjourneyai.controller`
    - `br.edu.puc.fitjourneyai.orchestrator`
    - `br.edu.puc.fitjourneyai.service.telegram`
    - `br.edu.puc.fitjourneyai.service.openai`
    - `br.edu.puc.fitjourneyai.service.scheduling` (para reengajamento/inatividade)
    - `br.edu.puc.fitjourneyai.domain.entity`
    - `br.edu.puc.fitjourneyai.domain.enums`
    - `br.edu.puc.fitjourneyai.domain.repository`
    - `br.edu.puc.fitjourneyai.dto.telegram`
    - `br.edu.puc.fitjourneyai.dto.openai`
    - `br.edu.puc.fitjourneyai.dto.internal` (DTOs internos, se necessário)

### 1.2 Configuração de application.yml

- [ ] Migrar/organizar `application.yml` para conter:
    - [ ] Configuração de datasource PostgreSQL (URL, usuário, senha via variáveis de ambiente).
    - [ ] `spring.jpa.hibernate.ddl-auto=none`
    - [ ] `spring.jpa.show-sql=false` (ligar em dev se quiser)
    - [ ] `spring.flyway.enabled=true`
    - [ ] Porta da aplicação (8080 ou outra, se necessário).

### 1.3 Propriedades externas

- [ ] Criar classe `TelegramProperties`:
    - botToken
    - baseUrl (https://api.telegram.org)
    - webhookPath (ex.: `/telegram/webhook`)
- [ ] Criar classe `OpenAiProperties`:
    - apiKey
    - baseUrl
    - model principal para treinos/mensagens
- [ ] Garantir que todas essas configs venham de env (`.env` + `application.yml`).

### 1.4 Cliente HTTP

- [ ] Criar bean `RestTemplate` ou `WebClient` global.
- [ ] Configurar timeouts básicos.
- [ ] Configurar log mínimo de requisições externas em nível DEBUG.

---

## Fase 2 – Modelagem de Domínio e Persistência (JPA + Flyway)

### 2.1 Entidades principais

- [ ] `User`
    - `id`
    - `telegramChatId`
    - `nome`
    - `objetivo` (enum)
    - `nivel` (enum)
    - `frequenciaTreinoEstimada`
    - `pesoAtual`
    - `altura` (opcional)
    - `onboardingConcluido` (boolean)
    - `lastInteractionAt`
    - `lastNudgeAt`
    - `nudgesEnabled`
    - `dataCadastro`
    - `dataAtualizacao`

- [ ] `Message`
    - `id`
    - `user` (FK)
    - `conteudo`
    - `tipo` (USER / BOT)
    - `dataHora`

- [ ] `Measurement`
    - `id`
    - `user` (FK)
    - `tipo` (enum: PESO, CINTURA, QUADRIL, PEITO, BRACO_DIREITO, BRACO_ESQUERDO, etc.)
    - `valor`
    - `dataRegistro`

- [ ] `Workout`
    - `id`
    - `user` (FK)
    - `grupoMuscular` (enum ou string categorizada)
    - `fonte` (enum: IA, MANUAL)
    - `descricaoTreino` (texto gerado pela IA, se for o caso)
    - `dataGeracao` (quando for treino gerado)
    - `dataRealizacao`
    - `duracaoMinutos`
    - `intensidadePercebida`
    - `observacoes` (livre)

- (Opcional/TCC2) [ ] Entidade específica para gráfico/agendamento, se necessário.

### 2.2 Enumerações

- [ ] `GoalType` – (EMAGRECIMENTO, HIPERTROFIA, DESEMPENHO, MANUTENCAO, etc.)
- [ ] `LevelType` – (INICIANTE, INTERMEDIARIO, AVANCADO)
- [ ] `MeasurementType` – (PESO, CINTURA, QUADRIL, PEITO, BRACO_DIREITO, BRACO_ESQUERDO...)
- [ ] `WorkoutGroup` – (PEITO, COSTAS, PERNAS, OMBROS, BRACOS, FULLBODY, CARDIO...)
- [ ] `WorkoutSource` – (IA, MANUAL)
- [ ] `MessageType` – (USER, BOT, SISTEMA)

### 2.3 Repositórios

- [ ] `UserRepository`
- [ ] `MessageRepository`
- [ ] `MeasurementRepository`
- [ ] `WorkoutRepository`

Com métodos auxiliares para:
- [ ] Buscar último peso/medida
- [ ] Buscar treinos por período
- [ ] Buscar usuários inativos para reengajamento

### 2.4 Migrações com Flyway

Criar scripts SQL em `src/main/resources/db.migration`:

- [ ] `V1__create_users.sql`
- [ ] `V2__create_messages.sql`
- [ ] `V3__create_measurements.sql`
- [ ] `V4__create_workouts.sql`

Rodar a aplicação e verificar se as tabelas sobem corretamente.

---

## Fase 3 – Integração com Telegram

### 3.1 DTOs do Telegram

- [ ] Criar modelos em `dto.telegram` para mapear os JSONs:
    - [ ] `TelegramUpdate`
    - [ ] `TelegramMessage`
    - [ ] `TelegramChat`
    - [ ] `TelegramSendMessageRequest`
    - (Opcional) [ ] Estruturas para botões inline, se forem usadas.

### 3.2 Serviço Telegram

- [ ] Criar `TelegramService` com métodos:
    - [ ] `sendMessage(chatId, text)` – mínimo viável
    - (Opcional) [ ] `sendMessageMarkdown(chatId, text)`
    - (Opcional) [ ] `sendPhoto(chatId, imageBytes)` – para gráficos no futuro

### 3.3 Webhook

- [ ] Criar `WebhookController`:
    - [ ] `POST /telegram/webhook` recebendo `TelegramUpdate`
    - [ ] Normalizar dados para um modelo interno (`InternalMessage`):
        - chatId
        - texto
        - dataHora
    - [ ] Encaminhar para `MessageOrchestrator`
    - [ ] Sempre retornar 200 OK para o Telegram

- [ ] Criar endpoint de saúde:
    - [ ] `GET /health` → retorna `"OK"` (útil para testes rápidos e TCC)

---

## Fase 4 – Integração com OpenAI

### 4.1 DTOs

- [ ] DTO de request para OpenAI (modelo de chat completion):
    - modelo, mensagens, temperatura, etc.
- [ ] DTO de response:
    - choices, content, finish_reason etc.

### 4.2 Serviço OpenAI

- [ ] `OpenAiService` com métodos:
    - [ ] `generateWorkoutPlan(userProfile, userRequest)`  
      → usado no fluxo de geração de treino personalizado.
    - [ ] `generateMotivationalMessage(progressContext)`  
      → usado no fluxo de resumo/progresso.
    - [ ] `classifyIntentIfNeeded(textoUsuario)` (opcional para fallback/ajuda).

- [ ] Tratar erros de rede/limite de tokens e retornar mensagem amigável ao usuário.

---

## Fase 5 – Orquestrador de Mensagens e Estados de Conversa

### 5.1 Modelo interno de mensagem

- [ ] Criar classe interna (ex.: `IncomingMessage`) contendo:
    - chatId
    - texto
    - dataHora
    - referência ao usuário (após lookup)

### 5.2 `MessageOrchestrator`

- [ ] Implementar `MessageOrchestrator` com responsabilidades:

    - [ ] Carregar/registrar usuário a partir do `telegramChatId`.
    - [ ] Atualizar `lastInteractionAt` a cada mensagem recebida.
    - [ ] Registrar a mensagem em `MessageRepository` (tipo USER).
    - [ ] Identificar intenção:
        - comandos fixos: `/start`, `/menu`, `/ajuda`, `/registro`, `/peso`, `/medidas`,
          `/treino`, `/treino_feito`, `/progresso`, `/resumo`, `/config`.
        - fluxo ativo: onboarding, registro guiado etc.
        - fallback (intenção desconhecida).
    - [ ] Delegar para handlers específicos de cada fluxo (métodos privados).
    - [ ] Registrar também mensagens do bot em `MessageRepository` (tipo BOT).
    - [ ] Retornar o texto de resposta para o `TelegramService`.

### 5.3 Estado de conversa

- [ ] Definir como armazenar o "passo" atual do usuário (onboarding, registro de medidas, etc.):
    - campo em `User` (ex.: `currentFlow`, `currentStep`) **ou**
    - entidade separada (ex.: `ConversationState`).
- [ ] Atualizar esse estado a cada interação.
- [ ] Garantir que, ao finalizar um fluxo, o estado é limpo.

---

## Fase 6 – Implementação dos Fluxos Funcionais

Cada item abaixo deve ser implementado alinhado aos arquivos em `docs/fluxos/*.md` e ao documento `fluxo_completo_fitjourneyai.pdf`.

### 6.1 Fluxo de Onboarding (`/start`)

- [ ] Verificar se usuário existe e se `onboardingConcluido` é `false`.
- [ ] Enviar mensagem de boas-vindas.
- [ ] Coletar, em passos:
    - nome
    - objetivo
    - nível
    - frequência semanal de treino
    - peso atual (obrigatório)
    - altura (opcional)
- [ ] Permitir revisão e confirmação dos dados.
- [ ] Persistir no `User`.
- [ ] Marcar `onboardingConcluido = true`.
- [ ] Apresentar menu principal com comandos disponíveis.

### 6.2 Fluxo de Menu e Ajuda (`/menu`, `/ajuda`)

- [ ] Implementar resposta com lista clara de comandos:
    - `/registro` – registrar peso e medidas
    - `/treino_feito` – registrar treinos realizados
    - `/treino` – gerar treino personalizado
    - `/progresso` – ver evolução
    - `/resumo` – resumo recente
    - `/config` – ajustes de preferências
- [ ] Reutilizar esse fluxo como fallback quando o usuário “se perde”.

### 6.3 Fluxo de Registro de Peso (`/peso` ou mensagem com valor explícito)

- [ ] Identificar valor de peso na mensagem (regex).
- [ ] Se valor não estiver presente, perguntar "Qual é seu peso atual (kg)?"
- [ ] Validar numérico + faixa plausível.
- [ ] Salvar em `Measurement` com tipo = PESO.
- [ ] Atualizar `pesoAtual` em `User`.
- [ ] Calcular diferença em relação ao último registro e informar ao usuário (se existir).

### 6.4 Fluxo de Registro de Medidas (`/medidas` ou `/registro` guiado)

- [ ] Iniciar sequência de perguntas:
    - peso (se ainda não informado no fluxo)
    - cintura
    - quadril
    - peito
    - braço(s)
    - outros campos definidos
- [ ] Validar cada valor.
- [ ] Ao final, mostrar resumo e solicitar confirmação.
- [ ] Persistir cada medida em `Measurement` com o tipo correspondente.
- [ ] Calcular variações em relação à medição anterior (quando fizer sentido).

### 6.5 Fluxo de Registro de Treino Realizado (`/treino_feito`)

- [ ] Perguntar grupo muscular (via texto ou botões).
- [ ] Perguntar duração aproximada (minutos).
- [ ] Perguntar intensidade percebida (1 a 10).
- [ ] (Opcional) Perguntar exercícios livres em texto.
- [ ] Salvar em `Workout`.
- [ ] Enviar mensagem de confirmação + reforço positivo.

### 6.6 Fluxo de Geração de Treinos Personalizados (`/treino`)

- [ ] Recuperar dados do usuário:
    - objetivo, nível, frequência, histórico recente de treinos.
- [ ] Montar prompt estruturado para OpenAI (conforme doc de fluxo).
- [ ] Chamar `OpenAiService.generateWorkoutPlan(...)`.
- [ ] Tratar resposta: formatar em lista (markdown).
- [ ] Enviar treino ao usuário.
- [ ] (Opcional) Salvar treino gerado em `Workout` como “plano recomendado” (sem dataRealizacao ainda).

### 6.7 Fluxo de Visualização de Progresso e Gráficos (`/progresso`)

- [ ] Definir período padrão (ex.: últimos 30 dias) ou permitir escolha.
- [ ] Consultar:
    - pesos/medidas nesse período (`MeasurementRepository`);
    - treinos realizados (`WorkoutRepository`).
- [ ] Calcular variações:
    - peso inicial x final;
    - medidas selecionadas;
    - número de treinos, média por semana.
- [ ] (Opcional / TCC2) Gerar gráficos (linha/barras) como imagem:
    - gerar PNG e enviar via `TelegramService`.
- [ ] Montar resumo textual com os números.
- [ ] (Opcional) Chamar `OpenAiService.generateMotivationalMessage` para comentário motivacional.

### 6.8 Fluxo de Resumo Periódico (`/resumo`)

- [ ] Calcular indicadores do período recente (última semana/mês):
    - quantidade de treinos
    - variação de peso
    - principais mudanças de medida
- [ ] Montar resposta textual simples com esses números.
- [ ] (Opcional) complementar com mensagem motivacional da IA.

### 6.9 Fluxo de Ajuda / Fallback (mensagem fora de contexto)

- [ ] Quando comando ou texto não mapearem para nenhum fluxo:
    - Tentar classificação de intenção com OpenAI (opcional).
    - Se ainda assim indefinido, responder com mensagem amigável de “não entendi”
      e apontar comandos principais (`/menu`).

### 6.10 Fluxo de Monitoramento de Inatividade / Reengajamento

- [ ] Manter campos em `User`:
    - `lastInteractionAt`
    - `lastNudgeAt`
    - `nudgesEnabled`
- [ ] Criar um job agendado em `service.scheduling`, usando `@Scheduled`:
    - Rodar diariamente (por exemplo).
    - Buscar usuários com:
        - `lastInteractionAt` anterior ao limite (ex.: hoje - 7 dias)
        - `nudgesEnabled = true`
        - `lastNudgeAt` nulo ou antigo demais.
- [ ] Para cada usuário inativo:
    - Montar contexto resumido (nome, objetivo, dias parado, treinos antes de parar).
    - (Opcional) Chamar OpenAI para gerar mensagem personalizada de reengajamento.
    - Enviar mensagem pelo `TelegramService`.
    - Atualizar `lastNudgeAt`.

---

## Fase 7 – Preocupações Transversais (Cross-cutting)

- [ ] Logging consistente (entrada e saída dos principais fluxos).
- [ ] Tratamento de exceções globais (`@ControllerAdvice` se necessário).
- [ ] Validações básicas de entrada.
- [ ] Uso correto de variáveis de ambiente (sem credenciais em código).
- [ ] Pequena camada de “rate limiting” manual se necessário (para não floodar OpenAI).

---

## Fase 8 – Testes

### 8.1 Testes unitários

- [ ] Testes de `MessageOrchestrator`:
    - onboarding
    - registro de peso
    - geração de treino
- [ ] Testes de `OpenAiService` (mockando client HTTP).
- [ ] Testes de `TelegramService` (montagem da URL e payload).

### 8.2 Testes de integração (opcional, mas desejável)

- [ ] Utilizar banco em memória ou Testcontainers para validar repositórios.
- [ ] Testar sequência completa: request simulada do Telegram → Webhook → Orchestrator → resposta.

### 8.3 Testes manuais

- [ ] Testar cada fluxo via Telegram real:
    - onboarding
    - menu/ajuda
    - registro de peso
    - registro de medidas
    - treino realizado
    - treino gerado
    - resumo
    - progresso
    - reengajamento (simular mudando datas no banco, se necessário)
- [ ] Capturar **prints** para usar no TCC (evidências).

---

## Fase 9 – Deploy, Execução e Documentação Final

### 9.1 Docker e execução local

- [ ] Garantir que:
    - `docker-compose.yml` sobe banco + app (ou pelo menos banco).
    - `./mvnw spring-boot:run` funciona usando as variáveis de ambiente definidas.

### 9.2 Deploy simples (opcional)

- [ ] Criar `Dockerfile` para a aplicação.
- [ ] Documentar como subir tudo com `docker compose up -d`.

### 9.3 Documentação

- [ ] Atualizar `README.md` na raiz:
    - objetivo do projeto
    - tecnologias usadas
    - como rodar (passo a passo)
    - como configurar env
- [ ] Referenciar `docs/architecture` e `docs/fluxos` no README.
- [ ] Atualizar relatórios de andamento do TCC com:
    - prints da aplicação funcionando
    - link do repositório GitHub
    - comentários sobre decisões técnicas (ex.: migração do WhatsApp para Telegram).

---

## Observações Finais

- Este plano cobre **mais do que o mínimo** para TCC1.
- Para priorizar:
    1. Concluir Fase 1 → 4 (infra + integrações).
    2. Implementar Fluxos principais: Onboarding, Registro Peso/Medidas, Treino Gerado e Resumo/Progresso.
    3. Só depois, adicionar reengajamento e gráficos se houver tempo.

Use este arquivo como referência viva: conforme for implementando, marque as checkboxes e, se necessário, ajuste o plano.
