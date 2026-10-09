package br.atendepai.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Controla o experimento por adb, sem precisar tocar na tela:
 *
 * ```
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd autoanswer --ez on true --ei delay 10
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd answer
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd hangup
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd speaker
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd audiostate
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd dump
 * adb shell am broadcast -a br.atendepai.spike.CMD --es cmd savefixture
 * ```
 */
class CmdReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val cmd = intent.getStringExtra("cmd")?.lowercase()
        if (cmd == null) {
            SpikeLog.d(app, "cmd ausente. Use --es cmd <comando>")
            return
        }
        SpikeLog.d(app, "--- cmd recebido: $cmd ---")

        when (cmd) {
            "speaker" -> AudioProbe.forcarVivaVoz(app)

            "audiostate" -> SpikeLog.d(app, "estado de audio: ${AudioProbe.estado(app)}")

            "autoanswer" -> {
                Prefs.setAutoAtender(app, intent.getBooleanExtra("on", true))
                if (intent.hasExtra("delay")) {
                    Prefs.setAtrasoSegundos(app, intent.getIntExtra("delay", Prefs.ATRASO_PADRAO))
                }
                SpikeLog.d(
                    app,
                    "auto-atender=${Prefs.autoAtender(app)} atraso=${Prefs.atrasoSegundos(app)}s",
                )
            }

            in COMANDOS_DO_LISTENER -> comListener(app, cmd)

            else -> SpikeLog.d(app, "comando desconhecido: $cmd")
        }
    }

    private fun comListener(app: Context, cmd: String) {
        val listener = NotificationDumpService.instancia
        if (listener == null) {
            SpikeLog.d(app, "listener de notificacoes NAO conectado — conceda o acesso e tente de novo")
            return
        }
        when (cmd) {
            "dump" -> listener.dumpAtivas()
            "answer" -> listener.atender(origem = "adb")
            "hangup" -> listener.desligar(origem = "adb")
            "savefixture" -> listener.salvaFixture()
            "inventory" -> listener.inventariarTela()
        }
    }

    private companion object {
        val COMANDOS_DO_LISTENER = setOf("dump", "answer", "hangup", "savefixture", "inventory")
    }
}
