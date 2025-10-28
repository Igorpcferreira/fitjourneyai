# 🧠 FitJourneyAI

**Assistente inteligente para acompanhamento fitness e motivacional via Telegram**  
*(Projeto de TCC — PUC Goiás)*

---

## 📘 Sobre o Projeto

O **FitJourneyAI** é um bot desenvolvido como Trabalho de Conclusão de Curso (TCC) com o objetivo de **auxiliar praticantes de atividades físicas** através de mensagens inteligentes e personalizadas.  
Inicialmente planejado para o WhatsApp, o projeto migrou para o **Telegram Bot API** devido a limitações técnicas impostas pela Meta (WhatsApp Cloud API), garantindo liberdade total para testes e integração.

O bot é capaz de:
- Interagir com o usuário em tempo real;
- Oferecer **mensagens motivacionais** personalizadas;
- Gerar **treinos automáticos** com base em prompts inteligentes;
- Utilizar a **API da OpenAI** (modelo `gpt-4o-mini`) para gerar respostas contextuais e naturais.

---

## 🏗️ Arquitetura do Sistema

```mermaid
flowchart TD
    A[Usuário - Telegram] -->|Mensagens| B[Bot Telegram API]
    B -->|Requisição| C[Servidor Node.js (Express)]
    C -->|Prompt| D[OpenAI API]
    D -->|Resposta inteligente| C
    C -->|Mensagem formatada| B
    B -->|Retorno ao usuário| A
