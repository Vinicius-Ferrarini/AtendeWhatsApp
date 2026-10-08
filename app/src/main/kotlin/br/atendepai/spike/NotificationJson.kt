package br.atendepai.spike

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializa uma [StatusBarNotification] real em JSON.
 *
 * A saida alimenta a fixture `whatsapp_chamada.json` (T008), que depois sustenta
 * o teste de contrato IT-01. Por isso guarda tudo que o parser de producao vai
 * precisar: categoria, flags, extras e os rotulos das acoes.
 */
object NotificationJson {

    private val instante = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    fun de(
        ctx: Context,
        sbn: StatusBarNotification,
        evento: String,
        motivoRemocao: Int? = null,
    ): JSONObject {
        val n = sbn.notification
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        val power = ctx.getSystemService(PowerManager::class.java)

        return JSONObject().apply {
            put("evento", evento)
            if (motivoRemocao != null) put("motivoRemocao", motivoRemocao)
            put("capturadoEm", instante.format(Date()))
            // Contexto do aparelho no instante da captura: separa o caso bloqueado do desbloqueado (P-1).
            put("telaBloqueada", keyguard?.isKeyguardLocked ?: JSONObject.NULL)
            put("telaLigada", power?.isInteractive ?: JSONObject.NULL)

            put("key", sbn.key)
            put("id", sbn.id)
            put("tag", sbn.tag ?: JSONObject.NULL)
            put("pacote", sbn.packageName)
            put("postTime", sbn.postTime)
            put("ongoing", sbn.isOngoing)
            put("clearable", sbn.isClearable)
            put("groupKey", sbn.groupKey ?: JSONObject.NULL)

            put("categoria", n.category ?: JSONObject.NULL)
            put("canal", n.channelId ?: JSONObject.NULL)
            put("visibilidade", n.visibility)
            put("flags", n.flags)
            put("flagsDecodificadas", JSONArray(decodificaFlags(n.flags)))
            put("temFullScreenIntent", n.fullScreenIntent != null)
            put("temContentIntent", n.contentIntent != null)
            put("temDeleteIntent", n.deleteIntent != null)

            put("extras", deBundle(n.extras))
            put("acoes", deAcoes(n))
        }
    }

    private fun decodificaFlags(flags: Int): List<String> = buildList {
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) add("FLAG_ONGOING_EVENT")
        if (flags and Notification.FLAG_NO_CLEAR != 0) add("FLAG_NO_CLEAR")
        if (flags and Notification.FLAG_INSISTENT != 0) add("FLAG_INSISTENT")
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) add("FLAG_FOREGROUND_SERVICE")
        if (flags and Notification.FLAG_AUTO_CANCEL != 0) add("FLAG_AUTO_CANCEL")
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) add("FLAG_GROUP_SUMMARY")
        if (flags and Notification.FLAG_LOCAL_ONLY != 0) add("FLAG_LOCAL_ONLY")
        if (flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) add("FLAG_ONLY_ALERT_ONCE")
    }

    private fun deAcoes(n: Notification): JSONArray {
        val acoes = n.actions ?: return JSONArray()
        return JSONArray().apply {
            acoes.forEachIndexed { i, a ->
                put(
                    JSONObject().apply {
                        put("indice", i)
                        put("titulo", a.title?.toString() ?: JSONObject.NULL)
                        put("tituloNormalizado", Rotulos.normaliza(a.title?.toString() ?: ""))
                        // Sem actionIntent nao existe plano A (P-2 / P-3).
                        put("temActionIntent", a.actionIntent != null)
                        put("semanticAction", a.semanticAction)
                        put("temRemoteInput", (a.remoteInputs?.size ?: 0) > 0)
                        put("casaAtender", Rotulos.casa(a.title?.toString(), Rotulos.ATENDER))
                        put("casaDesligar", Rotulos.casa(a.title?.toString(), Rotulos.DESLIGAR))
                    },
                )
            }
        }
    }

    private fun deBundle(bundle: Bundle?): JSONObject {
        if (bundle == null) return JSONObject()
        return JSONObject().apply {
            for (chave in bundle.keySet()) {
                // put() rejeita NaN/Infinity e os extras vem do WhatsApp: isola a chave.
                runCatching { put(chave, valor(bundle, chave)) }
                    .onFailure { put(chave, "<erro: ${it.javaClass.simpleName}>") }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun valor(bundle: Bundle, chave: String): Any {
        val v = runCatching { bundle.get(chave) }.getOrNull() ?: return JSONObject.NULL
        return when (v) {
            is CharSequence -> v.toString()
            is Boolean, is Int, is Long, is Float, is Double -> v
            is Bundle -> deBundle(v)
            is Array<*> -> JSONArray().apply { v.forEach { put(descreve(it)) } }
            else -> descreve(v)
        }
    }

    private fun descreve(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is CharSequence -> v.toString()
        is Boolean, is Int, is Long, is Float, is Double -> v
        else -> "<${v.javaClass.name}>"
    }
}
