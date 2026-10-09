package br.atendepai.spike

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.StatusBarNotification

/**
 * Tenta atender a chamada por varias estrategias, em ordem, ate uma funcionar.
 *
 * O sinal de sucesso e objetivo: a chegada da **notificacao continua** do
 * WhatsApp, que so existe depois da chamada atendida (comprovado em P-3).
 *
 * A estrategia de acessibilidade ganha **retentativas**: a tela da chamada pode
 * demorar a aparecer, e uma tentativa unica falharia sem que se saiba se foi por
 * isso ou porque o clique nao funciona. As outras tres sao disparo unico, porque
 * um PendingIntent ou chega ou nao chega.
 */
object CascataAtendimento {

    /** Espera depois de um disparo unico, antes de declarar que nao atendeu. */
    private const val ESPERA_MS = 2_000L

    /** Intervalo entre retentativas do clique por acessibilidade. */
    private const val INTERVALO_ACESSIBILIDADE_MS = 900L

    private const val TENTATIVAS_ACESSIBILIDADE = 6

    private const val WAKELOCK_MAX_MS = 20_000L

    private val handler = Handler(Looper.getMainLooper())

    private var rodando = false
    private var indice = 0
    private var tentativa = 0
    private var fila: List<Estrategia> = emptyList()
    private var chamada: StatusBarNotification? = null
    private var atendida = false
    private var ctx: Context? = null
    private var origem = ""
    private var bloqueadaAoTocar = false
    private var ultimoDiagnostico = ""
    private var wakelock: PowerManager.WakeLock? = null

    val emExecucao: Boolean get() = rodando

    fun iniciar(
        contexto: Context,
        sbn: StatusBarNotification,
        fila: List<Estrategia>,
        bloqueadaAoTocar: Boolean,
        origem: String,
    ) {
        if (rodando) {
            SpikeLog.d(contexto, "cascata ja em execucao, ignorando novo inicio")
            return
        }
        if (fila.isEmpty()) {
            SpikeLog.d(contexto, "cascata sem estrategias selecionadas")
            return
        }
        this.ctx = contexto.applicationContext
        this.chamada = sbn
        this.fila = fila
        this.indice = 0
        this.tentativa = 0
        this.atendida = false
        this.rodando = true
        this.origem = origem
        this.bloqueadaAoTocar = bloqueadaAoTocar
        this.ultimoDiagnostico = ""

        segurarTela(contexto)

        SpikeLog.d(
            contexto,
            "=== cascata (origem=$origem, bloqueadaAoTocar=$bloqueadaAoTocar): " +
                "${fila.joinToString { it.id }} ===",
        )
        proximaEstrategia()
    }

    /** Chamado pelo listener quando a notificacao continua aparece. */
    fun aoDetectarAtendida() {
        if (!rodando) return
        val contexto = ctx ?: return
        atendida = true
        SpikeLog.d(contexto, "notificacao continua detectada — chamada ATENDIDA")
        handler.removeCallbacksAndMessages(null)
        concluir(sucesso = true)
    }

    fun cancelar(contexto: Context, motivo: String) {
        if (!rodando) return
        SpikeLog.d(contexto, "cascata cancelada ($motivo)")
        handler.removeCallbacksAndMessages(null)
        rodando = false
        chamada = null
        liberarTela()
    }

    private fun proximaEstrategia() {
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
            proximaEstrategia()
            return
        }

        tentativa = 0
        SpikeLog.d(contexto, "--- ${estrategia.id}: ${estrategia.rotulo} ---")
        executarTentativa()
    }

    private fun executarTentativa() {
        val contexto = ctx ?: return
        val estrategia = fila.getOrNull(indice) ?: return
        tentativa++

        ultimoDiagnostico = EstrategiasAtender.disparar(contexto, estrategia, chamada)
        SpikeLog.d(contexto, "${estrategia.id} tentativa $tentativa: $ultimoDiagnostico")

        val espera = if (estrategia == Estrategia.ACESSIBILIDADE) {
            INTERVALO_ACESSIBILIDADE_MS
        } else {
            ESPERA_MS
        }
        handler.postDelayed({ verificar() }, espera)
    }

    private fun verificar() {
        val contexto = ctx ?: return
        val estrategia = fila.getOrNull(indice) ?: return

        if (atendida) {
            concluir(sucesso = true)
            return
        }

        val limite = if (estrategia == Estrategia.ACESSIBILIDADE) TENTATIVAS_ACESSIBILIDADE else 1
        if (tentativa < limite) {
            executarTentativa()
            return
        }

        Veredito.registrar(
            contexto,
            estrategia.id,
            Veredito.Resultado.NAO,
            "$tentativa tentativa(s) sem atender. Ultimo: $ultimoDiagnostico",
        )
        SpikeLog.d(contexto, "${estrategia.id} FALHOU apos $tentativa tentativa(s)")
        indice++
        proximaEstrategia()
    }

    private fun concluir(sucesso: Boolean) {
        val contexto = ctx ?: return
        val estrategia = fila.getOrNull(indice)
        rodando = false
        chamada = null
        liberarTela()

        if (!sucesso || estrategia == null) {
            Veredito.registrar(
                contexto,
                "P-2",
                Veredito.Resultado.NAO,
                "nenhuma das ${fila.size} estrategias atendeu: ${fila.joinToString { it.id }}",
            )
            SpikeLog.d(contexto, ">>> nenhuma estrategia atendeu")
            return
        }

        Veredito.registrar(
            contexto,
            estrategia.id,
            Veredito.Resultado.SIM,
            "atendeu na tentativa $tentativa. $ultimoDiagnostico",
        )
        Veredito.registrar(
            contexto,
            "P-2",
            Veredito.Resultado.SIM,
            "atendeu por ${estrategia.id} — ${estrategia.rotulo}",
        )

        // P-7 e P-8 sao o que realmente interessa: atender sozinho, sem ninguem tocar
        // na tela, com o celular bloqueado. Sem eles o app nao resolve o problema.
        if (origem == "auto") {
            Veredito.registrar(
                contexto,
                "P-7",
                Veredito.Resultado.SIM,
                "o atraso venceu e a cascata disparou sozinha, sem toque na tela " +
                    "(venceu ${estrategia.id})",
            )
        }
        if (bloqueadaAoTocar) {
            Veredito.registrar(
                contexto,
                "P-8",
                Veredito.Resultado.SIM,
                "atendeu com o aparelho BLOQUEADO por ${estrategia.id}",
            )
        }

        SpikeLog.d(contexto, ">>> ATENDEU por ${estrategia.id} (origem=$origem, bloqueada=$bloqueadaAoTocar)")
    }

    /**
     * Mantem a tela acordada durante a tentativa.
     *
     * A chamada do WhatsApp normalmente acende a tela sozinha, mas se ela apagar
     * no meio o clique por acessibilidade pode nao alcancar nada. Wakelock aqui
     * respeita a constituicao IV: e durante uma chamada, nao entre chamadas.
     */
    private fun segurarTela(contexto: Context) {
        liberarTela()
        val power = contexto.getSystemService(PowerManager::class.java) ?: return
        runCatching {
            @Suppress("DEPRECATION")
            val lock = power.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AtendeSpike:cascata",
            )
            lock.acquire(WAKELOCK_MAX_MS)
            wakelock = lock
            SpikeLog.d(contexto, "wakelock adquirido para a tentativa de atendimento")
        }.onFailure {
            SpikeLog.d(contexto, "wakelock indisponivel: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun liberarTela() {
        runCatching { wakelock?.takeIf { it.isHeld }?.release() }
        wakelock = null
    }
}
