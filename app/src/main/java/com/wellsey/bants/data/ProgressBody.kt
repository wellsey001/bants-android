package com.wellsey.bants.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** Wraps an upload so we can show how far it's got. Reports at most once per percent. */
class ProgressBody(private val inner: RequestBody, private val onProgress: (Float) -> Unit) : RequestBody() {
    override fun contentType(): MediaType? = inner.contentType()
    override fun contentLength(): Long = inner.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var sent = 0L
        var last = -1
        val counting = object : ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                sent += byteCount
                if (total > 0) {
                    val pct = (sent * 100 / total).toInt()
                    if (pct != last) {
                        last = pct
                        onProgress(pct / 100f)
                    }
                }
            }
        }.buffer()
        inner.writeTo(counting)
        counting.flush()
    }
}
