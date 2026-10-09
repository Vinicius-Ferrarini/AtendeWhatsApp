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
**Nenhuma linha daqui vai para produção.**

**Funciona sem USB.** O app se auto-avalia, mostra o veredito de cada pergunta na
própria tela e exporta tudo pela folha de compartilhamento. O `adb` é opcional.

---

## v0.3 — o que a medicao de 09/10 ensinou

A v0.2 provou o essencial e expos duas falhas, uma delas de interface.

**Funcionou:** `P-2.4` — o clique por acessibilidade **atende**, confirmado pela chegada
da notificação contínua. `P-3` — desligar segue funcionando por `PendingIntent`.
`P-6` — o botão liga/desliga encerra chamada do WhatsApp, o que **decide a C-5**.

**Não funcionou, e por quê:**

| Sintoma | Causa real |
| --- | --- |
| `P-2.1`, `P-2.2`, `P-2.3` ficaram `NAO_TESTADO` | Os botões "Só P-2.x" da v0.2 pareciam ações, mas só *selecionavam* a estratégia. Apertar os quatro fixou a cascata na última, e as outras três nunca foram disparadas |
| `P-4b` não achou o botão de viva-voz | Rodou com a chamada ainda **tocando**. O inventário provou: `Recusar ligação \| Aceitar ligação \| Responder`. O botão de alto-falante só existe na tela de chamada **em andamento** |
| Auto-atender não disparou | `P-1a` ficou `NAO_TESTADO`, ou seja a avaliação da chamada tocando não rodou — e é no mesmo ponto que o auto-atender é agendado. Sem o arquivo de log não há como saber se foi configuração ou os vereditos apagados depois |

### O que mudou

1. **Configuração e ação separadas na tela.** Ações em MAIÚSCULAS; configuração com o prefixo `definir:`. Um painel no topo mostra o estado efetivo e diz `PRONTO PARA O TESTE` ou não.
2. **Auto-atender LIGADO por padrão**, e a migração de preferências **desfixa** a estratégia herdada da v0.2, devolvendo a cascata.
3. **Retentativas no clique por acessibilidade** — 6 tentativas a cada 900 ms. Uma tentativa única falha se a tela da chamada demorar a aparecer, e não dá para distinguir isso de "o clique não funciona".
4. **Wakelock durante a tentativa**, para o caso de a tela apagar no meio. Só durante a chamada, nunca entre chamadas (constituição IV).
5. **Diagnóstico de tela bloqueada** em cada clique: `bloqueada=`, `telaLigada=`, número de janelas e se o nó estava visível. `SEM JANELA acessível` passou a ser uma resposta distinta de "não achei o botão".
6. **Duas perguntas novas**, que são as que realmente importam:
   - **P-7** — o auto-atender disparou sozinho, sem ninguém tocar na tela
   - **P-8** — atendeu com o aparelho **bloqueado**
7. **Rótulos corrigidos** com o que o inventário mostrou. `responder` saiu da lista de atender: responde por mensagem, não atende a ligação.
8. O viva-voz agora **avisa** quando é acionado sem chamada em andamento.

---

## 1. Instalar

Baixar `atende-spike.apk` desta branch pelo navegador do proprio celular, abrir, e
permitir **fontes desconhecidas** para o app que abriu o arquivo. Se o MIUI / Play
Protect reclamar, escolher "Instalar mesmo assim".

Com cabo: `adb install -r atende-spike.apk`.

> Instalando sobre a v0.2, a migracao de preferencias **desfixa** a estrategia e
> **liga** o auto-atender. Os vereditos antigos continuam gravados: use
> **APAGAR VEREDITOS** antes do teste novo.

## 2. Conceder as tres permissoes (secao 1 da tela)

1. **Conceder acesso a notificacoes** -> marcar "Atende Spike"
2. **Ativar acessibilidade** -> ativar "Atende Spike - acessibilidade". **Essencial**, nao opcional: e a unica estrategia comprovada
3. **Permitir atender chamadas** -> conceder `ANSWER_PHONE_CALLS`

Nas configuracoes do app no HyperOS: bateria **sem restricoes** e **inicializacao
automatica**. Sem isso o sistema mata o servico e o resultado sai falso.

## 3. Conferir o painel ESTADO

O topo da tela precisa dizer **`>>> PRONTO PARA O TESTE <<<`**. Se disser o contrario,
alguma linha abaixo esta `PENDENTE`. O painel tambem mostra o que vale de fato:

```
auto-atender ... LIGADO, 10 s
estrategia ..... CASCATA (4 em ordem)
```

> Os botoes da secao 3 comecam com `definir:` porque **nao atendem nada** — so
> escolhem o que o auto-atender vai tentar. Foi essa confusao que invalidou tres
> estrategias na v0.2.

## 4. O teste que vale: auto-atender com a tela bloqueada

Nao toque na tela em nenhum momento. E isso que o app precisa fazer sozinho.

