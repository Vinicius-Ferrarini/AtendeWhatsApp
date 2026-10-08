# Plano técnico — Atende Pai

> **Como** implementar a `spec.md`, respeitando a `constitution.md`.

**Atualizado em:** 2026-10-07

---

## 1. Resumo técnico

App Android nativo em Kotlin, orientado a eventos. Um `NotificationListenerService` recebe a notificação de chamada do WhatsApp, o domínio decide se atende e quando, e adaptadores executam a ação (atender, falar, ligar viva-voz). Entre chamadas, nada roda.

## 2. Ambiente-alvo

| Item | Valor |
| --- | --- |
| Aparelho | Redmi Note 13 |
| Sistema | HyperOS, Android 14 ou superior (confirmar no aparelho) |
| minSdk | 29 |
| targetSdk | Versão estável mais recente |
| Distribuição | APK assinado, instalado direto no aparelho |

## 3. Stack

| Camada | Escolha | Motivo |
| --- | --- | --- |
| Linguagem e build | Kotlin 2.x, Gradle Kotlin DSL, version catalog (`libs.versions.toml`) | Padrão atual; versões num só lugar |
| Concorrência | Kotlin Coroutines + Flow | Atraso testável com relógio virtual; cancelamento estruturado |
| UI | Jetpack Compose + Material 3 | Uma única tela simples |
| Persistência | DataStore Preferences | Poucas chaves; assíncrono |
| Injeção de dependência | Manual (`AppContainer`) | Constituição VIII |
| Voz | `android.speech.tts.TextToSpeech`, pt-BR | Nativo e offline |
| Qualidade | ktlint + detekt | Constituição X |

## 4. Arquitetura

Ports and adapters em dois módulos Gradle.

```
               Fora do app
   ┌──────────────┐        ┌───────────────────────┐
   │  WhatsApp    │        │ Usuário (gesto físico) │
   └──────┬───────┘        └───────────┬───────────┘
          ▼                            ▼
 ┌─────────────────── :app — entrada ───────────────────┐
 │ NotificationListener          AccessibilityService    │
 │ (filtra com.whatsapp,         (gesto de volume e      │
 │  categoria call)               plano B de clique)     │
 └──────┬──────────────────────────────┬────────────────┘
        ▼                              ▼
 ┌──────────── :core — domínio, Kotlin puro ────────────┐
 │ PoliticaAtendimento ──elegível──▶ MaquinaChamada      │
 └──────┬───────────────────────────────┬───────────────┘
        ▼                               ▼
 ┌────────────────── :app — adaptadores ────────────────┐
 │ Agenda/Config   AcoesChamada   Anunciador   Roteador  │
 │                                (TTS)        Audio     │
 └──────────────────────────────────────────────────────┘
```

### 4.1 Ports (interfaces no `:core`)

```kotlin
public interface AcoesChamada {
    public suspend fun atender(chave: ChaveChamada): Resultado
    public suspend fun desligar(chave: ChaveChamada): Resultado
}
public interface Anunciador { public fun falar(texto: String) }
public interface Agenda { public suspend fun ehContato(nomeOuNumero: String): Boolean }
public interface RoteadorAudio { public suspend fun ligarVivaVoz(): Resultado }
public interface RepositorioConfig { public val config: Flow<Config> }
public interface RegistroEventos { public fun registrar(evento: EventoLog) }
```

### 4.2 Máquina de estados

Cada linha vira um teste da `MaquinaChamada` (ver `tests.md`).

| Estado atual | Evento | Próximo estado | Ação |
| --- | --- | --- | --- |
| Ocioso | Chamada de voz elegível | Agendada | Anuncia; agenda o atraso |
| Ocioso | Chamada não elegível | Ocioso | Anuncia; não atende |
| Agendada | Atraso venceu | Ativa | Atende; liga viva-voz; anuncia "Atendida" |
| Agendada | Notificação removida | Ocioso | Cancela o agendamento |
| Agendada | Atraso venceu, ação falhou | Agendada | Tenta plano B (clique); registra falha |
| Ativa | Gesto de encerrar | Ocioso | Desliga; anuncia "Ligação encerrada" |
| Ativa | Notificação contínua removida | Ocioso | Nenhuma |

### 4.3 Componentes Android

