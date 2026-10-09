package br.atendepai.spike

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Responde P-4: `setCommunicationDevice(BUILTIN_SPEAKER)` mantem o viva-voz
 * ou o WhatsApp devolve o audio ao alto-falante de ouvido?
 *
 * Reconsulta o estado varias vezes depois de forcar, porque a devolucao do audio
 * costuma acontecer segundos depois.
 */
object AudioProbe {

    private val RECONSULTAS_MS = listOf(2_000L, 5_000L, 10_000L, 20_000L)

    /** Evita clicar no botao de viva-voz uma vez por reconsulta, o que o alternaria. */
    private var jaTentouAcessibilidade = false

    fun forcarVivaVoz(ctx: Context) {
        jaTentouAcessibilidade = false

        // O botao de viva-voz so existe na tela de chamada EM ANDAMENTO. Na v0.2 a
        // sonda rodou com a chamada ainda tocando e o inventario, naturalmente,
        // nao achou nada — o aviso aqui evita repetir o engano.
        if (!NotificationDumpService.chamadaEmAndamento) {
            SpikeLog.d(
                ctx,
                "P-4 AVISO: nenhuma chamada em andamento. O botao de viva-voz nao existe " +
                    "na tela de chamada tocando; o resultado nao vale.",
            )
        }
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am == null) {
            SpikeLog.d(ctx, "P-4: AudioManager indisponivel")
            return
        }
        SpikeLog.d(ctx, "P-4 antes: ${estado(ctx)}")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
            SpikeLog.d(ctx, "P-4: API < 31, usei isSpeakerphoneOn = true")
        } else {
            val alto = am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (alto == null) {
                val disponiveis = am.availableCommunicationDevices.joinToString { nomeTipo(it.type) }
                SpikeLog.d(ctx, "P-4 FALHA: BUILTIN_SPEAKER fora de availableCommunicationDevices [$disponiveis]")
                return
            }
            val ok = am.setCommunicationDevice(alto)
            SpikeLog.d(ctx, "P-4 setCommunicationDevice(BUILTIN_SPEAKER) = $ok")
            if (!ok) {
                Veredito.registrar(
                    ctx, "P-4", Veredito.Resultado.NAO,
                    "setCommunicationDevice devolveu false → plano B: clicar no botão de alto-falante",
                )
                return
            }
            Veredito.registrar(
                ctx, "P-4", Veredito.Resultado.PARCIAL,
                "viva-voz forçado; aguardando as reconsultas de 2 a 20 s",
            )
        }

        val handler = Handler(Looper.getMainLooper())
        RECONSULTAS_MS.forEach { ms ->
            handler.postDelayed({ reconsulta(ctx, ms) }, ms)
        }
    }

    /**
     * O WhatsApp costuma devolver o audio ao alto-falante de ouvido alguns segundos
     * depois, por isso a resposta de P-4 so fecha na ultima reconsulta.
     */
    private fun reconsulta(ctx: Context, ms: Long) {
        SpikeLog.d(ctx, "P-4 +${ms}ms: ${estado(ctx)}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val tipo = am.communicationDevice?.type

        if (tipo != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            Veredito.registrar(
                ctx, "P-4", Veredito.Resultado.NAO,
                "voltou para ${nomeTipo(tipo ?: -1)} depois de ${ms}ms " +
                    "→ plano B: clicar no botão de alto-falante",
            )
            tentarPorAcessibilidade(ctx)
            return
        }
        if (ms == RECONSULTAS_MS.last()) {
            // preservarNao: se caiu numa reconsulta anterior, a queda e o que vale.
            Veredito.registrar(
                ctx, "P-4", Veredito.Resultado.SIM,
                "continuou em BUILTIN_SPEAKER por ${ms}ms",
                preservarNao = true,
            )
        }
    }

    /**
     * P-4b: reserva para quando o WhatsApp devolve o audio ao ouvido.
     *
     * Clica no botao de viva-voz dentro da tela da chamada. Só e tentada uma vez
     * por chamada, para nao ficar alternando o alto-falante a cada reconsulta.
     */
    private fun tentarPorAcessibilidade(ctx: Context) {
        if (jaTentouAcessibilidade) return
        jaTentouAcessibilidade = true

        val servico = SpikeAccessibilityService.instancia
        if (servico == null) {
            Veredito.registrar(
                ctx, "P-4b", Veredito.Resultado.NAO_TESTADO,
                "acessibilidade desativada",
            )
            return
        }
        val diagnostico = ClicadorAcessibilidade.clicarPorTexto(servico, Rotulos.ALTO_FALANTE)
        SpikeLog.d(ctx, "P-4b viva-voz por acessibilidade: $diagnostico")
        Veredito.registrar(
            ctx,
            "P-4b",
            if (diagnostico.startsWith("CLICOU")) Veredito.Resultado.PARCIAL else Veredito.Resultado.NAO,
            diagnostico,
        )
    }

    fun estado(ctx: Context): String {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return "sem AudioManager"
        val dispositivo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.communicationDevice?.let { "${nomeTipo(it.type)}(${it.type})" } ?: "null"
        } else {
            "n/d"
        }
        @Suppress("DEPRECATION")
        val viva = am.isSpeakerphoneOn
        return "mode=${nomeModo(am.mode)} communicationDevice=$dispositivo speakerphoneOn=$viva"
    }

    private fun nomeTipo(tipo: Int): String = when (tipo) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        else -> "TIPO_$tipo"
    }

    private fun nomeModo(modo: Int): String = when (modo) {
        AudioManager.MODE_NORMAL -> "NORMAL"
        AudioManager.MODE_RINGTONE -> "RINGTONE"
        AudioManager.MODE_IN_CALL -> "IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
        else -> "MODO_$modo"
    }
}
