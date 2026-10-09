package br.atendepai.spike

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda o resultado de cada pergunta do spike no proprio aparelho.
 *
 * Existe para o experimento funcionar sem USB: o app se auto-avalia, mostra o
 * veredito na tela e o inclui na exportacao. Sem isso, as respostas de P-1 a P-6
 * so sairiam lendo `adb logcat`.
 */
object Veredito {

    enum class Resultado { NAO_TESTADO, SIM, NAO, PARCIAL }

    /** Ordem de exibicao. P-1 e separado porque a spec exige os dois cenarios (AC-03.1 e AC-03.2). */
    val PERGUNTAS: List<Pair<String, String>> = listOf(
        "P-1a" to "Bloqueado: chamada chega como CATEGORY_CALL com ação de atender",
        "P-1b" to "Desbloqueado: idem",
        "P-2" to "ATENDER funciona por alguma estratégia",
        "P-2.1" to "  - PendingIntent simples (linha de base)",
        "P-2.2" to "  - PendingIntent + background activity start (API 34)",
        "P-2.3" to "  - TelecomManager.acceptRingingCall()",
        "P-2.4" to "  - clique no botão por acessibilidade",
        "P-3" to "DESLIGAR pelo PendingIntent da notificação contínua",
        "P-3b" to "  - desligar por acessibilidade (reserva)",
        "P-4" to "setCommunicationDevice mantém o viva-voz",
        "P-4b" to "  - viva-voz por clique em acessibilidade (reserva)",
        "P-5" to "Gesto de volume chega à acessibilidade com a tela apagada",
        "P-6" to "Opção nativa do botão liga/desliga encerra chamada do WhatsApp",
    )

    private val horario = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("veredito", Context.MODE_PRIVATE)

    /**
     * @param preservarNao nao sobrescreve um "NAO" ja registrado. Usado em P-4, onde o
     *   viva-voz pode cair numa reconsulta e voltar na seguinte — a queda e o que importa.
     */
    fun registrar(
        ctx: Context,
        pergunta: String,
        resultado: Resultado,
        detalhe: String,
        preservarNao: Boolean = false,
    ) {
        if (preservarNao && ler(ctx, pergunta).first == Resultado.NAO) return
        sp(ctx).edit()
            .putString("$pergunta.r", resultado.name)
            .putString("$pergunta.d", detalhe)
            .putString("$pergunta.t", horario.format(Date()))
            .apply()
        SpikeLog.d(ctx, ">>> VEREDITO $pergunta = $resultado — $detalhe")
    }

    fun ler(ctx: Context, pergunta: String): Triple<Resultado, String, String> {
        val p = sp(ctx)
        val r = runCatching {
            Resultado.valueOf(p.getString("$pergunta.r", null) ?: Resultado.NAO_TESTADO.name)
        }.getOrDefault(Resultado.NAO_TESTADO)
        return Triple(r, p.getString("$pergunta.d", "") ?: "", p.getString("$pergunta.t", "") ?: "")
    }

    fun limpar(ctx: Context) {
        sp(ctx).edit().clear().apply()
        SpikeLog.d(ctx, ">>> vereditos apagados")
    }

    /** Relatorio legivel, mostrado na tela e no topo do arquivo exportado. */
    fun relatorio(ctx: Context): String = buildString {
        appendLine("RESPOSTAS DO SPIKE — plan.md seção 7")
        appendLine("gerado em ${horario.format(Date())}")
        appendLine()
        PERGUNTAS.forEach { (id, texto) ->
            val (resultado, detalhe, quando) = ler(ctx, id)
            appendLine("$id  [${simbolo(resultado)}] $resultado")
            appendLine("     $texto")
            if (detalhe.isNotBlank()) appendLine("     → $detalhe")
            if (quando.isNotBlank()) appendLine("     em $quando")
            appendLine()
        }
        appendLine("PARCIAL = precisa de confirmação humana (ouvido/olho), vide README.")
    }

    private fun simbolo(r: Resultado): String = when (r) {
        Resultado.SIM -> "OK"
        Resultado.NAO -> "FALHOU"
        Resultado.PARCIAL -> "??"
        Resultado.NAO_TESTADO -> "  "
    }
}