| Componente | Responsabilidade | Observações |
| --- | --- | --- |
| `ChamadaNotificationListener` | Recebe `onNotificationPosted/Removed`, filtra `com.whatsapp`, delega ao parser e à máquina | Sem regra de negócio; escopo de coroutine próprio, cancelado em `onListenerDisconnected` |
| `ParserNotificacaoWhatsApp` | `StatusBarNotification` → `ChamadaRecebida?` | Usa `category == CATEGORY_CALL` e as ações; rótulos vindos da config |
| `AcoesChamadaNotificacao` | Dispara o `PendingIntent` da ação "Atender"/"Desligar" | Plano A |
| `AtendeAccessibilityService` | Plano B de clique por texto; captura do gesto de volume (`FLAG_REQUEST_FILTER_KEY_EVENTS`) | Só age com chamada do WhatsApp em andamento |
| `AnunciadorTts` | `TextToSpeech` pt-BR | Inicialização preguiçosa |
| `RoteadorAudioAndroid` | `AudioManager.setCommunicationDevice` (API 31+) | Plano B: clique no botão de alto-falante |
| `AgendaContatos` | Consulta `ContactsContract` | Título parecido com número de telefone = desconhecido |
| `ConfigDataStore` | Implementa `RepositorioConfig` | |
| `AppContainer` | Monta o grafo de dependências | Criado no `Application` |

## 5. Estrutura do projeto

```
atende-pai/
├── .specify/memory/constitution.md
├── specs/001-atende-pai/
│   ├── spec.md
│   ├── plan.md
│   ├── tests.md
│   └── tasks.md
├── gradle/libs.versions.toml
├── core/                         # módulo JVM, Kotlin puro
│   └── src/
│       ├── main/kotlin/br/atendepai/core/
│       │   ├── model/            # ChamadaRecebida, Config, AtrasoSegundos, EstadoChamada
│       │   ├── policy/           # PoliticaAtendimento
│       │   ├── engine/           # MaquinaChamada
│       │   └── port/             # interfaces (4.1)
│       └── test/kotlin/…         # testes unitários + fakes
└── app/                          # módulo Android
    └── src/
        ├── main/kotlin/br/atendepai/app/
        │   ├── listener/
        │   ├── accessibility/
        │   ├── adapter/
        │   ├── data/
        │   ├── ui/
        │   └── di/AppContainer.kt
        ├── test/                 # Robolectric
        │   └── resources/fixtures/whatsapp_chamada.json
        └── androidTest/          # Compose UI Test
```

## 6. Boas práticas Kotlin adotadas

**Domínio**
- `data class` com `val`; nada mutável no `:core`.
- `sealed interface` para estados e eventos; `when` exaustivo.
- `@JvmInline value class AtrasoSegundos(val valor: Int)` com `require(valor in 0..60)`.
- Sem `!!`. Parser retorna `ChamadaRecebida?` ou resultado selado.
- `explicitApi()` no `:core`.

**Coroutines**
- Concorrência estruturada: `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` no serviço.
- Um `Job` por chamada, num mapa pela chave da notificação; remoção cancela o `Job`.
- Dispatcher e relógio injetados. Nunca `GlobalScope`.

**Android**
- Serviços só traduzem e delegam.
- Rótulos comparados normalizados (minúsculas, sem acento).
- Compose com state hoisting; `ViewModel` expõe `StateFlow`.

## 7. Pesquisa / Spike (Incremento 0)

Antes de qualquer código de produção, um app descartável responde:

| # | Pergunta | Se "não" |
| --- | --- | --- |
| P-1 | A chamada do WhatsApp chega como notificação `CATEGORY_CALL` com ações de atender/recusar, bloqueado e desbloqueado? | Detectar pela janela da chamada via acessibilidade |
| P-2 | Disparar o `PendingIntent` de "Atender" a partir do listener funciona no HyperOS? | Plano B: clique via acessibilidade |
| P-3 | Existe notificação contínua com ação "Desligar" durante a chamada? | Clique no botão vermelho via acessibilidade |
| P-4 | `setCommunicationDevice` mantém o viva-voz no WhatsApp? | Clique no botão de alto-falante |
| P-5 | O gesto de volume chega ao `AccessibilityService` com a tela apagada durante a chamada? | Usar a opção nativa do botão liga/desliga |
| P-6 | A opção nativa "Botão liga/desliga encerra chamada" funciona com o WhatsApp? | Gesto de volume (P-5) |

Saída obrigatória do spike: respostas registradas aqui e a notificação real salva em `fixtures/whatsapp_chamada.json`.

> **Atenção:** não detectar "tela apagou" para encerrar a ligação. O sensor de proximidade apaga a tela quando o celular encosta no ouvido e derrubaria a chamada.

### 7.1 Respostas medidas

