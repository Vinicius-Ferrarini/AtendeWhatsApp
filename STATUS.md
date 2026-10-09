# Status do projeto

**Atualizado em:** 2026-10-08 (medição parcial do spike)

| | |
| --- | --- |
| **Fase atual** | Fase 0 — spike de validação |
| **Código de produção** | Nenhuma linha ainda (começa na Fase 1) |
| **Bloqueio** | Atender não funciona por `PendingIntent`; a v0.2 testa 4 alternativas |
| **Próximo passo** | Instalar a v0.2 do spike e rodar uma ligação: a cascata descobre qual estratégia atende |

---

## Onde o projeto está

A especificação está completa: requisitos, critérios de aceite, arquitetura, catálogo de testes e tarefas por fase. A constituição do projeto está ratificada.

Antes de escrever qualquer código de produção, a Fase 0 precisa responder **6 perguntas** sobre como o WhatsApp e o HyperOS se comportam de verdade (`plan.md`, seção 7). Cada resposta define se o app usa o plano A ou o plano B naquele ponto. Um app de spike descartável foi escrito, compilado e executado no aparelho. **Quatro das sete medições já foram feitas**; faltam P-1b, P-5 e P-6.

As respostas medidas estão na seção 7.1 do `plan.md`. As três pendentes seguem em branco de propósito: a tabela só recebe observação real.

## O que já foi feito

### Especificação

- [x] Constituição ratificada (v1.0.0) — 10 princípios inegociáveis
- [x] `spec.md` — 5 histórias de usuário, 8 requisitos funcionais (`RF-01` a `RF-08`), 6 não funcionais, 5 decisões em aberto
- [x] `plan.md` — arquitetura ports and adapters, stack, máquina de estados, riscos, configuração do aparelho
- [x] `tests.md` — 20 testes unitários, 7 de integração, 3 de UI, 10 de aceitação manual, todos rastreados a um critério de aceite
- [x] `tasks.md` — 55 tarefas em 9 fases
- [x] **T014** — documentos movidos para `.specify/memory/` e `specs/001-atende-pai/`, os caminhos que o `CLAUDE.md` referencia *(tarefa da Fase 1, concluída fora de ordem)*

### Fase 0 — spike de validação

Medido no Redmi Note 13 em 2026-10-08, com chamada de voz real. Detalhes na seção 7.1 do [`plan.md`](specs/001-atende-pai/plan.md).

- [x] **T001** — app de spike escrito, compilado, instalado e executado
- [~] **T002** — P-1 **bloqueado: SIM** (`category=call`, ação `Aceitar` com `PendingIntent`). Falta o cenário desbloqueado
- [x] **T003** — P-2 **NÃO**: `PendingIntent.send()` não lança exceção e não tem efeito. Atender vai por acessibilidade
- [x] **T004** — P-3 **SIM**: notificação contínua com ação `Desligar`; encerra de verdade
- [x] **T005** — P-4 **NÃO**: o áudio volta para o alto-falante de ouvido em até 20 s. Viva-voz vai por acessibilidade
- [ ] **T006** — P-5: gesto de volume chega à acessibilidade com a tela apagada?
- [ ] **T007** — P-6: opção nativa do botão liga/desliga encerra chamada do WhatsApp?
- [ ] **T008** — salvar a notificação real como `fixtures/whatsapp_chamada.json`
- [ ] **T009** — registrar as respostas (parcial, feito) e decidir C-5 na `spec.md` (travado em P-5/P-6)

**O achado central:** o mesmo código encerra a chamada mas não a atende. Atender exige serviço em primeiro plano com microfone, que o Android 14 nega a um disparo programático em segundo plano; desligar não precisa de microfone. Isso promove o `AccessibilityService` de contingência a mecanismo principal de atendimento.

### Ambiente

- [x] Android SDK portátil instalado (platform-tools 37.0.1, cmdline-tools, platform 35, build-tools 35.0.0)
- [x] JDK 17 (Microsoft OpenJDK LTS) e Gradle 8.9 instalados, sem Android Studio
- [x] `platform-tools` no PATH do usuário
- [ ] Redmi Note 13 com depuração USB reconhecida *(opcional — o spike funciona sem cabo)*

## O spike de validação

Vive na branch órfã [`spike`](../../tree/spike), sem relação alguma com a `main`. O `tasks.md` manda manter o código da Fase 0 fora do projeto principal; a branch órfã cumpre isso e ainda preserva o trabalho. **Nenhuma linha dele vai para produção.**

O APK compilado está versionado lá como `atende-spike.apk`, para baixar direto no celular.

O app se auto-avalia e grava o veredito de cada pergunta no próprio aparelho, então **não precisa de cabo USB** para colher os resultados.

| Pergunta | Como é respondida |
| --- | --- |
| P-1a / P-1b | Automático: categoria, ação de atender, `fullScreenIntent`, bloqueado vs. desbloqueado |
| P-2 | Automático: a cascata tenta quatro estratégias e descobre qual atendeu |
| P-3 | Automático, na notificação contínua da chamada em andamento |
| P-4 | Automático; só vira `SIM` se o viva-voz resistir às reconsultas de 2, 5, 10 e 20 s |
| P-5 | Automático quando a tecla de volume chega com a tela apagada |
| P-6 | Você registra num botão — é configuração do sistema, não dá para detectar |

### v0.2 — a cascata de atendimento

Como atender foi o único ponto crítico que falhou, a v0.2 ataca-o por quatro ângulos numa única ligação:

