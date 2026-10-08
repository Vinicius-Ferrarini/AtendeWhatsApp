package br.atendepai.spike

import android.content.Context

/** Estado do experimento, ajustavel por `adb shell am broadcast` ou pela tela. */
object Prefs {

    const val ATRASO_PADRAO = 10

    private fun sp(ctx: Context) = ctx.getSharedPreferences("spike", Context.MODE_PRIVATE)

    fun autoAtender(ctx: Context): Boolean = sp(ctx).getBoolean("auto_atender", false)

    fun setAutoAtender(ctx: Context, ligado: Boolean) {
        sp(ctx).edit().putBoolean("auto_atender", ligado).apply()
    }

    fun atrasoSegundos(ctx: Context): Int = sp(ctx).getInt("atraso", ATRASO_PADRAO)

    fun setAtrasoSegundos(ctx: Context, segundos: Int) {
        sp(ctx).edit().putInt("atraso", segundos).apply()
    }
}
