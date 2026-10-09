package br.atendepai.spike

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Responde P-5: o gesto de volume chega ao AccessibilityService com a tela apagada
 * durante a chamada?
 *
 * Tambem registra as janelas em primeiro plano, insumo para o plano B de clique
 * (plan.md, AtendeAccessibilityService).
 *
 * Nunca consome o evento (`return false`): o spike so observa.
 */
class SpikeAccessibilityService : AccessibilityService() {

    private var pressionadoEm = 0L

    override fun onServiceConnected() {
        instancia = this
        SpikeLog.d(this, "acessibilidade CONECTADA (filtro de teclas e leitura de janelas ativos)")
    }

    override fun onInterrupt() {
        SpikeLog.d(this, "acessibilidade INTERROMPIDA")
    }

    override fun onDestroy() {
        instancia = null
        SpikeLog.d(this, "acessibilidade DESTRUIDA")
        super.onDestroy()
    }

    override fun onKeyEvent(evento: KeyEvent): Boolean {
        if (evento.keyCode !in TECLAS_DE_INTERESSE) return false

        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val acao = if (evento.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"

        if (evento.action == KeyEvent.ACTION_DOWN && evento.repeatCount == 0) {
            pressionadoEm = evento.eventTime
        }
        val duracao = if (evento.action == KeyEvent.ACTION_UP && pressionadoEm > 0L) {
            evento.eventTime - pressionadoEm
        } else {
            -1L
        }

        SpikeLog.d(
            this,
            "P-5 tecla ${nomeTecla(evento.keyCode)} $acao repeat=${evento.repeatCount} " +
                "duracao=${duracao}ms telaLigada=${power?.isInteractive} telaBloqueada=${keyguard?.isKeyguardLocked}",
        )
        // P-5 so interessa com a tela apagada: e esse o caso difícil.
        if (evento.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            evento.action == KeyEvent.ACTION_UP &&
            power?.isInteractive == false
        ) {
            Veredito.registrar(
                this,
                "P-5",
                Veredito.Resultado.SIM,
                "VOLUME_DOWN recebido com tela apagada (duração ${duracao}ms, " +
                    "chamada em andamento=${NotificationDumpService.chamadaEmAndamento})",
            )
        }

        // Não consome: o volume continua funcionando normalmente.
        return false
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent) {
        if (evento.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pacote = evento.packageName?.toString() ?: return
        if (pacote !in NotificationDumpService.PACOTES_WHATSAPP) return
        SpikeLog.d(this, "janela do WhatsApp em primeiro plano: classe=${evento.className}")
    }

    private fun nomeTecla(codigo: Int): String = when (codigo) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> "VOLUME_DOWN"
        KeyEvent.KEYCODE_VOLUME_UP -> "VOLUME_UP"
        KeyEvent.KEYCODE_POWER -> "POWER"
        KeyEvent.KEYCODE_HEADSETHOOK -> "HEADSETHOOK"
        else -> "TECLA_$codigo"
    }

    companion object {
        /** Necessaria para o ClicadorAcessibilidade alcancar as janelas da chamada. */
        @Volatile
        var instancia: SpikeAccessibilityService? = null

        private val TECLAS_DE_INTERESSE = setOf(
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )
    }
}
