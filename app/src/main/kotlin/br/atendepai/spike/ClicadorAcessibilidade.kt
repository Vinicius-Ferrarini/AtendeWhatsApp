package br.atendepai.spike

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Encontra e clica um botão pelo texto, varrendo todas as janelas.
 *
 * Varre todas, e não só a ativa, porque os dois cenários da chamada vivem em
 * janelas diferentes: com o celular bloqueado o botão está na tela cheia do
 * WhatsApp; desbloqueado, está no aviso flutuante, que pertence à SystemUI.
 *
 * Quando não encontra, devolve os textos que viu. Esse inventário é o que
 * permite descobrir o rótulo real sem ficar adivinhando — foi assim que se
 * soube que o botão se chama "Aceitar", não "Atender".
 */
object ClicadorAcessibilidade {

    private const val MAX_PROFUNDIDADE = 40
    private const val MAX_TEXTOS_NO_RELATORIO = 30

    fun clicarPorTexto(servico: AccessibilityService, alvos: List<String>): String {
        val contexto = estadoDaTela(servico)
        val janelas = runCatching { servico.windows }.getOrNull()
        if (janelas.isNullOrEmpty()) {
            // O caso critico: com a tela bloqueada pode nao haver janela alcancavel.
            return "SEM JANELA acessível [$contexto]"
        }

        val vistos = mutableListOf<String>()

        for (janela in janelas) {
            val raiz = janela.root ?: continue
            val achado = buscar(raiz, alvos, vistos, 0)
                ?: continue

            val visivel = achado.isVisibleToUser
            val clicavel = ancestralClicavel(achado)
                ?: return "achou '${textoDe(achado)}' mas nada clicável na linhagem [$contexto]"

            val ok = clicavel.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return if (ok) {
                "CLICOU em '${textoDe(achado)}' (pacote=${raiz.packageName}, " +
                    "janela=${janela.type}, visível=$visivel) [$contexto]"
            } else {
                "ACTION_CLICK devolveu false em '${textoDe(achado)}' (visível=$visivel) [$contexto]"
            }
        }

        val inventario = vistos.filter { it.isNotBlank() }.distinct()
        return "nenhum nó casou com $alvos em ${janelas.size} janela(s) [$contexto]. Textos: " +
            inventario.take(MAX_TEXTOS_NO_RELATORIO).joinToString(" | ") +
            if (inventario.size > MAX_TEXTOS_NO_RELATORIO) " … (+${inventario.size - MAX_TEXTOS_NO_RELATORIO})" else ""
    }

    /** Sem isto nao da para distinguir "nao achou o botao" de "a tela estava apagada". */
    fun estadoDaTela(ctx: Context): String {
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        val power = ctx.getSystemService(PowerManager::class.java)
        return "bloqueada=${keyguard?.isKeyguardLocked} telaLigada=${power?.isInteractive}"
    }

    /** Só inventaria o que está na tela, sem clicar. Útil para descobrir rótulos. */
    fun inventariar(servico: AccessibilityService): String {
        val janelas = runCatching { servico.windows }.getOrNull()
        if (janelas.isNullOrEmpty()) return "nenhuma janela acessível"
        return buildString {
            for (janela in janelas) {
                val raiz = janela.root ?: continue
                val vistos = mutableListOf<String>()
                buscar(raiz, emptyList(), vistos, 0)
                val textos = vistos.filter { it.isNotBlank() }.distinct()
                if (textos.isEmpty()) continue
                appendLine("janela ${janela.type} (${raiz.packageName}): ${textos.joinToString(" | ")}")
            }
        }.ifBlank { "nenhum texto encontrado nas janelas" }
    }

    private fun buscar(
        no: AccessibilityNodeInfo,
        alvos: List<String>,
        vistos: MutableList<String>,
        profundidade: Int,
    ): AccessibilityNodeInfo? {
        if (profundidade > MAX_PROFUNDIDADE) return null

        val texto = textoDe(no)
        if (texto.isNotBlank()) vistos.add(texto)
        if (alvos.isNotEmpty() && Rotulos.casa(texto, alvos)) return no

        for (i in 0 until no.childCount) {
            val filho = no.getChild(i) ?: continue
            val achado = buscar(filho, alvos, vistos, profundidade + 1)
            if (achado != null) return achado
        }
        return null
    }

    /** O nó com o texto costuma ser um TextView dentro de um botão; o clique vai no ancestral. */
    private fun ancestralClicavel(no: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var atual: AccessibilityNodeInfo? = no
        var subidas = 0
        while (atual != null && subidas <= 6) {
            if (atual.isClickable) return atual
            atual = atual.parent
            subidas++
        }
        return null
    }

    private fun textoDe(no: AccessibilityNodeInfo): String =
        no.text?.toString()?.takeIf { it.isNotBlank() }
            ?: no.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: ""
}
