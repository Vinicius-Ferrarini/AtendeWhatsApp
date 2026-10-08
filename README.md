> **Esta é a branch `spike`, órfã e sem relação com a `main`.**
>
> Ela guarda o app descartável da Fase 0, que valida como o WhatsApp e o HyperOS se
> comportam de verdade antes de existir código de produção. O `tasks.md` manda manter
> este código fora do projeto principal — a branch órfã cumpre isso e ainda preserva o
> trabalho. **Nada daqui vai para a `main`.**
>
> O APK já compilado está em `atende-spike.apk`, pronto para baixar direto no celular.

# Atende Spike — Fase 0 (descartável)

App de validação do `specs/001-atende-pai/plan.md` seção 7.
**Nenhuma linha daqui vai para produção.** Fica fora do repositório `AtendeWhatsApp`
de propósito (Fase 0 do `tasks.md`).

Responde P-1 a P-6 e produz a fixture `whatsapp_chamada.json` (T008).

**Funciona sem USB.** O app se auto-avalia, mostra o veredito de cada pergunta na
própria tela e exporta tudo pela folha de compartilhamento. O `adb` é opcional.

---

## 1. Gerar o APK

Precisa, na máquina de quem compila: **JDK 17** e **Android SDK** (platform 35 +
build-tools). Não precisa de Android Studio.

```bash
gradle wrapper          # uma vez, para criar o wrapper (o .jar é binário, não vai no git)
./gradlew assembleDebug
# saída: app/build/outputs/apk/debug/app-debug.apk
```

O APK de debug é assinado com a chave de debug automática — serve para instalar
direto, sem keystore.

## 2. Instalar no Redmi (sem cabo)

1. Mandar o `app-debug.apk` para o celular (WhatsApp para si mesmo, e-mail, Drive).
2. Abrir o arquivo. O HyperOS vai pedir para **permitir instalação de fontes
   desconhecidas** para o app que está abrindo o arquivo — permitir.
3. Se aparecer a varredura do **MIUI Optimization / Play Protect**, escolher
   "Instalar mesmo assim".

Com cabo, em vez disso: `adb install -r app-debug.apk`.

## 3. Conceder as permissões (uma vez)

Abrir o **Atende Spike** e usar os botões da seção 1 da tela:

- **Conceder acesso a notificações** → marcar "Atende Spike" na lista
- **Ativar acessibilidade (P-5)** → ativar "Atende Spike — acessibilidade"

O topo da tela mostra `OK` ou `PENDENTE` para cada uma. Também vale ajustar, nas
configurações do app no HyperOS: bateria **sem restrições** e **inicialização automática**.

---

## 4. Roteiro das perguntas

Precisa de **um segundo celular** para ligar pelo WhatsApp. Faça nesta ordem — cada
rodada depende da anterior. Tudo pela tela do app; nenhum comando necessário.

### Rodada 1 — P-1a, bloqueado

1. Na tela: **Desligar auto-atender (só observar)**.
2. Bloquear o aparelho e apagar a tela.
3. Ligar pelo WhatsApp (**voz**) de um número **salvo na agenda**.
4. Deixar tocar até cair. Não tocar na tela.
5. Abrir o app e ler a seção 5: `P-1a` deve estar `SIM`.

`NAO` em P-1a significa que não há ação de atender utilizável na notificação →
plano B: detectar pela janela da chamada via acessibilidade.

### Rodada 2 — P-1b, desbloqueado

Repetir com o aparelho **desbloqueado e em outro app** (aviso flutuante).
Conferir `P-1b`.

### Rodada 3 — P-2, atender (o teste central)

1. Na tela: **Ligar auto-atender (10s)**.
2. Bloquear, apagar a tela, ligar do segundo celular.
3. **Escutar.** O app registra `P-2 = PARCIAL` quando o `PendingIntent` sai sem
   exceção — isso não prova que atendeu.
