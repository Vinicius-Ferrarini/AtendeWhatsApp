package br.atendepai.spike

import android.app.KeyguardManager
import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.File
import org.json.JSONObject

/**
 * Responde P-1, P-2 e P-3.
 *
 * Registra no Logcat todos os campos das notificacoes de interesse e, sob comando,
 * dispara o PendingIntent de "Atender" / "Desligar".
 */
class NotificationDumpService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    private var ultimaChamada: StatusBarNotification? = null
    private var ultimoJson: JSONObject? = null
    private var tarefaAutoAtender: Runnable? = null

    override fun onListenerConnected() {
        instancia = this
        SpikeLog.d(this, "listener CONECTADO (auto=${Prefs.autoAtender(this)} atraso=${Prefs.atrasoSegundos(this)}s)")
        dumpAtivas()
    }

    override fun onListenerDisconnected() {
        SpikeLog.d(this, "listener DESCONECTADO — HyperOS pode ter matado o servico (risco da secao 8 do plan.md)")
        instancia = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        trata("POSTED", sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, motivo: Int) {
        trata("REMOVED", sbn, motivo)
    }

    fun dumpAtivas() {
        val ativas = runCatching { activeNotifications }.getOrNull()
        if (ativas == null) {
            SpikeLog.d(this, "nao foi possivel ler activeNotifications")
            return
        }
        SpikeLog.d(this, "--- ${ativas.size} notificacoes ativas ---")
        ativas.filter(::interessa).forEach { trata("ATIVA", it) }
    }

    /** P-2 / P-3: dispara o PendingIntent da acao. */
    fun atender(origem: String): Boolean =
        disparaAcao(Rotulos.ATENDER, "P-2 ATENDER", origem, sondarAudio = true)

    fun desligar(origem: String): Boolean =
        disparaAcao(Rotulos.DESLIGAR, "P-3 DESLIGAR", origem, sondarAudio = false)

    fun salvaFixture(): File? {
        val json = ultimoJson
        if (json == null) {
            SpikeLog.d(this, "T008: nenhuma notificacao de chamada capturada ainda")
            return null
        }
        val arquivo = File(filesDir, "whatsapp_chamada.json")
        return runCatching {
            arquivo.writeText(json.toString(2))
            SpikeLog.d(this, "T008: fixture salva em ${arquivo.absolutePath}")
            arquivo
        }.getOrElse {
            SpikeLog.d(this, "T008: falha ao salvar fixture: ${it.message}")
            null
        }
    }

    private fun interessa(sbn: StatusBarNotification): Boolean =
        sbn.packageName in PACOTES_WHATSAPP ||
            sbn.notification.category == Notification.CATEGORY_CALL

    private fun ehChamadaWhatsApp(sbn: StatusBarNotification): Boolean =
        sbn.packageName in PACOTES_WHATSAPP &&
            sbn.notification.category == Notification.CATEGORY_CALL

    private fun trata(evento: String, sbn: StatusBarNotification, motivo: Int? = null) {
        if (!interessa(sbn)) return

        val n = sbn.notification
        SpikeLog.d(
            this,
            "=== $evento pacote=${sbn.packageName} cat=${n.category} ongoing=${sbn.isOngoing} " +
                "fullScreen=${n.fullScreenIntent != null} acoes=${n.actions?.size ?: 0} key=${sbn.key}",
        )
        n.actions?.forEachIndexed { i, a ->
            SpikeLog.d(this, "    acao[$i] '${a.title}' intent=${a.actionIntent != null}")
        }

        val json = NotificationJson.de(this, sbn, evento, motivo)
        SpikeLog.json(this, json)

        if (!ehChamadaWhatsApp(sbn)) return

        when (evento) {
            "POSTED", "ATIVA" -> {
                if (sbn.isOngoing) {
                    // P-3: a notificacao continua e a da chamada em andamento.
                    SpikeLog.d(this, "P-3: notificacao CONTINUA da chamada em andamento detectada")
                    chamadaEmAndamento = true
                    avaliaP3(sbn)
                } else {
                    avaliaP1(sbn)
                }
                val repetida = ultimaChamada?.key == sbn.key && tarefaAutoAtender != null
                ultimaChamada = sbn
                ultimoJson = json
                when {
                    sbn.isOngoing ->
                        SpikeLog.d(this, "notificacao em andamento — nao agenda atendimento")

                    repetida ->
                        SpikeLog.d(this, "notificacao repostada com a mesma key — nao reagenda (vide UT-11)")

                    evento == "POSTED" -> agendaAutoAtender()

                    else -> SpikeLog.d(this, "chamada encontrada no dump inicial — nao agenda")
                }
            }

            "REMOVED" -> {
                SpikeLog.d(this, "chamada removida (motivo=$motivo)")
                if (sbn.isOngoing) chamadaEmAndamento = false
                cancelaAutoAtender("notificacao removida")
            }
        }
    }

    /** P-1: categoria e acoes da chamada tocando, distinguindo bloqueado de desbloqueado. */
    private fun avaliaP1(sbn: StatusBarNotification) {
        val bloqueada = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val pergunta = if (bloqueada) "P-1a" else "P-1b"
        val acoes = sbn.notification.actions
        val atender = acoes?.firstOrNull {
            Rotulos.casa(it.title?.toString(), Rotulos.ATENDER) && it.actionIntent != null
        }
        val titulo = sbn.notification.extras.getCharSequence("android.title")?.toString()
        val rotulos = acoes?.joinToString { "'${it.title}'" } ?: "nenhuma"

        when {
            atender != null -> Veredito.registrar(
                this,
                pergunta,
                Veredito.Resultado.SIM,
                "category=call, ação '${atender.title}' com PendingIntent, título='$titulo', " +
                    "fullScreenIntent=${sbn.notification.fullScreenIntent != null}",
            )

            else -> Veredito.registrar(
                this,
                pergunta,
                Veredito.Resultado.NAO,
                "category=call OK mas sem ação de atender utilizável. Ações: $rotulos. " +
                    "→ plano B: detectar pela janela via acessibilidade",
            )
        }
    }

    /** P-3: a notificacao continua traz acao de desligar? */
    private fun avaliaP3(sbn: StatusBarNotification) {
        val desligar = sbn.notification.actions?.firstOrNull {
            Rotulos.casa(it.title?.toString(), Rotulos.DESLIGAR) && it.actionIntent != null
        }
        if (desligar != null) {
            Veredito.registrar(
                this,
                "P-3",
                Veredito.Resultado.SIM,
                "notificação contínua com ação '${desligar.title}' e PendingIntent",
            )
        } else {
            val rotulos = sbn.notification.actions?.joinToString { "'${it.title}'" } ?: "nenhuma"
            Veredito.registrar(
                this,
                "P-3",
                Veredito.Resultado.NAO,
                "notificação contínua existe mas sem ação de desligar. Ações: $rotulos. " +
                    "→ plano B: clique no botão vermelho via acessibilidade",
            )
        }
    }

    private fun agendaAutoAtender() {
        if (!Prefs.autoAtender(this)) {
            SpikeLog.d(this, "auto-atender DESLIGADO — apenas observando (P-1)")
            return
        }
        cancelaAutoAtender("reagendando")
        val atraso = Prefs.atrasoSegundos(this)
        val tarefa = Runnable {
            tarefaAutoAtender = null
            SpikeLog.d(this, "atraso de ${atraso}s venceu — disparando atender")
            atender(origem = "auto")
        }
        tarefaAutoAtender = tarefa
        handler.postDelayed(tarefa, atraso * 1_000L)
        SpikeLog.d(this, "auto-atender agendado para ${atraso}s")
    }

    private fun cancelaAutoAtender(motivo: String) {
        tarefaAutoAtender?.let {
            handler.removeCallbacks(it)
            SpikeLog.d(this, "auto-atender cancelado ($motivo)")
        }
        tarefaAutoAtender = null
    }

    private fun disparaAcao(
        rotulos: List<String>,
        etiqueta: String,
        origem: String,
        sondarAudio: Boolean,
    ): Boolean {
        val sbn = ultimaChamada
        if (sbn == null) {
            SpikeLog.d(this, "$etiqueta: nenhuma chamada registrada")
            return false
        }
        val acoes = sbn.notification.actions
        if (acoes.isNullOrEmpty()) {
            SpikeLog.d(this, "$etiqueta FALHA: notificacao sem acoes -> plano B por acessibilidade")
            return false
        }
        val alvo = acoes.firstOrNull { Rotulos.casa(it.title?.toString(), rotulos) }
        if (alvo == null) {
            val titulos = acoes.joinToString { "'${it.title}'" }
            SpikeLog.d(this, "$etiqueta FALHA: nenhum rotulo casou com $rotulos. Disponiveis: $titulos")
            return false
        }
        val pendente = alvo.actionIntent
        if (pendente == null) {
            SpikeLog.d(this, "$etiqueta FALHA: acao '${alvo.title}' sem PendingIntent")
            return false
        }
        return try {
            pendente.send()
            SpikeLog.d(
                this,
                "$etiqueta: PendingIntent '${alvo.title}' disparado sem excecao (origem=$origem). " +
                    "CONFIRMAR DE OUVIDO se a chamada realmente mudou de estado.",
            )
            if (sondarAudio) {
                // send() sem excecao nao prova que atendeu: so o ouvido fecha P-2.
                Veredito.registrar(
                    this,
                    "P-2",
                    Veredito.Resultado.PARCIAL,
                    "PendingIntent '${alvo.title}' disparado sem exceção (origem=$origem). " +
                        "Confirme na tela se a chamada foi atendida.",
                )
            }
            if (sondarAudio) {
                // P-4 so faz sentido com a chamada ja atendida.
                handler.postDelayed({ AudioProbe.forcarVivaVoz(applicationContext) }, 1_500L)
            }
            true
        } catch (e: Exception) {
            SpikeLog.d(this, "$etiqueta ERRO: ${e.javaClass.simpleName}: ${e.message} -> plano B por acessibilidade")
            if (sondarAudio) {
                Veredito.registrar(
                    this,
                    "P-2",
                    Veredito.Resultado.NAO,
                    "${e.javaClass.simpleName}: ${e.message} → plano B: clique por acessibilidade",
                )
            }
            false
        }
    }

    companion object {
        @Volatile
        var instancia: NotificationDumpService? = null

        /** Lido pelo servico de acessibilidade para contextualizar P-5. */
        @Volatile
        var chamadaEmAndamento: Boolean = false

        val PACOTES_WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
