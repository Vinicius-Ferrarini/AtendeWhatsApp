package br.atendepai.spike

import java.text.Normalizer

/**
 * Comparacao de rotulos normalizada (minusculas, sem acento) — mesma estrategia
 * prevista para producao no plan.md secao 6.
 */
object Rotulos {

    /** "Aceitar" vem primeiro: e o rotulo real observado no WhatsApp em 2026-10-08. */
    val ATENDER = listOf("aceitar", "atender", "answer", "accept", "responder")

    val DESLIGAR = listOf("desligar", "recusar", "encerrar", "decline", "reject", "hang up", "end call")

    /** Botao de viva-voz dentro da tela da chamada, para o plano B de P-4. */
    val ALTO_FALANTE = listOf("alto-falante", "alto falante", "viva-voz", "viva voz", "speaker")

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