1. **APAGAR VEREDITOS** (secao 6).
2. Conferir no painel: auto-atender `LIGADO`, estrategia `CASCATA`.
3. Bloquear o celular e apagar a tela.
4. Ligar por WhatsApp (voz) do segundo celular.
5. **Deixar tocar ~25 s sem tocar em nada.**

O atraso de 10 s e depois a cascata, que gasta no pior caso ~11 s: tres disparos
unicos de 2 s e seis tentativas de clique a cada 900 ms.

Depois abrir o app e ler a secao 6. As linhas decisivas:

| Veredito | O que significa |
| --- | --- |
| **P-7 = SIM** | o auto-atender disparou sozinho, sem toque na tela |
| **P-8 = SIM** | atendeu com o aparelho **bloqueado** |
| **P-2** | qual das quatro estrategias venceu |
| `P-2.1` a `P-2.4` | o que cada uma fez, com diagnostico de tela e janelas |

Se quiser mais folga, use **definir: auto-atender LIGADO, 5 s**.

### Se falhar

Cada tentativa de clique registra `bloqueada=`, `telaLigada=`, quantas janelas havia e
se o no estava visivel. Tres leituras possiveis:

- **`SEM JANELA acessivel`** -> a acessibilidade nao alcanca a tela de chamada com o
  aparelho bloqueado. E o pior caso, e muda o desenho da producao
- **`nenhum no casou`** + lista de textos -> o botao existe com outro rotulo; o
  inventario mostra qual
- **`ACTION_CLICK devolveu false`** -> achou o botao e o clique nao foi aceito

## 5. Testar desligar

Com a chamada atendida, apertar **DESLIGAR AGORA**. Funciona por `PendingIntent`
(**P-3**, comprovado); a reserva por acessibilidade (**P-3b**) so e tentada se ele falhar.

Na mesma chamada **em andamento**, apertar **FORCAR VIVA-VOZ** para medir P-4 e P-4b.
Nao aperte com a chamada ainda tocando: o botao de alto-falante nao existe nessa tela,
e foi esse engano que invalidou o P-4b na v0.2. O app agora avisa no log.

## 6. Opcional

| Pergunta | O que fazer |
| --- | --- |
| **P-1b** | Ligar com o celular desbloqueado, em outro app |
| **P-5** | Durante a chamada, tela apagada, longe do ouvido, segurar **volume para baixo** 2 s |
| **P-6** | Ja respondido SIM. So repetir se quiser confirmar |

## 7. Exportar

Secao 6: **SALVAR FIXTURE**, depois **EXPORTAR E ENVIAR**. Grava em `Downloads` e abre
a folha de compartilhamento:

| Arquivo | Conteudo |
| --- | --- |
| `atende-spike-RESPOSTAS-<data>.txt` | veredito de cada pergunta |
| `atende-spike-log-<data>.txt` | log completo, com o passo a passo da cascata |
| `whatsapp_chamada-<data>.json` | a fixture de T008 |

**Mande os dois primeiros.** Na v0.2 so o RESPOSTAS chegou, e sem o log nao foi possivel
dizer por que o auto-atender nao disparou.

> **Privacidade:** a fixture contem o **nome e possivelmente o numero** de quem ligou, e
> o repositorio e **publico**. Trocar por um nome ficticio antes de levar para a `main`.

---

## 8. Opcional: com USB

```bash
adb logcat -s AtendeSpike

S="adb shell am broadcast -a br.atendepai.spike.CMD --es cmd"
$S dump                                     # despeja notificações ativas
$S autoanswer --ez on true --ei delay 10    # liga o atendimento automático
$S answer                                   # roda a cascata agora
$S hangup                                   # desliga agora
$S inventory                                # lista os textos visíveis nas janelas
$S speaker                                  # força viva-voz
$S audiostate                               # só lê o estado do áudio
$S savefixture                              # grava a fixture
```

## 9. Compilar

Precisa de **JDK 17** e **Android SDK** (platform 35 + build-tools). Android Studio é opcional.

```bash
# local.properties com sdk.dir=... na raiz
./gradlew assembleDebug
# saída: app/build/outputs/apk/debug/app-debug.apk
```

## 10. Limitações conhecidas

- `TelecomManager.acceptRingingCall()` está **deprecado** no API 35. Ainda funciona no Android 14, que é o alvo; se P-2.3 for a vencedora, a produção precisa checar a alternativa.
- O lint aponta `StaticFieldLeak` nas instâncias estáticas dos serviços. Aceitável aqui: as referências são anuladas em `onDestroy` / `onListenerDisconnected`, e o app é descartável.
- A v0.3 **compila e passa no lint, mas nunca foi executada**.

## 11. Registro final

Com as respostas em mão, preencher a seção 7.1 do `specs/001-atende-pai/plan.md` e a
decisão **C-5** da `spec.md` (T009). **Registrar só o que foi observado.**
