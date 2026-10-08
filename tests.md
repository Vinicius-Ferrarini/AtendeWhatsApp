# Testes — Atende Pai

> Estratégia de TDD e catálogo de testes. Cada teste aponta para um critério de aceite (`AC-XX.Y`) da `spec.md`.
> Regra da constituição (II): o teste é escrito e falha **antes** da implementação.

**Atualizado em:** 2026-10-07

---

## 1. Ciclo

1. Escolher o próximo critério de aceite em `spec.md`.
2. Escrever o teste com o ID no nome e ver falhar (vermelho).
3. Escrever o mínimo de código para passar (verde).
4. Refatorar com todos os testes verdes.
5. Marcar o teste neste arquivo e a tarefa em `tasks.md`.

## 2. Pirâmide

| Nível | Onde roda | O que cobre | Ferramentas | Meta |
| --- | --- | --- | --- | --- |
| Unitário | JVM, `:core` | Política, máquina de estados, atraso, modelo | JUnit 5, kotlinx-coroutines-test, Turbine | Cobertura ≥ 90% do `:core` |
| Integração | JVM + Robolectric, `:app` | Parser com `Notification` real, DataStore, adaptadores | Robolectric, JUnit 4 | Parser e adaptadores cobertos |
| UI | Emulador / aparelho | Tela de configuração | Compose UI Test | Fluxos de RF-08 |
| Aceitação manual | Redmi Note 13 | Fluxo real com WhatsApp | Roteiro da seção 6 | 100% do roteiro antes de cada entrega |

## 3. Regras

- **Fakes antes de mocks.** `FakeAcoesChamada`, `FakeAnunciador`, `FakeAgenda`, `FakeRoteadorAudio`, `FakeRepositorioConfig` em `core/src/test`. MockK só para classes do Android sem alternativa.
- **Tempo virtual.** Atrasos com `runTest` + `advanceTimeBy`; nenhum teste dorme.
- **Teste de contrato.** A notificação real do WhatsApp capturada no spike fica em `app/src/test/resources/fixtures/whatsapp_chamada.json`. Se o WhatsApp mudar, este teste é o primeiro a quebrar.
- **Nome = rastreabilidade.** Padrão: `` `AC02_1 atende uma unica vez apos o atraso` ``.
- **Um comportamento por teste.**

## 4. Exemplos

```kotlin
class MaquinaChamadaTest {

    private val acoes = FakeAcoesChamada()
    private val anunciador = FakeAnunciador()

    @Test
    fun `AC02_1 atende uma unica vez apos o atraso`() = runTest {
        val maquina = maquina(atraso = 10)

        maquina.aoReceber(chamada(chave = "c1", nome = "Maria"))
        advanceTimeBy(9_999)
        assertEquals(emptyList<String>(), acoes.atendidas)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("c1"), acoes.atendidas)
    }

    @Test
    fun `AC02_2 nao atende se a chamada parar de tocar antes do prazo`() = runTest {
        val maquina = maquina(atraso = 10)

        maquina.aoReceber(chamada(chave = "c1"))
        advanceTimeBy(5_000)
        maquina.aoRemover(ChaveChamada("c1"))
        advanceTimeBy(10_000)

        assertEquals(emptyList<String>(), acoes.atendidas)
    }

    private fun TestScope.maquina(atraso: Int) = MaquinaChamada(
        acoes = acoes,
        anunciador = anunciador,
        config = Config(atraso = AtrasoSegundos(atraso)),
        scope = backgroundScope,
    )
}
```

```kotlin
class AtrasoSegundosTest {
    @Test
    fun `AC02_3 rejeita atraso fora de 0 a 60`() {
        assertThrows<IllegalArgumentException> { AtrasoSegundos(-1) }
        assertThrows<IllegalArgumentException> { AtrasoSegundos(61) }
    }
}
```

## 5. Catálogo de testes automatizados

### `:core` — unitários

