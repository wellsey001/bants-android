package com.wellsey.bants.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Reads and writes the on-phone copy of chats and the outbox. */
class Store(context: Context) {
    private val dir = File(context.filesDir, "store").apply { mkdirs() }
    private val cacheFile = File(dir, "cache.json")
    private val outboxFile = File(dir, "outbox.json")
    val outboxDir = File(context.filesDir, "outbox").apply { mkdirs() }

    fun loadCache(): CacheSnapshot? = runCatching {
        if (!cacheFile.exists()) null
        else BantsJson.decodeFromString(CacheSnapshot.serializer(), cacheFile.readText())
    }.getOrNull()

    fun saveCache(snapshot: CacheSnapshot) {
        writeAtomically(cacheFile, BantsJson.encodeToString(CacheSnapshot.serializer(), snapshot))
    }

    fun loadOutbox(): List<OutboxItem> = runCatching {
        if (!outboxFile.exists()) emptyList()
        else BantsJson.decodeFromString(ListSerializer(OutboxItem.serializer()), outboxFile.readText())
    }.getOrDefault(emptyList())

    fun saveOutbox(items: List<OutboxItem>) {
        writeAtomically(outboxFile, BantsJson.encodeToString(ListSerializer(OutboxItem.serializer()), items))
    }

    fun clear() {
        cacheFile.delete()
        outboxFile.delete()
        outboxDir.listFiles()?.forEach { it.delete() }
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}