Medido em **2026-10-08**, no Redmi Note 13 (HyperOS), com chamada de voz real do WhatsApp.
Fase 0 **incompleta**: três perguntas seguem sem medição.

| # | Resposta | Observado | Decisão |
| --- | --- | --- | --- |
| P-1a (bloqueado) | **SIM** | `category=call`, ação **`Aceitar`** com `PendingIntent`, título com o nome do contato, **`fullScreenIntent=false`** | Plano A: detectar por notificação |
| P-1b (desbloqueado) | — | não medido | pendente |
| P-2 | **NÃO** | `PendingIntent.send()` **não lança exceção e não tem efeito algum**. Falha também no disparo manual, fora do atraso automático | **Plano B: clique via acessibilidade** |
| P-3 | **SIM** | notificação contínua com ação `Desligar` e `PendingIntent`; **encerrou de verdade** | Plano A: disparar o `PendingIntent` |
| P-4 | **NÃO** | `setCommunicationDevice(BUILTIN_SPEAKER)` retorna `true`, mas o áudio volta para `BUILTIN_EARPIECE` em até 20 s | **Plano B: clique no botão de alto-falante** |
| P-5 | — | não medido | pendente |
| P-6 | — | não medido | pendente |

**A assimetria entre P-2 e P-3 é o achado central.** O mesmo código, na mesma notificação, encerra a chamada mas não a atende. A explicação mais provável: atender exige iniciar um serviço em primeiro plano com acesso ao **microfone**, e o Android 14 bloqueia isso quando a origem é um disparo programático em segundo plano — o `send()` é entregue, mas o WhatsApp não consegue completar a ação. Desligar não precisa de microfone, então passa. Um toque humano carrega a isenção de *background activity launch*; `PendingIntent.send()` não.

Consequências para a arquitetura:

- **Atender deixa de ser plano A.** O `AccessibilityService` passa de contingência a mecanismo principal de atendimento, porque o clique sintético conta como interação do usuário. Afeta o componente `AcoesChamadaNotificacao` (seção 4.3) e a tarefa T024.
- **Desligar continua plano A**, por `PendingIntent` — é o caminho mais simples e já comprovado.
- **Viva-voz também vai por acessibilidade**, clicando no botão de alto-falante dentro da tela da chamada (afeta T043).
- O rótulo real é **`Aceitar`**, não "Atender". Confirma a necessidade do RNF-06: rótulos em configuração, nunca fixos no código.
- `fullScreenIntent=false` com a tela bloqueada contraria a suposição inicial. A tela cheia da chamada vem por outro mecanismo, então não se deve depender desse campo para detectar o estado bloqueado.

Antes de aceitar o plano B como definitivo, duas tentativas de plano A ainda não exploradas valem um teste (ambas baratas):

1. `PendingIntent.send()` com `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` (API 34), que concede explicitamente a isenção de *background activity launch*.
2. `TelecomManager.acceptRingingCall()` com a permissão `ANSWER_PHONE_CALLS`, caso esta versão do WhatsApp registre as chamadas no Telecom.

## 8. Riscos

| Risco | Prob. | Impacto | Mitigação |
| --- | --- | --- | --- |
| Atualização do WhatsApp muda notificação ou rótulos | Média | Alto | Parser por categoria; rótulos configuráveis; teste de contrato; log de eventos |
| Android bloqueia disparo da ação em segundo plano | Média | Alto | Plano B por acessibilidade |
| HyperOS encerra o serviço | Alta | Alto | Inicialização automática, bateria sem restrições, `requestRebind`, status na tela |
| Gesto de volume não chega com tela apagada | Média | Médio | Opção nativa do botão liga/desliga |
| WhatsApp devolve o áudio ao fone de ouvido | Média | Médio | Clique no botão de alto-falante |
| Golpista atendido automaticamente | Média | Médio | Modo "só contatos" como padrão |

## 9. Configuração do aparelho (Redmi / HyperOS)

Os nomes dos menus variam entre versões; a tela de configuração do app abre os atalhos certos.

1. Instalar o APK assinado (permitir fontes desconhecidas para Arquivos ou navegador).
2. Conceder **acesso a notificações**.
3. Ativar o serviço de **acessibilidade** do Atende Pai.
4. Conceder permissão de **contatos**.
5. Ativar **inicialização automática**.
6. Bateria do app: **sem restrições**.
7. **Travar** o app nos recentes.
8. Opcional: testar **"Botão liga/desliga encerra chamada"** em Acessibilidade.
