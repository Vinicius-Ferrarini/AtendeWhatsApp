package br.atendepai.spike

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification

/**
 * Tenta atender a chamada por várias estratégias, em ordem, até uma funcionar.
 *
 * O sinal de sucesso é objetivo: a chegada da **notificação contínua** do
 * WhatsApp, que só existe depois da chamada atendida (comprovado em P-3). Isso
 * tira a resposta de P-2 do campo do "confirme de ouvido" e a torna automática.
 *
 * Entre uma tentativa e a próxima espera [ESPERA_MS], tempo suficiente para o
 * WhatsApp reagir sem estourar a duração de uma chamada tocando.
 */
object CascataAtendimento {

    private const val ESPERA_MS = 2_500L

    private val handler = Handler(Looper.getMainLooper())

    private var rodando = false
    private var indice = 0
    private var fila: List<Estrategia> = emptyList()
    private var chamada: StatusBarNotification? = null
    private var atendida = false
    private var ctx: Context? = null

    val emExecucao: Boolean get() = rodando

    fun iniciar(contexto: Context, sbn: StatusBarNotification, fila: List<Estrategia>) {
        if (rodando) {
            SpikeLog.d(contexto, "cascata já em execução, ignorando novo início")
            return
        }
        if (fila.isEmpty()) {
            SpikeLog.d(contexto, "cascata sem estratégias selecionadas")
            return
        }
        this.ctx = contexto.applicationContext
        this.chamada = sbn
        this.fila = fila
        this.indice = 0
        this.atendida = false
        this.rodando = true

        SpikeLog.d(contexto, "=== cascata de atendimento: ${fila.joinToString { it.id }} ===")
        proximaTentativa()
    }

    /** Chamado pelo listener quando a notificação contínua aparece. */
    fun aoDetectarAtendida() {
        val contexto = ctx ?: return
        if (!rodando) return
        atendida = true
        SpikeLog.d(contexto, "notificação contínua detectada — chamada ATENDIDA")
        handler.removeCallbacksAndMessages(null)
        concluir(sucesso = true)
    }

    fun cancelar(contexto: Context, motivo: String) {
        if (!rodando) return
        SpikeLog.d(contexto, "cascata cancelada ($motivo)")
        handler.removeCallbacksAndMessages(null)
        rodando = false
        chamada = null
    }

    private fun proximaTentativa() {
        val contexto = ctx ?: return

        if (indice >= fila.size) {
            concluir(sucesso = false)
            return
        }

        val estrategia = fila[indice]
        val impedimento = EstrategiasAtender.impedimento(contexto, estrategia)
        if (impedimento != null) {
            SpikeLog.d(contexto, "${estrategia.id} pulada: $impedimento")
            Veredito.registrar(
                contexto,
                estrategia.id,
                Veredito.Resultado.NAO_TESTADO,
                "pulada: $impedimento",
            )
            indice++
            proximaTentativa()
            return
        }

        SpikeLog.d(contexto, "--- tentando ${estrategia.id}: ${estrategia.rotulo} ---")
        val diagnostico = EstrategiasAtender.disparar(contexto, estrategia, chamada)
        SpikeLog.d(contexto, "${estrategia.id} disparo: $diagnostico")

        handler.postDelayed({ verificar(diagnostico) }, ESPERA_MS)
    }

    private fun verificar(diagnosticoDoDisparo: String) {
        val contexto = ctx ?: return
        val estrategia = fila.getOrNull(indice) ?: return

        if (atendida) {
            concluir(sucesso = true)
            return
        }

        Veredito.registrar(
            contexto,
            estrategia.id,
            Veredito.Resultado.NAO,
            "disparou mas a chamada não foi atendida. $diagnosticoDoDisparo",
        )
        SpikeLog.d(contexto, "${estrategia.id} FALHOU: sem notificação contínua após ${ESPERA_MS}ms")
        indice++
        proximaTentativa()
    }

    private fun concluir(sucesso: Boolean) {
        val contexto = ctx ?: return
        val estrategia = fila.getOrNull(indice)
        rodando = false
        chamada = null

        if (sucesso && estrategia != null) {
            Veredito.registrar(
                contexto,
                estrategia.id,
                Veredito.Resultado.SIM,
                "a notificação contínua apareceu em até ${ESPERA_MS}ms depois do disparo",
            )
            Veredito.registrar(
                contexto,
                "P-2",
                Veredito.Resultado.SIM,
                "atendeu por ${estrategia.id} — ${estrategia.rotulo}",
            )
            SpikeLog.d(contexto, ">>> ATENDEU por ${estrategia.id}: ${estrategia.rotulo}")
        } else {
            Veredito.registrar(
                contexto,
                "P-2",
                Veredito.Resultado.NAO,
                "nenhuma das ${fila.size} estratégias atendeu: ${fila.joinToString { it.id }}",
            )
            SpikeLog.d(contexto, ">>> nenhuma estratégia atendeu a chamada")
        }
    }
}
