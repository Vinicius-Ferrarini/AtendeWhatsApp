# Atende Pai

App Android que **atende sozinho as ligações do WhatsApp** depois de alguns segundos, anuncia por voz quem está ligando e permite encerrar a chamada sem olhar a tela.

Feito para uma pessoa cega que não usa leitor de tela e não consegue localizar o botão "Atender" das chamadas do WhatsApp — e por isso perdia ligações de família e amigos.

Uso pessoal. Aparelho-alvo: **Redmi Note 13 (HyperOS)**. Instalado via APK, sem Play Store.

> **Status:** especificado, ainda sem código de produção. A validação técnica (Fase 0) está em andamento. Veja [STATUS.md](STATUS.md).

---

## O que ele faz

1. Percebe a chegada de uma chamada de voz do WhatsApp, com o celular bloqueado ou em uso.
2. Anuncia em português: *"Ligação de Maria. Atendendo em 10 segundos."*
3. Espera o tempo configurado e **atende**, ligando o viva-voz.
4. Permite **encerrar por gesto físico**, sem procurar botão na tela.

E, igualmente importante, o que ele **não** faz:

- Não interfere em ligações telefônicas comuns nem em outros apps.
- Não atende números fora da agenda (padrão), para proteger de golpes.
- Não atende videochamadas — apenas anuncia.
- **Não acessa a internet.** O app não declara a permissão `INTERNET`; nenhum dado sai do aparelho.
- Não fica rodando em segundo plano: é orientado a eventos, sem polling nem serviço permanente.

## Princípios do projeto

O desenvolvimento segue **SDD** (Spec-Driven Development) e **TDD**, com regras registradas em [`.specify/memory/constitution.md`](.specify/memory/constitution.md). As que mais moldam o código:

| Princípio | Consequência prática |
| --- | --- |
| Spec antes de código | Nenhuma linha existe sem um requisito `RF-XX` que a justifique |
| Teste antes de implementação | Ciclo vermelho → verde → refatorar, sem exceção |
| Domínio puro | O módulo `:core` não pode importar `android.*` |
| Bateria zero entre chamadas | Proibido polling, serviço em primeiro plano e wakelock fora de chamada |
| Não interferir no que não é do WhatsApp | Na dúvida sobre a origem do evento, o app não age |
| Privacidade | Nenhum dado sai do aparelho |
| Acessível para quem não enxerga | Nenhum fluxo do usuário final exige olhar ou tocar na tela |
| Simplicidade | Sem framework de injeção de dependência; sem biblioteca sem uso claro |
| Resiliência a terceiros | Rótulos da interface do WhatsApp em configuração, nunca fixos no código |

## Tecnologias

| Camada | Escolha | Motivo |
| --- | --- | --- |
| Linguagem | Kotlin 2.x | Padrão atual do Android |
| Build | Gradle Kotlin DSL + version catalog | Versões num só lugar |
| Concorrência | Coroutines + Flow | Atraso testável com relógio virtual; cancelamento estruturado |
| UI | Jetpack Compose + Material 3 | Uma única tela de configuração |
| Persistência | DataStore Preferences | Poucas chaves, assíncrono |
| Injeção de dependência | Manual (`AppContainer`) | Constituição VIII |
| Voz | `android.speech.tts.TextToSpeech` (pt-BR) | Nativo e offline |
| Detecção de chamada | `NotificationListenerService` | Evento, não polling |
| Plano B de clique e gesto | `AccessibilityService` | Quando o `PendingIntent` não funcionar |
| Testes | JUnit 5, kotlinx-coroutines-test, Turbine, Robolectric | Pirâmide de testes |
| Qualidade | ktlint + detekt | Constituição X |

`minSdk` 29 · `targetSdk` na versão estável mais recente.

## Arquitetura

Ports and adapters em dois módulos Gradle. O domínio decide; o Android apenas traduz eventos e executa ações.

```
   WhatsApp                      Usuário (gesto físico)
       │                                  │
       ▼                                  ▼
┌─────────────────── :app — entrada ──────────────────┐
│ NotificationListener          AccessibilityService   │
│ (filtra com.whatsapp,         (gesto de volume e     │
│  categoria call)               plano B de clique)    │
└──────┬─────────────────────────────┬────────────────┘
       ▼                             ▼
┌──────────── :core — domínio, Kotlin puro ───────────┐
│ PoliticaAtendimento ──elegível──▶ MaquinaChamada     │
└──────┬──────────────────────────────┬───────────────┘
       ▼                              ▼
┌────────────────── :app — adaptadores ───────────────┐
│ Agenda/Config   AcoesChamada   Anunciador   Roteador │
│                                (TTS)        Áudio    │
└─────────────────────────────────────────────────────┘
```

