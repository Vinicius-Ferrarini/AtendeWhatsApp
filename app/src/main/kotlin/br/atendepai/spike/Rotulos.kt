package br.atendepai.spike

import java.text.Normalizer

/**
 * Comparacao de rotulos normalizada (minusculas, sem acento) — mesma estrategia
 * prevista para producao no plan.md secao 6.
 */
object Rotulos {

    val ATENDER = listOf("atender", "answer", "aceitar", "accept", "responder")

    val DESLIGAR = listOf("desligar", "recusar", "encerrar", "decline", "reject", "hang up", "end call")

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
