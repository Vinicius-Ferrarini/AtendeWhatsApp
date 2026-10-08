package br.atendepai.spike

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/** Logcat (`adb logcat -s AtendeSpike`) + arquivo interno, para `run-as ... cat files/spike.log`. */
object SpikeLog {

    const val TAG = "AtendeSpike"

    private const val LIMITE_LINHA_LOGCAT = 3000

    private val horario = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(ctx: Context?, msg: String) {
        Log.d(TAG, msg)
        gravar(ctx, "${horario.format(Date())} $msg")
    }

    fun json(ctx: Context?, json: JSONObject) {
        val texto = json.toString(2)
        texto.chunked(LIMITE_LINHA_LOGCAT).forEachIndexed { i, parte ->
            Log.d(TAG, "json[$i] $parte")
        }
        gravar(ctx, texto)
    }

    fun arquivoLog(ctx: Context): File = File(ctx.filesDir, "spike.log")

    private fun gravar(ctx: Context?, texto: String) {
        if (ctx == null) return
        runCatching { arquivoLog(ctx).appendText(texto + "\n") }
    }
}