| ID | Estratégia | Aposta |
| --- | --- | --- |
| P-2.1 | `PendingIntent` simples | linha de base; já sabemos que falha, serve de controle |
| P-2.2 | `PendingIntent` + `setPendingIntentBackgroundActivityStartMode(ALLOWED)` | concede a isenção que falta (API 34) |
| P-2.3 | `TelecomManager.acceptRingingCall()` | se o WhatsApp usa Telecom, é o caminho mais limpo |
| P-2.4 | Clique no botão por acessibilidade | o clique sintético conta como interação do usuário |

**O sucesso passou a ser detectado sozinho.** A notificação contínua só existe depois da chamada atendida (comprovado em P-3), então a cascata dispara uma estratégia, espera 2,5 s e usa a chegada dessa notificação como sinal objetivo. P-2 não depende mais de confirmação de ouvido.

Desligar ganhou reserva por acessibilidade (**P-3b**) e o viva-voz também (**P-4b**), já que P-4 falhou.

O botão **EXPORTAR E ENVIAR** grava em `Downloads` e abre a folha de compartilhamento:

- `atende-spike-RESPOSTAS-<data>.txt` — veredito de P-1 a P-6, legível
- `atende-spike-log-<data>.txt` — log completo, com o JSON de cada notificação
- `whatsapp_chamada-<data>.json` — a fixture de T008

> **A v0.1 rodou com sucesso em 2026-10-08** no Redmi Note 13: o app subiu, o listener conectou e as sondas registraram veredito. **A v0.2 compila e passa no lint, mas ainda não foi executada.**

> **Privacidade:** a fixture contém o nome e possivelmente o número de quem ligou. Trocar por um nome fictício antes de versionar.

## Próximo passo

A prioridade é **atender e desligar**. Gesto físico e timer ficam para depois.

1. Instalar a **v0.2** da branch [`spike`](../../tree/spike) e conceder as **três** permissões — acessibilidade agora é essencial, não opcional.
2. Apagar os vereditos da v0.1 para não misturar resultados.
3. Uma ligação com a cascata ligada responde **P-2** inteiro: qual das quatro estratégias atende.
4. Na mesma chamada, conferir **P-3** (desligar) e **P-4 / P-4b** (viva-voz).
5. Enviar o `atende-spike-log-*.txt` e o `whatsapp_chamada-*.json` para fechar **T008**.
6. Só então medir **P-1b**, **P-5** e **P-6**, decidir **C-5** e fechar a Fase 0.

Só então começa a Fase 1 (fundação do projeto Gradle) e, com ela, o primeiro teste vermelho.

## Decisões em aberto

As cinco da seção 6 da `spec.md` têm padrão adotado e todas viram opção configurável em `RF-08`. A única que depende do spike:

| # | Pergunta | Padrão adotado | Depende de |
| --- | --- | --- | --- |
| C-1 | Quem é atendido automaticamente? | Só contatos salvos | — |
| C-2 | Anunciar quem liga? | Sim | — |
| C-3 | Saída de áudio ao atender? | Viva-voz | — |
| C-4 | Atender videochamadas? | Não; apenas anunciar | — |
| C-5 | Qual gesto encerra a ligação? | Botão liga/desliga | **P-5 e P-6** |

## Riscos conhecidos

| Risco | Prob. | Impacto | Mitigação |
| --- | --- | --- | --- |
| Atualização do WhatsApp muda notificação ou rótulos | Média | Alto | Parser por categoria; rótulos configuráveis; teste de contrato |
| Android bloqueia disparo da ação em segundo plano | **Confirmado** | Alto | **Materializou-se em P-2.** Atender vai por acessibilidade |
| HyperOS encerra o serviço | Alta | Alto | Inicialização automática, bateria sem restrições, `requestRebind` |
| Gesto de volume não chega com tela apagada | Média | Médio | Opção nativa do botão liga/desliga |
| WhatsApp devolve o áudio ao fone de ouvido | **Confirmado** | Médio | **Materializou-se em P-4**, em até 20 s. Clique no botão de alto-falante |
| Golpista atendido automaticamente | Média | Médio | Modo "só contatos" como padrão |

## Anotações para fases futuras

- **Fase 6 (`RoteadorAudioAndroid`)** — o lint do Android avisa que quem chama `setCommunicationDevice` deve chamar `clearCommunicationDevice` ao fim da chamada. Observado ao compilar o spike.
- **Fase 7** — se P-6 der `SIM`, a implementação do gesto é dispensada; basta documentar a configuração (`tasks.md`, nota da Fase 7).

## Cronograma

Estimativa do `tasks.md`: **8 a 10 dias** para quem já conhece Android, do início ao endurecimento.

| Fase | Entrega | Dias |
| --- | --- | --- |
| 0 | Spike de validação — go/no-go | 1 |
| 1 | Fundação: módulos, qualidade, `check` verde | 0,5 |
| 2 | Domínio do atendimento automático (`RF-02`) | 1,5 |
| 3 | **Marco: problema principal resolvido** (`RF-01`, `RF-03`) | 1 |
| 4 | Configuração e persistência (`RF-08`) | 1 |
| 5 | Contatos e voz (`RF-04`, `RF-05`) | 1 |
| 6 | Viva-voz (`RF-06`) | 0,5 a 1 |
| 7 | Encerrar por gesto (`RF-07`) | 1 a 2 |
| 8 | Endurecimento (`RNF-01` a `RNF-04`) | 1 |
