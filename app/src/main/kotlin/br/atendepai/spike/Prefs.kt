package br.atendepai.spike

import android.content.Context

/** Estado do experimento, ajustavel por `adb shell am broadcast` ou pela tela. */
object Prefs {

    const val ATRASO_PADRAO = 10

    /** Sobe a cada versao que precisa descartar preferencia antiga. */
    private const val VERSAO_ATUAL = 3

    private fun sp(ctx: Context) = ctx.getSharedPreferences("spike", Context.MODE_PRIVATE)

    /**
     * Na v0.2 o seletor de estrategia da tela parecia um botao de acao: quem
     * apertasse os quatro acabava com a cascata fixada na ultima. A migracao
     * devolve a cascata, que e o que responde tudo numa ligacao so.
     */
    fun migrarSeNecessario(ctx: Context) {
        val p = sp(ctx)
        if (p.getInt("versao", 0) >= VERSAO_ATUAL) return
        p.edit()
            .remove("estrategia")
            .putBoolean("auto_atender", true)
            .putInt("versao", VERSAO_ATUAL)
            .apply()
        SpikeLog.d(ctx, "prefs migradas para v$VERSAO_ATUAL: cascata + auto-atender LIGADO")
    }

    /** Ligado por padrao: atender sozinho e o proposito do app, nao um extra. */
    fun autoAtender(ctx: Context): Boolean = sp(ctx).getBoolean("auto_atender", true)

    fun setAutoAtender(ctx: Context, ligado: Boolean) {
        sp(ctx).edit().putBoolean("auto_atender", ligado).apply()
    }

    fun atrasoSegundos(ctx: Context): Int = sp(ctx).getInt("atraso", ATRASO_PADRAO)

    fun setAtrasoSegundos(ctx: Context, segundos: Int) {
        sp(ctx).edit().putInt("atraso", segundos).apply()
    }

    /**
     * Estrategia unica a testar, ou `null` para rodar a cascata inteira.
     *
     * A cascata e o padrao: responde tudo numa ligacao so. Fixar uma estrategia
     * serve para repetir um resultado duvidoso sem interferencia das outras.
     */
    fun estrategiaFixa(ctx: Context): Estrategia? {
        val nome = sp(ctx).getString("estrategia", null) ?: return null
        return runCatching { Estrategia.valueOf(nome) }.getOrNull()
    }

    fun setEstrategiaFixa(ctx: Context, estrategia: Estrategia?) {
        val editor = sp(ctx).edit()
        if (estrategia == null) editor.remove("estrategia") else editor.putString("estrategia", estrategia.name)
        editor.apply()
    }

    /** A fila que a cascata vai percorrer nesta ligacao. */
    fun filaDeEstrategias(ctx: Context): List<Estrategia> =
        estrategiaFixa(ctx)?.let { listOf(it) } ?: Estrategia.entries.toList()
}