A máquina de estados (`Ocioso` → `Agendada` → `Ativa`) está detalhada na seção 4.2 do [`plan.md`](specs/001-atende-pai/plan.md).

## Estrutura do projeto

Hoje o repositório contém só a especificação:

```
AtendeWhatsApp/
├── CLAUDE.md                        # instruções de trabalho para o agente
├── README.md                        # este arquivo
├── STATUS.md                        # onde o projeto está e qual o próximo passo
├── .specify/memory/
│   └── constitution.md              # princípios inegociáveis
└── specs/001-atende-pai/
    ├── spec.md                      # o quê e por quê — requisitos e critérios de aceite
    ├── plan.md                      # como — arquitetura, stack, máquina de estados
    ├── tests.md                     # catálogo de testes e roteiro manual
    └── tasks.md                     # tarefas em ordem, por fase
```

A partir da Fase 1 ele passa a ter:

```
├── gradle/libs.versions.toml
├── core/                            # módulo JVM, Kotlin puro, sem android.*
│   └── src/main/kotlin/br/atendepai/core/
│       ├── model/                   # ChamadaRecebida, Config, AtrasoSegundos
│       ├── policy/                  # PoliticaAtendimento
│       ├── engine/                  # MaquinaChamada
│       └── port/                    # interfaces dos adaptadores
└── app/                             # módulo Android
    └── src/
        ├── main/kotlin/br/atendepai/app/
        │   ├── listener/ accessibility/ adapter/ data/ ui/ di/
        ├── test/                    # Robolectric
        │   └── resources/fixtures/whatsapp_chamada.json
        └── androidTest/             # Compose UI Test
```

O pacote Kotlin é `br.atendepai`.

## Documentação

| Para saber | Leia |
| --- | --- |
| O que o app deve fazer e por quê | [`specs/001-atende-pai/spec.md`](specs/001-atende-pai/spec.md) |
| Como será implementado | [`specs/001-atende-pai/plan.md`](specs/001-atende-pai/plan.md) |
| Quais testes existem e o roteiro manual | [`specs/001-atende-pai/tests.md`](specs/001-atende-pai/tests.md) |
| O que fazer, em ordem | [`specs/001-atende-pai/tasks.md`](specs/001-atende-pai/tasks.md) |
| Regras que nada pode violar | [`.specify/memory/constitution.md`](.specify/memory/constitution.md) |
| Onde o projeto está agora | [`STATUS.md`](STATUS.md) |

## Comandos

Disponíveis a partir da Fase 1, quando o projeto Gradle existir:

```bash
./gradlew :core:test                 # testes unitários do domínio (rápidos)
./gradlew :app:testDebugUnitTest     # testes Robolectric
./gradlew check                      # testes + ktlint + detekt
./gradlew assembleDebug              # APK de debug
./gradlew installDebug               # instala no celular conectado via USB
./gradlew assembleRelease            # APK assinado (precisa de keystore.properties)
adb logcat -s AtendeWhatsApp         # logs do app no celular
```

## Ambiente de desenvolvimento

Precisa de **JDK 17** e **Android SDK** (platform 35 + build-tools). Android Studio é opcional — o projeto compila por linha de comando.

O caminho do SDK vai em `local.properties` (`sdk.dir=...`), que não é versionado.

## Assinatura do APK de release

A keystore fica **fora do repositório**. Um `keystore.properties` na raiz (também fora do git) informa `storeFile`, `storePassword`, `keyAlias` e `keyPassword`. Se o arquivo não existir, o build de release falha com mensagem clara e o de debug continua funcionando.

## Configuração do aparelho

O HyperOS precisa de ajustes manuais para o app sobreviver: acesso a notificações, serviço de acessibilidade, permissão de contatos, inicialização automática, bateria sem restrições e travar o app nos recentes. A lista completa está na seção 9 do [`plan.md`](specs/001-atende-pai/plan.md); a tela de configuração do app abre os atalhos certos.

## Fora de escopo

Ligações telefônicas comuns · WhatsApp Business · iOS · leitura de tela em geral · chamadas em grupo · publicação na Play Store.
