package br.atendepai.spike

import java.text.Normalizer

/**
 * Comparacao de rotulos normalizada (minusculas, sem acento) — mesma estrategia
 * prevista para producao no plan.md secao 6.
 */
object Rotulos {

    /**
     * Rotulos reais, capturados pelo inventario de acessibilidade em 2026-10-09:
     * a tela da chamada tocando mostra "Aceitar ligacao", "Recusar ligacao" e "Responder".
     *
     * "aceitar" vem primeiro e casa por substring com "Aceitar ligacao".
     * Cuidado: "responder" responde por mensagem, nao atende — fica fora da lista.
     */
    val ATENDER = listOf("aceitar", "atender", "answer", "accept")

    /** "recusar" casa com "Recusar ligacao" na tela tocando; "desligar" com a notificacao continua. */
    val DESLIGAR = listOf("desligar", "recusar", "encerrar", "decline", "reject", "hang up", "end call")

    /**
     * Botao de viva-voz da tela de chamada **em andamento** — nao existe na tela
     * tocando, onde a v0.2 o procurou por engano.
     */
    val ALTO_FALANTE = listOf(
        "alto-falante", "alto falante", "viva-voz", "viva voz", "speaker",
        "audio", "som", "loudspeaker",
    )

    fun normaliza(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace("""\p{Mn}+""".toRegex(), "")
            .lowercase()
            .trim()

    fun casa(titulo: String?, rotulos: List<String>): Boolean {
        val t = normaliza(titulo ?: return false)
        return rotulos.any { t.contains(normaliza(it)) }
    }
}