| ID | AC | Classe | Descrição | Feito |
| --- | --- | --- | --- | --- |
| UT-01 | AC-02.3 | `AtrasoSegundosTest` | Aceita 0 e 60; rejeita -1 e 61 | [ ] |
| UT-02 | AC-02.3 | `ConfigTest` | Padrão: atraso 10, só contatos, anúncio ligado, viva-voz ligado | [ ] |
| UT-03 | AC-04.1 | `PoliticaAtendimentoTest` | Desconhecido no modo "só contatos" → não elegível | [ ] |
| UT-04 | AC-04.2 | `PoliticaAtendimentoTest` | Contato salvo no modo "só contatos" → elegível | [ ] |
| UT-05 | AC-04.3 | `PoliticaAtendimentoTest` | Modo "qualquer número" → elegível | [ ] |
| UT-06 | C-4 | `PoliticaAtendimentoTest` | Videochamada → não elegível | [ ] |
| UT-07 | AC-08.1 | `PoliticaAtendimentoTest` | App desligado → não elegível | [ ] |
| UT-08 | AC-02.1 | `MaquinaChamadaTest` | Atende uma única vez após o atraso | [ ] |
| UT-09 | AC-02.2 | `MaquinaChamadaTest` | Remoção antes do prazo cancela | [ ] |
| UT-10 | AC-02.1 | `MaquinaChamadaTest` | Atraso 0 atende imediatamente | [ ] |
| UT-11 | AC-02.1 | `MaquinaChamadaTest` | Notificação repostada com a mesma chave não agenda duas vezes | [ ] |
| UT-12 | AC-05.1 | `MaquinaChamadaTest` | Anuncia "Ligação de Maria. Atendendo em 10 segundos." | [ ] |
| UT-13 | AC-04.1 | `MaquinaChamadaTest` | Não elegível anuncia "Número desconhecido" e não atende | [ ] |
| UT-14 | AC-05.2 | `MaquinaChamadaTest` | Ao atender, anuncia "Atendida" | [ ] |
| UT-15 | AC-05.4 | `MaquinaChamadaTest` | Anúncio desligado → nada é falado | [ ] |
| UT-16 | AC-06.1 | `MaquinaChamadaTest` | Viva-voz ligado → `ligarVivaVoz` chamado após atender | [ ] |
| UT-17 | — | `MaquinaChamadaTest` | Falha no plano A → tenta plano B e registra falha | [ ] |
| UT-18 | AC-07.1 | `MaquinaChamadaTest` | Gesto com chamada ativa → desliga e anuncia "Ligação encerrada" | [ ] |
| UT-19 | AC-07.2 | `MaquinaChamadaTest` | Gesto sem chamada ativa → nenhuma ação | [ ] |
| UT-20 | — | `MaquinaChamadaTest` | Notificação contínua removida → volta a Ocioso | [ ] |

### `:app` — integração (Robolectric)

| ID | AC | Classe | Descrição | Feito |
| --- | --- | --- | --- | --- |
| IT-01 | AC-01.1 | `ParserNotificacaoWhatsAppTest` | Fixture real → `ChamadaRecebida` com nome e tipo | [ ] |
| IT-02 | AC-01.2 | `ParserNotificacaoWhatsAppTest` | Notificação do discador nativo → `null` | [ ] |
| IT-03 | AC-01.3 | `ParserNotificacaoWhatsAppTest` | Notificação de mensagem do WhatsApp → `null` | [ ] |
| IT-04 | RNF-06 | `ParserNotificacaoWhatsAppTest` | Rótulo alterado na config é reconhecido | [ ] |
| IT-05 | AC-04.1 | `AgendaContatosTest` | Título com formato de telefone → desconhecido | [ ] |
| IT-06 | AC-08.2 | `ConfigDataStoreTest` | Grava e relê todas as opções | [ ] |
| IT-07 | AC-07.3 | `AtendeAccessibilityServiceTest` | Gesto ignorado sem chamada do WhatsApp ativa | [ ] |

### `:app` — UI

| ID | AC | Descrição | Feito |
| --- | --- | --- | --- |
| UI-01 | AC-08.1 | Alterar atraso e opções reflete na configuração | [ ] |
| UI-02 | AC-08.3 | Permissão ausente aparece como pendente com atalho | [ ] |
| UI-03 | AC-08.4 | Log mostra os eventos mais recentes primeiro | [ ] |

## 6. Roteiro de aceitação manual (Redmi Note 13)

Executar antes de cada entrega. Usar um segundo celular para ligar.

| ID | AC / RNF | Cenário | Resultado esperado | OK |
| --- | --- | --- | --- | --- |
| MT-01 | AC-03.1 | Ligar com o aparelho bloqueado e tela apagada | Anuncia, atende em ~10 s, viva-voz ligado | [ ] |
| MT-02 | AC-03.2 | Ligar com o aparelho desbloqueado em outro app | Igual ao MT-01 | [ ] |
| MT-03 | AC-02.2 | Ligar e desligar no 5º segundo | Não atende | [ ] |
| MT-04 | AC-04.1 | Ligar de número não salvo | Anuncia "Número desconhecido", não atende | [ ] |
| MT-05 | AC-01.2 | Fazer ligação telefônica comum | App não interfere | [ ] |
| MT-06 | AC-07.1 | Durante chamada ativa, fazer o gesto | Encerra e anuncia | [ ] |
| MT-07 | — | Durante chamada, encostar o celular no ouvido por 30 s | Chamada continua | [ ] |
| MT-08 | RNF-03 | Reiniciar o aparelho e repetir MT-01 sem abrir o app | Funciona | [ ] |
| MT-09 | RNF-01 | Deixar 24 h em uso normal | App fora dos consumidores relevantes de bateria | [ ] |
| MT-10 | C-4 | Fazer videochamada | Anuncia, não atende | [ ] |
