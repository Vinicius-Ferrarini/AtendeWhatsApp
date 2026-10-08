package br.atendepai.spike

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tira os resultados do aparelho sem USB, sem root e sem gerenciador de arquivos.
 *
 * Grava em Downloads via MediaStore (a URI devolvida pelo insert ja e compartilhavel,
 * entao nao precisa de FileProvider nem de dependencia AndroidX) e abre a folha de
 * compartilhamento para mandar por WhatsApp, e-mail ou Drive.
 */
object LogExporter {

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /** Grava relatorio + log + fixture em Downloads. Devolve o que conseguiu gravar. */
    fun exportar(ctx: Context): List<Uri> {
        val marca = stamp.format(Date())
        val uris = mutableListOf<Uri>()

        gravar(ctx, "atende-spike-RESPOSTAS-$marca.txt", "text/plain", Veredito.relatorio(ctx))
            ?.let(uris::add)

        val log = SpikeLog.arquivoLog(ctx)
        if (log.exists()) {
            gravar(ctx, "atende-spike-log-$marca.txt", "text/plain", log.readText())?.let(uris::add)
        } else {
            SpikeLog.d(ctx, "exportar: ainda nao existe spike.log")
        }

        val fixture = File(ctx.filesDir, "whatsapp_chamada.json")
        if (fixture.exists()) {
            gravar(ctx, "whatsapp_chamada-$marca.json", "application/json", fixture.readText())
                ?.let(uris::add)
        }

        SpikeLog.d(ctx, "exportar: ${uris.size} arquivo(s) em Downloads")
        return uris
    }

    fun compartilhar(ctx: Context, uris: List<Uri>) {
        if (uris.isEmpty()) {
            SpikeLog.d(ctx, "compartilhar: nada a enviar")
            return
        }
        val envio = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_SUBJECT, "Atende Spike — resultados")
            putExtra(Intent.EXTRA_TEXT, Veredito.relatorio(ctx))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(Intent.createChooser(envio, "Enviar resultados")) }
            .onFailure { SpikeLog.d(ctx, "compartilhar falhou: ${it.message}") }
    }

    private fun gravar(ctx: Context, nome: String, mime: String, conteudo: String): Uri? {
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return runCatching {
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: error("insert devolveu null")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(conteudo.toByteArray()) }
                ?: error("sem stream de escrita")
            uri
        }.onFailure {
            SpikeLog.d(ctx, "falha ao gravar $nome: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()
    }
}
