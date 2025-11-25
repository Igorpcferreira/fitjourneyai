# Fluxos Funcionais – FitJourneyAI

Este diretório reúne os fluxos funcionais do bot FitJourneyAI, focados na experiência de uso (UX) e no comportamento conversacional do sistema.

Os fluxos aqui documentados complementam os diagramas de arquitetura técnicos presentes em `docs/architecture`, descrevendo **o que o bot faz** do ponto de vista do usuário.

## Arquivos

- **fluxo_completo.md**  
  Descrição em texto do fluxo completo de uma interação típica com o FitJourneyAI, desde o envio da mensagem pelo usuário até a resposta final do bot, incluindo chamadas à OpenAI e persistência no banco de dados.

- **fluxo_completo.png**  
  Diagrama visual com a visão macro do fluxo de comunicação entre:
    - Usuário (Telegram)
    - Telegram Bot API
    - Backend FitJourneyAI (Spring Boot)
    - OpenAI API
    - Banco de dados PostgreSQL

- **fluxo_onboarding.png**  
  Fluxo específico de onboarding: primeiro contato do usuário com o bot, apresentação, coleta de informações iniciais (ex.: nome, objetivo, nível de treino) e registro do perfil.

- **fluxo_registro_peso.png**  
  Fluxo de registro de peso corporal: como o usuário informa o peso atual e como o sistema valida e armazena esse dado.

- **fluxo_registro_medidas.png**  
  Fluxo de registro de medidas corporais (ex.: cintura, peito, quadril, braço), incluindo validações mínimas e resposta de confirmação.

- **fluxo_registro_treino.png**  
  Fluxo de registro de treino realizado, permitindo que o usuário informe que concluiu um treino sugerido ou um treino próprio.

- **fluxo_resumo.png**  
  Fluxo de resumo de dados: como o usuário solicita um resumo (ex.: últimos pesos, medidas, treinos) e como o sistema responde de forma agregada.

- **fluxo_progresso.png**  
  Fluxo de consulta de progresso, focado em comparar momentos (ex.: evolução de peso/medidas ao longo do tempo) e enviar feedback motivacional.

