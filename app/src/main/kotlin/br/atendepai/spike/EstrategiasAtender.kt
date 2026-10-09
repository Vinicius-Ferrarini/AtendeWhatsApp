package br.atendepai.spike

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager

/**
 * As quatro formas de atender que o spike testa, da menos invasiva para a mais.
 *
 * A medição de 2026-10-08 mostrou que [PENDING_SIMPLES] não funciona no HyperOS:
 * `send()` não lança exceção e não tem efeito. A hipótese é que atender exige
 * iniciar um serviço em primeiro plano com microfone, o que o Android 14 nega a
 * um disparo programático em segundo plano — desligar, que não precisa de
 * microfone, passa pelo mesmo caminho sem problema.
 *
 * As outras três atacam essa hipótese por ângulos diferentes.
 */
enum class Estrategia(val id: String, val rotulo: String) {
    PENDING_SIMPLES("P-2.1", "PendingIntent simples (linha de base, já falhou)"),
    PENDING_BAL("P-2.2", "PendingIntent + background activity start permitido (API 34)"),
    TELECOM("P-2.3", "TelecomManager.acceptRingingCall()"),
    ACESSIBILIDADE("P-2.4", "Clique no botão por acessibilidade"),
}

object EstrategiasAtender {

    /**
     * Dispara a estratégia e devolve um diagnóstico do disparo.
     *
     * O retorno **não** diz se a chamada foi atendida: quem decide isso é a
     * chegada da notificação contínua, observada pela [CascataAtendimento].
     */
    fun disparar(ctx: Context, estrategia: Estrategia, sbn: StatusBarNotification?): String =
        when (estrategia) {
            Estrategia.PENDING_SIMPLES -> pendingSimples(ctx, sbn)
            Estrategia.PENDING_BAL -> pendingComBal(ctx, sbn)
            Estrategia.TELECOM -> telecom(ctx)
            Estrategia.ACESSIBILIDADE -> acessibilidade()
        }

    /** Diz se a estratégia tem como ser tentada agora, e por que não, se for o caso. */
    fun impedimento(ctx: Context, estrategia: Estrategia): String? = when (estrategia) {
        Estrategia.PENDING_BAL ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                "exige Android 14 (API 34); este aparelho é API ${Build.VERSION.SDK_INT}"
            } else {
                null
            }

        Estrategia.TELECOM ->
            if (!temPermissaoAtenderChamadas(ctx)) "falta a permissão ANSWER_PHONE_CALLS" else null

        Estrategia.ACESSIBILIDADE ->
            if (SpikeAccessibilityService.instancia == null) "serviço de acessibilidade desativado" else null

        Estrategia.PENDING_SIMPLES -> null
    }

    fun temPermissaoAtenderChamadas(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED

    private fun pendingSimples(ctx: Context, sbn: StatusBarNotification?): String {
        val pendente = acaoAtender(sbn) ?: return "sem ação de atender com PendingIntent"
        return try {
            pendente.send()
            "send() sem exceção"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Concede explicitamente a isenção de *background activity launch* que falta
     * no `send()` simples. É a API criada justamente para este caso.
     */
    private fun pendingComBal(ctx: Context, sbn: StatusBarNotification?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return "indisponível: API ${Build.VERSION.SDK_INT} < 34"
        }
        val pendente = acaoAtender(sbn) ?: return "sem ação de atender com PendingIntent"
        return try {
            val opcoes = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            pendente.send(ctx, 0, null, null, null, null, opcoes.toBundle())
            "send() com MODE_BACKGROUND_ACTIVITY_START_ALLOWED, sem exceção"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Só funciona se esta versão do WhatsApp registrar as chamadas no Telecom.
     * Se registrar, é o caminho mais limpo: nada de acessibilidade.
     */
    private fun telecom(ctx: Context): String {
        if (!temPermissaoAtenderChamadas(ctx)) return "falta a permissão ANSWER_PHONE_CALLS"
        val telecom = ctx.getSystemService(TelecomManager::class.java)
            ?: return "TelecomManager indisponível"
        return try {
            @Suppress("MissingPermission")
            telecom.acceptRingingCall()
            "acceptRingingCall() chamado sem exceção (silencioso se o WhatsApp não usa Telecom)"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** O clique sintético conta como interação do usuário, que é o que falta ao `send()`. */
    private fun acessibilidade(): String {
        val servico = SpikeAccessibilityService.instancia
            ?: return "serviço de acessibilidade desativado"
        return ClicadorAcessibilidade.clicarPorTexto(servico, Rotulos.ATENDER)
    }

    private fun acaoAtender(sbn: StatusBarNotification?): PendingIntent? =
        sbn?.notification?.actions
            ?.firstOrNull { Rotulos.casa(it.title?.toString(), Rotulos.ATENDER) }
            ?.actionIntent
}
