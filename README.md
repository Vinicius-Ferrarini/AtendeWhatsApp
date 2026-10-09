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

## v0.2 — o que mudou e por quê

A medição de 2026-10-08 (v0.1) deu um resultado decisivo:

| | |
| --- | --- |
| **Desligar** | funciona por `PendingIntent` |
| **Atender** | **não funciona** por `PendingIntent` — `send()` não lança exceção e não tem efeito |

Mesmo código, mesma notificação, resultados opostos. A hipótese: atender exige iniciar
um serviço em primeiro plano com acesso ao **microfone**, e o Android 14 nega isso a um
disparo programático em segundo plano. Desligar não precisa de microfone, então passa.
Um toque humano carrega a isenção de *background activity launch*; `send()` não.

A v0.2 ataca essa hipótese por **quatro ângulos**, em cascata:

| ID | Estratégia | Aposta |
| --- | --- | --- |
| P-2.1 | `PendingIntent` simples | linha de base — já sabemos que falha; serve de controle |
| P-2.2 | `PendingIntent` + `setPendingIntentBackgroundActivityStartMode(ALLOWED)` | concede explicitamente a isenção que falta (API 34) |
| P-2.3 | `TelecomManager.acceptRingingCall()` | se o WhatsApp registra as chamadas no Telecom, é o caminho mais limpo |
| P-2.4 | Clique no botão por acessibilidade | o clique sintético conta como interação do usuário |

**O sucesso agora é detectado sozinho.** A notificação contínua do WhatsApp só existe
depois da chamada atendida — isso foi comprovado em P-3. A cascata usa essa chegada como
sinal objetivo: dispara uma estratégia, espera 2,5 s, e se a notificação contínua
aparecer, aquela estratégia funcionou. **P-2 deixou de depender do seu ouvido.**

Outras mudanças:

- Desligar ganhou reserva por acessibilidade (**P-3b**), caso o `PendingIntent` pare de funcionar
- Viva-voz ganhou reserva por acessibilidade (**P-4b**), já que P-4 falhou
- Botão **Inventariar textos da tela**, que lista tudo que a acessibilidade vê — foi assim que se descobriu que o botão se chama `Aceitar`, não "Atender"
- O rótulo `Aceitar` passou a ser o primeiro da lista de busca

O gesto de volume (**P-5**) e o botão liga/desliga (**P-6**) continuam medidos, mas
saíram do caminho principal: primeiro atender e desligar têm que funcionar.

---

## 1. Instalar

Baixar `atende-spike.apk` desta branch pelo navegador do próprio celular, abrir, e
permitir **fontes desconhecidas** para o app que abriu o arquivo. Se o MIUI / Play
Protect reclamar, escolher "Instalar mesmo assim".

Com cabo: `adb install -r atende-spike.apk`.

> Instalando **sobre a v0.1**, os vereditos anteriores continuam gravados. Use
> **Apagar vereditos e começar de novo** antes da primeira ligação, para não misturar
> resultado velho com novo.

## 2. Conceder as três permissões

Pelos botões da seção 1 da tela:

1. **Conceder acesso a notificações** → marcar "Atende Spike"
2. **Ativar acessibilidade** → ativar "Atende Spike — acessibilidade" *(agora é essencial, não opcional: P-2.4 e P-3b dependem dela)*
3. **Permitir atender chamadas (P-2.3)** → conceder `ANSWER_PHONE_CALLS`

A seção 1 deve mostrar `OK` em todas as linhas, inclusive **Acessibilidade em execução**.
A seção 2 lista as quatro estratégias e diz se cada uma está `pronta` ou indisponível.

Nas configurações do app no HyperOS: bateria **sem restrições** e **inicialização
automática**. Sem isso o sistema mata o serviço e o resultado sai falso.

## 3. Testar atender — uma ligação resolve

1. Seção 2: deixar em **Cascata** (padrão).
2. Seção 3: **Ligar auto-atender (10s)**.
3. Bloquear o celular, apagar a tela.
4. Ligar por WhatsApp (voz) do segundo celular. **Não tocar na tela.**
5. Deixar tocar até a cascata terminar — cerca de 20 s no total.

A cascata gasta até 10 s depois do atraso de 10 s, então a ligação precisa tocar
~25 s. Se o chamador desistir antes, a cascata é cancelada e o log registra isso.

Abrir o app e ler a seção 6. O resultado útil é a linha do **P-2**: ela diz
**qual estratégia atendeu**. As linhas `P-2.1` a `P-2.4` mostram o que cada uma fez.

> **Importante:** não atenda na mão durante o teste. Se você tocar em `Aceitar`, a
> notificação contínua aparece e a cascata credita o sucesso à estratégia que estava
> correndo naquele instante. O log tem o horário de cada disparo, mas é mais simples
> não interferir.

### Se nenhuma funcionar

Aperte **Inventariar textos da tela** durante a chamada tocando e exporte. O inventário
diz se a acessibilidade está mesmo vendo a tela da chamada e com que rótulos. Sem isso
não há como saber se P-2.4 falhou por não achar o botão ou por o clique não ter efeito.

## 4. Testar desligar

Com a chamada atendida, apertar **DESLIGAR agora**. Já funcionava na v0.1 por
`PendingIntent` (**P-3**); a reserva por acessibilidade (**P-3b**) só é tentada se o
`PendingIntent` falhar.

Nessa mesma chamada, **P-4** e **P-4b** (viva-voz) se preenchem sozinhos.

## 5. Opcional: gesto e botão

| Pergunta | O que fazer |
| --- | --- |
| **P-1b** | Ligar com o celular desbloqueado, em outro app |
| **P-5** | Durante a chamada, tela apagada, longe do ouvido, segurar **volume para baixo** 2 s |
| **P-6** | Ativar "Botão liga/desliga encerra chamada" em Acessibilidade, testar, e registrar no botão da seção 5 |

## 6. Exportar

Seção 6: **Salvar fixture (T008)**, depois **EXPORTAR E ENVIAR**. Grava em `Downloads`
e abre a folha de compartilhamento:

| Arquivo | Conteúdo |
| --- | --- |
| `atende-spike-RESPOSTAS-<data>.txt` | veredito de cada pergunta |
| `atende-spike-log-<data>.txt` | log completo, com o JSON das notificações e o passo a passo da cascata |
| `whatsapp_chamada-<data>.json` | a fixture de T008 |

> **Privacidade:** a fixture contém o **nome e possivelmente o número** de quem ligou, e
> o repositório é **público**. Trocar por um nome fictício antes de levar para a `main`.

---

## 7. Opcional: com USB

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

## 8. Compilar

Precisa de **JDK 17** e **Android SDK** (platform 35 + build-tools). Android Studio é opcional.

```bash
# local.properties com sdk.dir=... na raiz
./gradlew assembleDebug
# saída: app/build/outputs/apk/debug/app-debug.apk
```

## 9. Limitações conhecidas

- `TelecomManager.acceptRingingCall()` está **deprecado** no API 35. Ainda funciona no Android 14, que é o alvo; se P-2.3 for a vencedora, a produção precisa checar a alternativa.
- O lint aponta `StaticFieldLeak` nas instâncias estáticas dos serviços. Aceitável aqui: as referências são anuladas em `onDestroy` / `onListenerDisconnected`, e o app é descartável.
- A v0.2 **compila e passa no lint, mas nunca foi executada**.

## 10. Registro final

Com as respostas em mão, preencher a seção 7.1 do `specs/001-atende-pai/plan.md` e a
decisão **C-5** da `spec.md` (T009). **Registrar só o que foi observado.**
