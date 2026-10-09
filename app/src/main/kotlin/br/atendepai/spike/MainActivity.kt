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
 * Unica interface do spike quando nao ha USB: mostra o veredito de cada pergunta,
 * escolhe a estrategia de atendimento e exporta tudo.
 *
 * Serve ao administrador durante o experimento; o usuario final nunca a usa.
 * UI programatica de proposito: zero dependencias.
 */
class MainActivity : Activity() {

    private lateinit var permissoes: TextView
    private lateinit var estrategiaAtual: TextView
    private lateinit var respostas: TextView
    private lateinit var raiz: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }

        titulo("1. Permissões")
        permissoes = corpo()
        botao("Conceder acesso a notificações") {
            abrir(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        botao("Ativar acessibilidade") {
            abrir(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        botao("Permitir atender chamadas (P-2.3)") {
            requestPermissions(arrayOf(Manifest.permission.ANSWER_PHONE_CALLS), PEDIDO_ATENDER)
        }

        titulo("2. Estratégia de atendimento")
        estrategiaAtual = corpo()
        botao("Cascata: tenta as 4 em ordem (recomendado)") {
            Prefs.setEstrategiaFixa(this, null)
        }
        Estrategia.entries.forEach { estrategia ->
            botao("Só ${estrategia.id}: ${estrategia.rotulo.substringBefore(" (")}") {
                Prefs.setEstrategiaFixa(this, estrategia)
            }
        }

        titulo("3. Antes de ligar")
        botao("Ligar auto-atender (${Prefs.ATRASO_PADRAO}s)") {
            Prefs.setAutoAtender(this, true)
            Prefs.setAtrasoSegundos(this, Prefs.ATRASO_PADRAO)
        }
        botao("Desligar auto-atender (só observar)") {
            Prefs.setAutoAtender(this, false)
        }

        titulo("4. Durante a chamada")
        botao("ATENDER agora") { NotificationDumpService.instancia?.atender(origem = "tela") }
        botao("DESLIGAR agora") { NotificationDumpService.instancia?.desligar(origem = "tela") }
        botao("Forçar viva-voz") { AudioProbe.forcarVivaVoz(applicationContext) }
        botao("Inventariar textos da tela") {
            respostas.text = NotificationDumpService.instancia?.inventariarTela() ?: "listener desativado"
        }
        botao("Despejar notificações ativas") { NotificationDumpService.instancia?.dumpAtivas() }

        titulo("5. Confirmar o que só você sabe")
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
        botao("Salvar fixture (T008)") { NotificationDumpService.instancia?.salvaFixture() }
        botao("EXPORTAR E ENVIAR") {
            val uris = LogExporter.exportar(this)
            if (uris.isEmpty()) {
                SpikeLog.d(this, "nada exportado ainda")
            } else {
                LogExporter.compartilhar(this, uris)
            }
        }
        botao("Apagar vereditos e começar de novo") { Veredito.limpar(this) }

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
        permissoes.text = buildString {
            appendLine("Acesso a notificações: ${marca(listenerAtivo())}")
            appendLine("Listener conectado: ${marca(NotificationDumpService.instancia != null)}")
            appendLine("Acessibilidade declarada: ${marca(acessibilidadeAtiva())}")
            appendLine("Acessibilidade em execução: ${marca(SpikeAccessibilityService.instancia != null)}")
            appendLine(
                "ANSWER_PHONE_CALLS: " +
                    marca(EstrategiasAtender.temPermissaoAtenderChamadas(this@MainActivity)),
            )
            appendLine()
            appendLine(
                "Auto-atender: ${Prefs.autoAtender(this@MainActivity)} " +
                    "(${Prefs.atrasoSegundos(this@MainActivity)}s)",
            )
            append("Áudio: ${AudioProbe.estado(this@MainActivity)}")
        }

        val fixa = Prefs.estrategiaFixa(this)
        estrategiaAtual.text = buildString {
            appendLine(if (fixa == null) "CASCATA: tenta as 4 em ordem" else "FIXA em ${fixa.id}")
            Estrategia.entries.forEach { estrategia ->
                val impedimento = EstrategiasAtender.impedimento(this@MainActivity, estrategia)
                appendLine("  ${estrategia.id} ${impedimento ?: "pronta"}")
            }
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

    private fun acessibilidadeAtiva(): Boolean = ativoEm(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun ativoEm(chave: String): Boolean =
        Settings.Secure.getString(contentResolver, chave)?.contains(packageName) == true

    private companion object {
        const val PADDING = 40
        const val PEDIDO_ATENDER = 1
    }
}
