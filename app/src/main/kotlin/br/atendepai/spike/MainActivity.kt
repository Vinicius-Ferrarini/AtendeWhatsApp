package br.atendepai.spike

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Unica interface do spike quando nao ha USB.
 *
 * Na v0.2 os botoes de selecao de estrategia pareciam acoes: quem apertava os
 * quatro acabava com a cascata fixada na ultima, e tres estrategias nunca eram
 * testadas. Por isso aqui **configuracao e acao ficam visualmente separadas** —
 * acoes em MAIUSCULAS, configuracao com o prefixo "definir", e um painel no topo
 * que mostra o estado efetivo antes de qualquer ligacao.
 */
class MainActivity : Activity() {

    private lateinit var painel: TextView
    private lateinit var respostas: TextView
    private lateinit var raiz: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.migrarSeNecessario(this)

        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }

        titulo("ESTADO — confira antes de ligar")
        painel = corpo()

        titulo("1. Permissões (uma vez)")
        botao("Conceder acesso a notificações") {
            abrir(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        botao("Ativar acessibilidade") {
            abrir(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        botao("Permitir atender chamadas") {
            requestPermissions(arrayOf(Manifest.permission.ANSWER_PHONE_CALLS), PEDIDO_ATENDER)
        }

        titulo("2. Configurar o auto-atender")
        nota("O auto-atender já vem LIGADO. É ele que atende sozinho, sem ninguém tocar na tela — é o que precisa funcionar.")
        botao("definir: auto-atender LIGADO, 10 s") {
            Prefs.setAutoAtender(this, true)
            Prefs.setAtrasoSegundos(this, 10)
        }
        botao("definir: auto-atender LIGADO, 5 s") {
            Prefs.setAutoAtender(this, true)
            Prefs.setAtrasoSegundos(this, 5)
        }
        botao("definir: auto-atender DESLIGADO (só observar)") {
            Prefs.setAutoAtender(this, false)
        }

        titulo("3. Configurar a estratégia")
        nota("Estes botões NÃO atendem nada: só escolhem o que o auto-atender vai tentar. Deixe em cascata para testar as quatro de uma vez.")
        botao("definir: CASCATA, tenta as 4 em ordem") {
            Prefs.setEstrategiaFixa(this, null)
        }
        Estrategia.entries.forEach { estrategia ->
            botao("definir: só ${estrategia.id} — ${estrategia.rotulo.substringBefore(" (")}") {
                Prefs.setEstrategiaFixa(this, estrategia)
            }
        }

        titulo("4. Ações manuais (durante a chamada)")
        nota("Só para depurar. O teste que vale é o do auto-atender, com o celular bloqueado.")
        botao("ATENDER AGORA") { NotificationDumpService.instancia?.atender(origem = "tela") }
        botao("DESLIGAR AGORA") { NotificationDumpService.instancia?.desligar(origem = "tela") }
        botao("FORÇAR VIVA-VOZ (só com chamada atendida)") {
            AudioProbe.forcarVivaVoz(applicationContext)
        }
        botao("INVENTARIAR TEXTOS DA TELA") {
            respostas.text = NotificationDumpService.instancia?.inventariarTela() ?: "listener desativado"
        }
        botao("DESPEJAR NOTIFICAÇÕES ATIVAS") { NotificationDumpService.instancia?.dumpAtivas() }

        titulo("5. Registrar o que só você sabe")
        botao("P-6: liga/desliga ENCERROU a chamada") {
            Veredito.registrar(this, "P-6", Veredito.Resultado.SIM, "opção nativa funcionou com o WhatsApp")
        }
        botao("P-6: NÃO encerrou") {
            Veredito.registrar(
                this, "P-6", Veredito.Resultado.NAO,
                "opção nativa não encerra chamada do WhatsApp → usar gesto de volume (P-5)",
            )
        }

        titulo("6. Resultado")
        respostas = corpo()
        botao("SALVAR FIXTURE") { NotificationDumpService.instancia?.salvaFixture() }
        botao("EXPORTAR E ENVIAR") {
            val uris = LogExporter.exportar(this)
            if (uris.isEmpty()) SpikeLog.d(this, "nada exportado ainda") else LogExporter.compartilhar(this, uris)
        }
        botao("APAGAR VEREDITOS (antes de um teste novo)") { Veredito.limpar(this) }

        setContentView(ScrollView(this).apply { addView(raiz) })
    }

    override fun onResume() {
        super.onResume()
        atualiza()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        SpikeLog.d(this, "ANSWER_PHONE_CALLS concedida=${EstrategiasAtender.temPermissaoAtenderChamadas(this)}")
        atualiza()
    }

    private fun atualiza() {
        val fixa = Prefs.estrategiaFixa(this)
        val pronto = listenerAtivo() &&
            NotificationDumpService.instancia != null &&
            SpikeAccessibilityService.instancia != null &&
            Prefs.autoAtender(this)

        painel.text = buildString {
            appendLine(if (pronto) ">>> PRONTO PARA O TESTE <<<" else ">>> AINDA NAO ESTA PRONTO <<<")
            appendLine()
            appendLine("auto-atender ... ${if (Prefs.autoAtender(this@MainActivity)) "LIGADO" else "DESLIGADO"}, ${Prefs.atrasoSegundos(this@MainActivity)} s")
            appendLine("estrategia ..... ${fixa?.id ?: "CASCATA (4 em ordem)"}")
            appendLine()
            appendLine("notificacoes ... ${marca(listenerAtivo())}")
            appendLine("listener ativo . ${marca(NotificationDumpService.instancia != null)}")
            appendLine("acessibilidade . ${marca(SpikeAccessibilityService.instancia != null)}")
            appendLine("atender chamada  ${marca(EstrategiasAtender.temPermissaoAtenderChamadas(this@MainActivity))}")
            appendLine()
            appendLine("em chamada ..... ${NotificationDumpService.chamadaEmAndamento}")
            append("audio .......... ${AudioProbe.estado(this@MainActivity)}")
        }

        respostas.text = Veredito.relatorio(this)
    }

    private fun marca(ok: Boolean) = if (ok) "OK" else "PENDENTE"

    private fun titulo(texto: String) {
        raiz.addView(
            TextView(this).apply {
                text = texto
                textSize = 17f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.parseColor("#1A237E"))
                setPadding(0, PADDING, 0, PADDING / 3)
            },
        )
    }

    private fun nota(texto: String) {
        raiz.addView(
            TextView(this).apply {
                text = texto
                textSize = 12f
                setTextColor(Color.parseColor("#5D4037"))
                setPadding(0, 0, 0, PADDING / 3)
            },
        )
    }

    private fun corpo(): TextView = TextView(this).apply {
        textSize = 13f
        typeface = Typeface.MONOSPACE
        setPadding(0, 0, 0, PADDING / 3)
        raiz.addView(this)
    }

    private fun botao(texto: String, acao: () -> Unit) {
        raiz.addView(
            Button(this).apply {
                text = texto
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener {
                    runCatching(acao).onFailure {
                        SpikeLog.d(
                            this@MainActivity,
                            "erro em '$texto': ${it.javaClass.simpleName}: ${it.message}",
                        )
                    }
                    atualiza()
                }
            },
        )
    }

    private fun abrir(acao: String) {
        runCatching { startActivity(Intent(acao)) }
            .onFailure { SpikeLog.d(this, "não foi possível abrir $acao: ${it.message}") }
    }

    private fun listenerAtivo(): Boolean = ativoEm("enabled_notification_listeners")

    private fun ativoEm(chave: String): Boolean =
        Settings.Secure.getString(contentResolver, chave)?.contains(packageName) == true

    private companion object {
        const val PADDING = 40
        const val PEDIDO_ATENDER = 1
    }
}
