# Status do projeto

**Atualizado em:** 2026-10-08

| | |
| --- | --- |
| **Fase atual** | Fase 0 — spike de validação |
| **Código de produção** | Nenhuma linha ainda (começa na Fase 1) |
| **Bloqueio** | Aguardando medições no Redmi Note 13 com ligações reais do WhatsApp |
| **Próximo passo** | Instalar o APK do spike no celular e rodar as 6 rodadas de medição |

---

## Onde o projeto está

A especificação está completa: requisitos, critérios de aceite, arquitetura, catálogo de testes e tarefas por fase. A constituição do projeto está ratificada.

Antes de escrever qualquer código de produção, a Fase 0 precisa responder **6 perguntas** sobre como o WhatsApp e o HyperOS se comportam de verdade (`plan.md`, seção 7). Cada resposta define se o app usa o plano A ou o plano B naquele ponto. Um app de spike descartável foi escrito e compilado para responder a essas perguntas; falta rodá-lo no aparelho.

**Nada foi medido ainda.** A tabela da seção 7 do `plan.md` segue em branco de propósito: só recebe observação real.

## O que já foi feito

### Especificação

- [x] Constituição ratificada (v1.0.0) — 10 princípios inegociáveis
- [x] `spec.md` — 5 histórias de usuário, 8 requisitos funcionais (`RF-01` a `RF-08`), 6 não funcionais, 5 decisões em aberto
- [x] `plan.md` — arquitetura ports and adapters, stack, máquina de estados, riscos, configuração do aparelho
- [x] `tests.md` — 20 testes unitários, 7 de integração, 3 de UI, 10 de aceitação manual, todos rastreados a um critério de aceite
- [x] `tasks.md` — 55 tarefas em 9 fases
- [x] **T014** — documentos movidos para `.specify/memory/` e `specs/001-atende-pai/`, os caminhos que o `CLAUDE.md` referencia *(tarefa da Fase 1, concluída fora de ordem)*

### Fase 0 — spike de validação

- [x] **T001** — app de spike escrito, compilado e empacotado em APK
- [ ] **T002** — P-1: chamada chega como `CATEGORY_CALL` com ações, bloqueado e desbloqueado?
- [ ] **T003** — P-2: disparar o `PendingIntent` de "Atender" funciona no HyperOS?
- [ ] **T004** — P-3: existe notificação contínua com ação "Desligar"?
- [ ] **T005** — P-4: viva-voz via `setCommunicationDevice` se mantém?
- [ ] **T006** — P-5: gesto de volume chega à acessibilidade com a tela apagada?
- [ ] **T007** — P-6: opção nativa do botão liga/desliga encerra chamada do WhatsApp?
- [ ] **T008** — salvar a notificação real como `fixtures/whatsapp_chamada.json`
- [ ] **T009** — registrar as respostas na seção 7 do `plan.md` e decidir C-5 na `spec.md`

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
| P-2 | O app marca `PARCIAL` quando o `PendingIntent` sai sem exceção; você confirma de ouvido num botão |
| P-3 | Automático, na notificação contínua da chamada em andamento |
| P-4 | Automático; só vira `SIM` se o viva-voz resistir às reconsultas de 2, 5, 10 e 20 s |
| P-5 | Automático quando a tecla de volume chega com a tela apagada |
| P-6 | Você registra num botão — é configuração do sistema, não dá para detectar |

O botão **EXPORTAR E ENVIAR** grava em `Downloads` e abre a folha de compartilhamento:

- `atende-spike-RESPOSTAS-<data>.txt` — veredito de P-1 a P-6, legível
- `atende-spike-log-<data>.txt` — log completo, com o JSON de cada notificação
- `whatsapp_chamada-<data>.json` — a fixture de T008

> **Ressalva:** o spike compila e passa no lint, mas **nunca foi executado**. Pode falhar no primeiro toque.

> **Privacidade:** a fixture contém o nome e possivelmente o número de quem ligou. Trocar por um nome fictício antes de versionar.

## Próximo passo

1. Instalar `atende-spike.apk` no Redmi — baixar da branch [`spike`](../../tree/spike) pelo próprio celular, ou mandar por WhatsApp/e-mail/Drive; abrir e permitir fontes desconhecidas.
2. Conceder acesso a notificações e ativar a acessibilidade, pelos botões da própria tela.
3. Rodar as 6 rodadas do roteiro — precisa de **um segundo celular** para ligar pelo WhatsApp.
4. Exportar os resultados e registrar as respostas na seção 7 do `plan.md` (T009).
5. Decidir **C-5** na `spec.md`: qual gesto encerra a ligação.
6. Dar o **go / no-go** de cada ponto: plano A ou plano B.

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
| Android bloqueia disparo da ação em segundo plano | Média | Alto | Plano B por acessibilidade |
| HyperOS encerra o serviço | Alta | Alto | Inicialização automática, bateria sem restrições, `requestRebind` |
| Gesto de volume não chega com tela apagada | Média | Médio | Opção nativa do botão liga/desliga |
| WhatsApp devolve o áudio ao fone de ouvido | Média | Médio | Clique no botão de alto-falante |
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
