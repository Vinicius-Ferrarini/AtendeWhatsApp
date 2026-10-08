# Constituição — Atende Pai

> Princípios inegociáveis do projeto. Toda spec, plano, teste e tarefa deve respeitá-los.
> Mudar este arquivo exige registrar a versão e o motivo em "Histórico".

**Versão:** 1.0.0 · **Ratificada em:** 2026-10-07

---

## I. Spec antes de código

Nenhuma linha de código de produção é escrita sem um requisito em `spec.md` que a justifique.
Cada requisito tem um ID (`RF-XX`, `RNF-XX`) e critérios de aceite no formato Dado / Quando / Então.

## II. Teste antes de implementação (TDD)

O ciclo é obrigatório: **vermelho → verde → refatorar**.
- Todo critério de aceite vira pelo menos um teste com o ID da spec no nome.
- Nenhuma tarefa de implementação começa antes da tarefa de teste correspondente estar vermelha.
- O tempo nunca é real nos testes: atrasos são testados com relógio virtual.

## III. Domínio puro

Toda regra de negócio vive no módulo `:core`, em Kotlin puro, sem nenhum `import android.*`.
O módulo `:app` apenas traduz eventos do Android para o domínio e executa as ações que o domínio decide.

## IV. Bateria zero entre chamadas

O app é orientado a eventos. É proibido:
- polling (verificações periódicas);
- serviço em primeiro plano permanente;
- wakelock fora de uma chamada em andamento.

## V. Não interferir no que não é do WhatsApp

Ligações telefônicas comuns e notificações de outros apps nunca são afetadas.
Na dúvida sobre a origem de um evento, o app não age.

## VI. Privacidade

Nenhum dado sai do aparelho. O app não declara a permissão `INTERNET`.

## VII. Acessível para quem não enxerga

O usuário final é cego e não usa leitor de tela.
- Nenhum fluxo do usuário final exige olhar ou tocar na tela.
- Toda ação automática relevante tem retorno por voz (anúncio, "Atendida", "Ligação encerrada").
- A tela de configuração é para o administrador, não para o usuário final.

## VIII. Simplicidade

O menor projeto que resolve o problema.
- Sem frameworks de injeção de dependência (DI manual).
- Sem bibliotecas que não tenham uso claro no `plan.md`.
- Toda abstração precisa de pelo menos dois usos ou de um teste que dependa dela.

## IX. Resiliência a terceiros

WhatsApp e HyperOS mudam sem aviso. Por isso:
- textos da interface do WhatsApp (rótulos de botões) ficam em configuração, nunca fixos no código;
- todo ponto de contato com o WhatsApp tem plano B documentado no `plan.md`;
- existe um teste de contrato com uma notificação real capturada.

## X. Qualidade e rastreabilidade

- `./gradlew check` (testes, ktlint, detekt) deve passar antes de qualquer commit na `main`.
- Commits seguem Conventional Commits e citam o ID da spec: `feat(RF-02): cancela atendimento ao remover notificação`.

---

## Histórico

| Versão | Data | Mudança |
| --- | --- | --- |
| 1.0.0 | 2026-10-07 | Versão inicial |
