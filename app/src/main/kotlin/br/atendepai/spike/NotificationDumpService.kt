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

    /**
     * P-2: roda a cascata de estrategias de atendimento.
     *
     * O PendingIntent simples sozinho ja se mostrou inutil no HyperOS, por isso
     * aqui nao se dispara mais uma acao so: a [CascataAtendimento] tenta as
     * quatro em ordem e detecta qual funcionou.
     */
    fun atender(origem: String): Boolean {
        val sbn = ultimaChamada
        if (sbn == null) {
            SpikeLog.d(this, "atender ($origem): nenhuma chamada registrada")
            return false
        }
        val fila = Prefs.filaDeEstrategias(this)
        SpikeLog.d(this, "atender (origem=$origem) com ${fila.size} estrategia(s)")
        CascataAtendimento.iniciar(this, sbn, fila)
        return true
    }

    /**
     * P-3: desligar continua no plano A, por PendingIntent — e o unico ponto
     * comprovadamente funcional na medicao de 2026-10-08. Se falhar, cai para
     * o clique por acessibilidade (P-3b).
     */
    fun desligar(origem: String): Boolean {
        if (disparaAcao(Rotulos.DESLIGAR, "P-3 DESLIGAR", origem, sondarAudio = false)) return true

        val servico = SpikeAccessibilityService.instancia
        if (servico == null) {
            SpikeLog.d(this, "P-3b: acessibilidade desativada, sem reserva para desligar")
            return false
        }
        val diagnostico = ClicadorAcessibilidade.clicarPorTexto(servico, Rotulos.DESLIGAR)
        SpikeLog.d(this, "P-3b desligar por acessibilidade: $diagnostico")
        Veredito.registrar(
            this,
            "P-3b",
            if (diagnostico.startsWith("CLICOU")) Veredito.Resultado.PARCIAL else Veredito.Resultado.NAO,
            diagnostico,
        )
        return diagnostico.startsWith("CLICOU")
    }

    /** Lista os textos visiveis nas janelas. Serve para descobrir os rotulos reais. */
    fun inventariarTela(): String {
        val servico = SpikeAccessibilityService.instancia
            ?: return "acessibilidade desativada"
        val inventario = ClicadorAcessibilidade.inventariar(servico)
        SpikeLog.d(this, "inventario da tela:\n$inventario")
        return inventario
    }

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
                    // Ela e tambem o sinal objetivo de que a chamada foi atendida,
                    // que e como a cascata descobre qual estrategia funcionou.
                    val novaChamada = !chamadaEmAndamento
                    SpikeLog.d(this, "P-3: notificacao CONTINUA da chamada em andamento detectada")
                    chamadaEmAndamento = true
                    avaliaP3(sbn)
                    CascataAtendimento.aoDetectarAtendida()
                    if (novaChamada) {
                        // P-4 so faz sentido com a chamada ja atendida.
                        handler.postDelayed({ AudioProbe.forcarVivaVoz(applicationContext) }, 1_500L)
                    }
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
                if (sbn.isOngoing) {
                    chamadaEmAndamento = false
                } else {
                    // A chamada parou de tocar: nao faz sentido seguir tentando atender.
                    CascataAtendimento.cancelar(this, "a chamada parou de tocar")
                }
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

    /**
     * Dispara o PendingIntent de uma acao da notificacao.
     *
     * Hoje serve so ao desligar: atender passou para a [CascataAtendimento],
     * porque o PendingIntent simples nao funciona no HyperOS.
     */
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
                "$etiqueta: PendingIntent '${alvo.title}' disparado sem excecao (origem=$origem)",
            )
            if (sondarAudio) {
                handler.postDelayed({ AudioProbe.forcarVivaVoz(applicationContext) }, 1_500L)
            }
            true
        } catch (e: Exception) {
            SpikeLog.d(this, "$etiqueta ERRO: ${e.javaClass.simpleName}: ${e.message}")
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
