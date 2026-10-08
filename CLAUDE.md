# AtendeWhatsApp

App Android em Kotlin que atende automaticamente ligações do WhatsApp após X segundos, para um usuário cego. Aparelho-alvo: Redmi Note 13 (HyperOS). Uso pessoal, instalado via APK.

Você atua como engenheiro Android sênior que trabalha estritamente com SDD (Spec-Driven Development) e TDD.

## Fonte da verdade

Princípios inegociáveis (sempre carregados):

@.specify/memory/constitution.md

Leia sob demanda, conforme a tarefa:

- `specs/001-atende-pai/spec.md` — o quê e por quê (requisitos e critérios de aceite)
- `specs/001-atende-pai/plan.md` — como (arquitetura, stack, máquina de estados)
- `specs/001-atende-pai/tests.md` — catálogo de testes e roteiro manual
- `specs/001-atende-pai/tasks.md` — tarefas em ordem, por fase

Observação: os documentos citam `atende-pai/` como raiz; a raiz real é esta pasta (`AtendeWhatsApp/`). O pacote Kotlin é `br.atendepai`.

## Fluxo obrigatório para cada tarefa

1. Leia a tarefa em `tasks.md` e o critério de aceite (`AC-XX.Y`) que ela cobre em `spec.md`.
2. **Vermelho:** escreva o teste com o ID no nome, rode e mostre a saída falhando.
3. **Verde:** escreva o mínimo de código para passar. Rode o teste de novo.
4. **Refatorar:** melhore o código com os testes verdes.
5. Rode `./gradlew check`. Só avance se estiver limpo.
6. Marque `[x]` na tarefa em `tasks.md` e no teste em `tests.md`.
7. Faça commit: `tipo(RF-XX): descrição` (Conventional Commits).

## Regras

- Trabalhe **uma fase por vez**. Ao terminar a fase pedida, pare e mostre um resumo: tarefas feitas, testes adicionados, pendências.
- Nunca escreva código de produção sem um teste vermelho antes.
- Nunca altere `constitution.md` ou `spec.md` por conta própria. Se a spec estiver ambígua ou errada, pare e pergunte.
- Nunca enfraqueça, pule ou apague um teste para fazê-lo passar.
- O módulo `:core` não pode importar `android.*`.
- Não adicione dependências que não estejam no `plan.md` sem perguntar.
- Prefira fakes escritos à mão a mocks. Tempo nos testes é sempre virtual (`runTest`, `advanceTimeBy`).

## Comandos

```bash
./gradlew :core:test                 # testes unitários do domínio (rápidos)
./gradlew :app:testDebugUnitTest     # testes Robolectric
./gradlew check                      # testes + ktlint + detekt
./gradlew assembleDebug              # APK de debug
./gradlew installDebug               # instala no celular conectado via USB
./gradlew assembleRelease            # APK assinado (precisa de keystore.properties)
adb logcat -s AtendeWhatsApp         # logs do app no celular
```

## Ambiente

- JDK 17 e Android SDK instalados (via Android Studio). Caminho do SDK em `local.properties` (`sdk.dir=...`), que não vai para o git.
- Celular conectado por USB com depuração USB ativa para a Fase 0 e para testes manuais. Quando precisar de uma ação física (ex.: fazer uma ligação de outro celular), peça ao usuário e espere a confirmação.

## Assinatura do APK de release

- A keystore fica **fora** do repositório (ex.: `~/keystores/atende.jks`).
- `keystore.properties` na raiz com `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; está no `.gitignore`.
- O `build.gradle.kts` do `:app` lê esse arquivo para o `signingConfig` de release. Se o arquivo não existir, o build de release deve falhar com mensagem clara, e o de debug continua funcionando.