4. Confirmar o que só você sabe, na seção 4 da tela:
   **"P-2: a chamada FOI atendida"** ou **"P-2: NÃO foi atendida"**.

### Rodada 4 — P-3 e P-4, na mesma ligação da rodada 3

Com a chamada atendida e em andamento, o app avalia sozinho:

- `P-3` — a notificação contínua tem ação de desligar
- `P-4` — o viva-voz foi forçado e **se manteve** nas reconsultas de 2, 5, 10 e 20 s

P-4 só vira `SIM` se o alto-falante resistir aos 20 s; se o WhatsApp devolver o áudio
ao ouvido em qualquer reconsulta, vira `NAO` → plano B: clicar no botão de alto-falante.

Para fechar P-3, apertar **Desligar agora (P-3)** e ver se encerra.

### Rodada 5 — P-5, gesto de volume com a tela apagada

Durante uma chamada atendida, tela apagada, aparelho **longe do ouvido**:
segurar **volume para baixo** por ~2 s.

`P-5 = SIM` aparece sozinho se a tecla chegou ao serviço de acessibilidade com a
tela apagada. Nada mudou → `P-5` fica `NAO_TESTADO` → usar a opção nativa (P-6).

> O app **nunca consome** a tecla: o volume continua funcionando normalmente.

### Rodada 6 — P-6, opção nativa

Ajustes → Acessibilidade → **"Botão liga/desliga encerra chamada"** (o nome varia):
ativar. Durante uma chamada **do WhatsApp** atendida, pressionar liga/desliga.

Registrar na seção 4 da tela: **"P-6: liga/desliga ENCERROU"** ou **"NÃO encerrou"**.

---

## 5. Exportar os resultados

Na seção 5 da tela:

1. **Salvar fixture (T008)** — grava a última notificação de chamada capturada.
2. **EXPORTAR E ENVIAR** — grava três arquivos em `Downloads` e abre a folha de
   compartilhamento:

| Arquivo | Conteúdo |
| --- | --- |
| `atende-spike-RESPOSTAS-<data>.txt` | veredito de P-1 a P-6, legível |
| `atende-spike-log-<data>.txt` | log completo, com o JSON de cada notificação |
| `whatsapp_chamada-<data>.json` | a fixture de T008 |

Mande por WhatsApp, e-mail ou Drive. Os arquivos também ficam em `Downloads` para
pegar com qualquer gerenciador de arquivos.

> **Antes de versionar a fixture:** ela contém o **nome e possivelmente o número** de
> quem ligou. Trocar por um nome fictício. Na Fase 3 ela vai para
> `app/src/test/resources/fixtures/whatsapp_chamada.json` e passa a sustentar IT-01.

---

## 6. Opcional: com USB

Se houver cabo e depuração USB, dá para acompanhar ao vivo e controlar sem tocar na tela:

```bash
adb logcat -s AtendeSpike

S="adb shell am broadcast -a br.atendepai.spike.CMD --es cmd"
$S dump                                     # despeja notificações ativas
$S autoanswer --ez on true --ei delay 10    # liga o atendimento automático
$S answer                                   # dispara "Atender" agora        (P-2)
$S hangup                                   # dispara "Desligar" agora       (P-3)
$S speaker                                  # força viva-voz                 (P-4)
$S audiostate                               # só lê o estado do áudio
$S savefixture                              # grava a fixture                (T008)
```

Sem compilar nada, só com `platform-tools`, ainda é possível fechar P-1 e parte de P-3:

```bash
powershell -ExecutionPolicy Bypass -File capturar.ps1 -Rotulo p1-bloqueado
```

Rodar **com a chamada tocando**. Usa `adb shell dumpsys notification --noredact`.
Não resolve P-2, P-4 nem P-5 — esses exigem o APK instalado.

---

## 7. Registro final

Com as respostas em mão, preencher a tabela da seção 7 do
`specs/001-atende-pai/plan.md` e a decisão **C-5** da `spec.md` (T009).
**Registrar só o que foi observado.**
