# Spec — Atende Pai

> **O quê** e **por quê**. Sem decisões técnicas (essas ficam em `plan.md`).
> Cada critério de aceite vira um ou mais testes em `tests.md`.

**Status:** Rascunho · **Atualizada em:** 2026-10-07

---

## 1. Contexto

O usuário final é cego, não usa leitor de tela e não consegue localizar o botão "Atender" das ligações do WhatsApp. Ele perde ligações de família e amigos.

O Atende Pai atende sozinho as ligações do WhatsApp depois de um tempo configurável, avisa por voz quem está ligando e permite encerrar a ligação sem olhar a tela.

## 2. Atores

| Ator | Descrição |
| --- | --- |
| Usuário final | Pai, cego, sem leitor de tela. Não interage com a tela do app. |
| Administrador | Filho(a). Instala, configura e mantém o app. |
| Chamador | Qualquer pessoa que liga pelo WhatsApp. |

## 3. Histórias de usuário

- **HU-1** — Como usuário final, quero que as ligações do WhatsApp sejam atendidas sozinhas, para não perder ligações.
- **HU-2** — Como usuário final, quero ouvir quem está ligando antes de atender, para saber com quem vou falar.
- **HU-3** — Como usuário final, quero encerrar a ligação com um gesto físico, sem procurar botão na tela.
- **HU-4** — Como administrador, quero ajustar o tempo e as regras de atendimento, para adaptar ao uso do meu pai.
- **HU-5** — Como administrador, quero que números desconhecidos não sejam atendidos, para proteger meu pai de golpes.

## 4. Requisitos funcionais

### RF-01 — Detectar chamada recebida do WhatsApp (HU-1)

- **AC-01.1** Dado uma chamada de voz chegando pelo WhatsApp, quando o aviso de chamada aparece, então o app reconhece a chamada com nome do chamador e tipo (voz ou vídeo).
- **AC-01.2** Dado uma ligação telefônica comum, quando ela chega, então o app não faz nada.
- **AC-01.3** Dado uma mensagem do WhatsApp (não chamada), quando ela chega, então o app não faz nada.

### RF-02 — Atender após atraso configurável (HU-1)

- **AC-02.1** Dado atraso de 10 s e chamada elegível, quando se passam 10 s com a chamada ainda tocando, então o app atende uma única vez.
- **AC-02.2** Dado que a chamada para de tocar antes do prazo (chamador desistiu ou chamada recusada), quando isso acontece, então o app não atende.
- **AC-02.3** O atraso aceita valores de 0 a 60 s. O padrão é 10 s. Valores fora da faixa são rejeitados.

### RF-03 — Funcionar bloqueado e desbloqueado (HU-1)

- **AC-03.1** Dado o celular bloqueado (chamada em tela cheia), quando chega uma chamada elegível, então o comportamento é igual ao RF-02.
- **AC-03.2** Dado o celular desbloqueado e em uso (aviso flutuante "Atender / Recusar"), quando chega uma chamada elegível, então o comportamento é igual ao RF-02.

### RF-04 — Filtrar por contatos (HU-5)

- **AC-04.1** Dado o modo "só contatos" e um chamador que não está na agenda, quando a chamada chega, então o app anuncia "Número desconhecido" e não atende.
- **AC-04.2** Dado o modo "só contatos" e um chamador salvo na agenda, quando a chamada chega, então a chamada é elegível.
- **AC-04.3** Dado o modo "qualquer número", então toda chamada de voz é elegível.

### RF-05 — Anunciar quem liga (HU-2)

- **AC-05.1** Dado o anúncio ligado, quando uma chamada elegível chega, então o app fala em português: "Ligação de {nome}. Atendendo em {X} segundos."
- **AC-05.2** Quando o app atende, então fala "Atendida".
- **AC-05.3** Quando o app encerra a ligação, então fala "Ligação encerrada".
- **AC-05.4** Dado o anúncio desligado, então o app não fala nada.

### RF-06 — Ativar viva-voz (HU-1)

- **AC-06.1** Dado o viva-voz ligado, quando a chamada é atendida pelo app, então o áudio sai pelo alto-falante em até 2 s.

### RF-07 — Encerrar ligação ativa por gesto (HU-3)

- **AC-07.1** Dado uma chamada ativa do WhatsApp, quando o usuário executa o gesto configurado, então a ligação é encerrada.
- **AC-07.2** Dado nenhuma chamada ativa do WhatsApp, quando o gesto é executado, então o botão mantém o comportamento normal.
- **AC-07.3** O gesto nunca encerra uma ligação telefônica comum.

### RF-08 — Configuração pelo administrador (HU-4)

- **AC-08.1** Uma tela permite: ligar/desligar o app, ajustar o atraso, escolher o modo de contatos, ligar/desligar anúncio, viva-voz e gesto.
- **AC-08.2** As configurações sobrevivem a reiniciar o aparelho.
- **AC-08.3** A tela mostra o estado de cada permissão necessária, com atalho para corrigir.
- **AC-08.4** A tela mostra os últimos 20 eventos (recebida, atendida, ignorada, falha) com horário.

## 5. Requisitos não funcionais

| ID | Requisito | Critério de aceite |
| --- | --- | --- |
| RNF-01 | Consumo de bateria desprezível | Após 24 h, o app não aparece entre os consumidores relevantes na tela de bateria |
| RNF-02 | Precisão do atraso | Atendimento com erro máximo de 1 s em relação ao atraso configurado |
| RNF-03 | Volta sozinho após reinício | Após reboot, atende sem o administrador abrir o app |
| RNF-04 | Privacidade | Nenhum dado sai do aparelho |
| RNF-05 | Isolamento | Ligações comuns e outros apps nunca são afetados |
| RNF-06 | Tolerância a mudanças do WhatsApp | Mudança de texto dos botões é corrigida por configuração, sem nova versão do app |

## 6. Esclarecimentos (decisões em aberto)

Padrões adotados até confirmação. Todos viram opção em RF-08.

| # | Pergunta | Padrão adotado | Alternativa |
| --- | --- | --- | --- |
| C-1 | Quem é atendido automaticamente? | Só contatos salvos | Qualquer número |
| C-2 | Anunciar quem liga? | Sim | Não |
| C-3 | Saída de áudio ao atender? | Viva-voz | Alto-falante de ouvido |
| C-4 | Atender videochamadas? | Não; apenas anunciar | Sim |
| C-5 | Qual gesto encerra a ligação? | Botão liga/desliga, se a opção nativa funcionar com WhatsApp | Segurar volume para baixo por 2 s |

## 7. Fora de escopo

- Ligações telefônicas comuns
- WhatsApp Business
- iOS
- Leitura de tela em geral
- Chamadas em grupo
- Publicação na Play Store
