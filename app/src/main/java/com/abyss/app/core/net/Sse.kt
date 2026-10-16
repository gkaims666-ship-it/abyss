package com.abyss.app.core.net

import com.abyss.app.core.provider.LlmError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

data class SseEvent(val type: String?, val data: String)

/**
 * Open [request] as a Server-Sent-Events stream and emit each event. Closes
 * normally when the server ends the stream, and fails the flow with an
 * [LlmError] on HTTP or transport errors.
 */
fun sseFlow(request: Request): Flow<SseEvent> = callbackFlow {
    val listener = object : EventSourceListener() {
        override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
            trySend(SseEvent(type, data))
        }

        override fun onClosed(source: EventSource) {
            close()
        }

        override fun onFailure(source: EventSource, t: Throwable?, response: Response?) {
            val code = response?.code ?: -1
            val body = try { response?.body?.string().orEmpty() } catch (_: Throwable) { "" }
            val err = when {
                response != null && code !in 200..299 -> LlmError.Http(code, body)
                t != null -> LlmError.Network(t)
                else -> LlmError.Network(RuntimeException("SSE failed"))
            }
            close(err)
        }
    }
    val source = EventSources.createFactory(Http.client).newEventSource(request, listener)
    awaitClose { source.cancel() }
}
