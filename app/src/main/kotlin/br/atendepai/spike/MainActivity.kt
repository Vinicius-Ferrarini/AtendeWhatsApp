package br.atendepai.spike

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
 * deixa confirmar o que so o ouvido decide (P-2 e P-6) e exporta tudo.
 *
 * Serve ao administrador durante o experimento; o usuario final nunca a usa.
 * UI programatica de proposito: zero dependencias.
 */
class MainActivity : Activity() {

    private lateinit var permissoes: TextView
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
        botao("Ativar acessibilidade (P-5)") {
            abrir(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        titulo("2. Antes de ligar")
        botao("Ligar auto-atender (${Prefs.ATRASO_PADRAO}s)") {
            Prefs.setAutoAtender(this, true)
            Prefs.setAtrasoSegundos(this, Prefs.ATRASO_PADRAO)
        }
        botao("Desligar auto-atender (só observar)") {
            Prefs.setAutoAtender(this, false)
        }

        titulo("3. Durante a chamada")
        botao("Atender agora (P-2)") { NotificationDumpService.instancia?.atender(origem = "tela") }
        botao("Desligar agora (P-3)") { NotificationDumpService.instancia?.desligar(origem = "tela") }
        botao("Forçar viva-voz (P-4)") { AudioProbe.forcarVivaVoz(applicationContext) }
        botao("Despejar notificações ativas") { NotificationDumpService.instancia?.dumpAtivas() }

        titulo("4. Confirmar o que só você sabe")
        botao("P-2: a chamada FOI atendida") {
            Veredito.registrar(this, "P-2", Veredito.Resultado.SIM, "confirmado de ouvido pelo administrador")
        }
        botao("P-2: NÃO foi atendida") {
            Veredito.registrar(
                this, "P-2", Veredito.Resultado.NAO,
                "disparo sem efeito, confirmado de ouvido → plano B: clique por acessibilidade",
            )
        }
        botao("P-6: liga/desliga ENCERROU a chamada") {
            Veredito.registrar(this, "P-6", Veredito.Resultado.SIM, "opção nativa funcionou com o WhatsApp")
        }
        botao("P-6: NÃO encerrou") {
            Veredito.registrar(
                this, "P-6", Veredito.Resultado.NAO,
                "opção nativa não encerra chamada do WhatsApp → usar gesto de volume (P-5)",
            )
        }

        titulo("5. Resultado")
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

    private fun atualiza() {
        permissoes.text = buildString {
            appendLine("Acesso a notificações: ${marca(listenerAtivo())}")
            appendLine("Listener conectado: ${marca(NotificationDumpService.instancia != null)}")
            appendLine("Acessibilidade: ${marca(acessibilidadeAtiva())}")
            appendLine()
            appendLine("Auto-atender: ${Prefs.autoAtender(this@MainActivity)} (${Prefs.atrasoSegundos(this@MainActivity)}s)")
            append("Áudio: ${AudioProbe.estado(this@MainActivity)}")
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
                        SpikeLog.d(this@MainActivity, "erro em '$texto': ${it.javaClass.simpleName}: ${it.message}")
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
    }
}
