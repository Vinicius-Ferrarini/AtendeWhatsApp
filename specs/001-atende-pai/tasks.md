# Tarefas — Atende Pai

> Lista executável derivada de `spec.md`, `plan.md` e `tests.md`.
> Legenda: `[P]` = pode rodar em paralelo com outras `[P]` da mesma fase · `→ UT-08` = teste do `tests.md` que a tarefa faz passar.
> Regra (constituição II): a tarefa de teste vem **antes** da implementação e deve estar vermelha.

**Estimativa total:** 8 a 10 dias para quem já conhece Android.

---

## Fase 0 — Spike de validação (1 dia)

App descartável, fora do repositório principal. Nenhum código daqui vai para produção.

- [x] T001 Criar app de spike com `NotificationListenerService` que registra no Logcat todos os campos da notificação de `com.whatsapp`
- [~] T002 Responder P-1: chamada chega como `CATEGORY_CALL` com ações, bloqueado e desbloqueado? — **bloqueado medido (SIM); falta desbloqueado**
- [x] T003 Responder P-2: disparar o `PendingIntent` de "Atender" funciona no HyperOS?
- [x] T004 Responder P-3: existe notificação contínua com "Desligar"?
- [x] T005 [P] Responder P-4: viva-voz via `setCommunicationDevice` se mantém?
- [ ] T006 [P] Responder P-5: gesto de volume chega à acessibilidade com tela apagada?
- [ ] T007 [P] Responder P-6: opção nativa do botão liga/desliga encerra chamada do WhatsApp?
- [ ] T008 Salvar a notificação real como `fixtures/whatsapp_chamada.json`
- [ ] T009 Registrar respostas na seção 7 do `plan.md` e atualizar a decisão C-5 da `spec.md`

**Saída:** go / no-go e plano A ou B definido para cada ponto.

> **Parcial em 2026-10-08.** Respostas medidas na seção 7.1 do `plan.md`.
> Definido: atender vai por **acessibilidade** (P-2 falhou), desligar por
> **`PendingIntent`** (P-3 passou), viva-voz por **acessibilidade** (P-4 falhou).
> Faltam P-1b, P-5 e P-6 — e P-5/P-6 travam a decisão C-5 da `spec.md`.

## Fase 1 — Fundação (0,5 dia)

- [ ] T010 Criar projeto com módulos `:core` (JVM) e `:app` (Android), Gradle Kotlin DSL e version catalog
- [ ] T011 [P] Configurar ktlint, detekt e `explicitApi()` no `:core`
- [ ] T012 [P] Configurar JUnit 5 + coroutines-test + Turbine no `:core`; Robolectric no `:app`
- [ ] T013 Garantir que `./gradlew check` roda verde com projeto vazio
- [x] T014 Copiar `constitution.md` e specs para `.specify/` e `specs/001-atende-pai/`

## Fase 2 — Domínio: atendimento automático (1,5 dia) · RF-02

- [ ] T015 [P] Teste `AtrasoSegundos` → UT-01
- [ ] T016 [P] Teste padrões de `Config` → UT-02
- [ ] T017 Implementar `AtrasoSegundos` e `Config`
- [ ] T018 Criar ports (`AcoesChamada`, `Anunciador`, `Agenda`, `RoteadorAudio`, `RepositorioConfig`, `RegistroEventos`) e fakes de teste
- [ ] T019 Testes de agendamento → UT-08, UT-09, UT-10, UT-11
- [ ] T020 Implementar `MaquinaChamada` (estados Ocioso / Agendada / Ativa) até UT-08 a UT-11 passarem
- [ ] T021 Refatorar com testes verdes

## Fase 3 — Entrada Android: MVP no aparelho (1 dia) · RF-01, RF-03

- [ ] T022 Testes do parser com a fixture → IT-01, IT-02, IT-03
- [ ] T023 Implementar `ParserNotificacaoWhatsApp`
- [ ] T024 Implementar `AcoesChamadaNotificacao` (plano A, ou plano B se o spike exigir)
- [ ] T025 Implementar `ChamadaNotificationListener` delegando ao parser e à `MaquinaChamada`
- [ ] T026 Criar `AppContainer` e `Application`
- [ ] T027 Gerar APK assinado e executar MT-01, MT-02, MT-03, MT-05

**Marco:** com esta fase concluída, o problema principal está resolvido.

## Fase 4 — Configuração e persistência (1 dia) · RF-08

- [ ] T028 Teste de persistência → IT-06
- [ ] T029 Implementar `ConfigDataStore`
- [ ] T030 Teste de rótulo configurável → IT-04
- [ ] T031 Implementar `RegistroEventos` em memória (últimos 20)
- [ ] T032 [P] Testes de UI → UI-01, UI-02, UI-03
- [ ] T033 Implementar tela Compose + `ViewModel` com status de permissões e atalhos
- [ ] T034 Executar MT-08

## Fase 5 — Contatos e voz (1 dia) · RF-04, RF-05

- [ ] T035 [P] Testes da política → UT-03, UT-04, UT-05, UT-06, UT-07
- [ ] T036 Implementar `PoliticaAtendimento`
- [ ] T037 [P] Teste de detecção de número → IT-05
- [ ] T038 Implementar `AgendaContatos`
- [ ] T039 Testes de anúncio → UT-12, UT-13, UT-14, UT-15
- [ ] T040 Implementar anúncios na `MaquinaChamada` e `AnunciadorTts`
- [ ] T041 Executar MT-04, MT-10

## Fase 6 — Viva-voz (0,5 a 1 dia) · RF-06

- [ ] T042 Teste → UT-16
- [ ] T043 Implementar `RoteadorAudioAndroid` (plano conforme P-4)
- [ ] T044 Executar MT-01 conferindo o alto-falante

## Fase 7 — Encerrar por gesto (1 a 2 dias) · RF-07

> Pular a implementação se T007 mostrar que a opção nativa funciona; nesse caso, só documentar a configuração.

- [ ] T045 Testes → UT-18, UT-19, UT-20
- [ ] T046 Implementar transições de Ativa na `MaquinaChamada`
- [ ] T047 Teste → IT-07
- [ ] T048 Implementar captura do gesto no `AtendeAccessibilityService`
- [ ] T049 Executar MT-06, MT-07

## Fase 8 — Endurecimento (1 dia) · RNF-01 a RNF-04

- [ ] T050 Teste → UT-17 (falha do plano A aciona plano B)
- [ ] T051 Implementar fallback e registro de falhas
- [ ] T052 Implementar `requestRebind` quando o listener for desconectado
- [ ] T053 Revisar manifest: sem `INTERNET`, sem serviço em primeiro plano (RNF-04, RNF-01)
- [ ] T054 Configurar o Redmi conforme seção 9 do `plan.md`
- [ ] T055 Executar o roteiro manual completo, incluindo MT-09 (24 h)

---

## Definição de pronto (cada fase)

- [ ] Testes da fase verdes
- [ ] `./gradlew check` limpo
- [ ] Testes manuais da fase executados no Redmi Note 13
- [ ] `tests.md` e `tasks.md` atualizados
- [ ] Commit no padrão `tipo(RF-XX): descrição`
