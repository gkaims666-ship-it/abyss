package com.abyss.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.agent.Agent
import com.abyss.app.core.agent.AgentRegistry
import com.abyss.app.core.agent.Router
import com.abyss.app.core.data.ChatMessageEntity
import com.abyss.app.core.provider.LlmError
import com.abyss.app.core.provider.LlmMessage
import com.abyss.app.core.provider.LlmRequest
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.provider.Role
import com.abyss.app.core.settings.AbyssSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.abyss.app.ui.i18n.I18n

data class UiMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val agentLabel: String? = null,      // agent id; the UI shows its localised name
    val streaming: Boolean = false,
    val error: Boolean = false,
    // X-style inline translation
    val offerTranslate: Boolean = false,
    val sourceLang: String? = null,
    val translatedText: String? = null,
    val showTranslated: Boolean = false,
    val translating: Boolean = false,
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val settings: AbyssSettings = AbyssSettings(),
    val isBusy: Boolean = false,
    val routedAgent: String? = null,     // agent id chosen by Auto
    val input: String = "",
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val mediaStatus: String? = null,     // transient status line for media work
    val conversationId: String = "default",
    val conversations: List<com.abyss.app.core.data.ConversationSummary> = emptyList(),
)


class ChatViewModel : ViewModel() {

    private val memory = ServiceLocator.memory
    private val keyStore = ServiceLocator.keyStore
    private val settingsStore = ServiceLocator.settings
    private val chatDao = ServiceLocator.db.chatDao()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var localIdSeq = -1L
    private var streamJob: Job? = null

    private val convId get() = _state.value.conversationId

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            chatDao.observeConversationSummaries().collect { list ->
                _state.update { it.copy(conversations = list) }
            }
        }
        viewModelScope.launch { loadConversationMessages(convId) }
    }

    private suspend fun loadConversationMessages(id: String) {
        val history = chatDao.loadConversation(id)
        _state.update { st ->
            st.copy(messages = history.map {
                UiMessage(it.id, if (it.role == "user") Role.USER else Role.ASSISTANT,
                    it.content, it.agentId?.let { aid -> AgentRegistry.byId(aid).id })
            })
        }
    }

    fun newConversation() {
        if (_state.value.isBusy) return
        _state.update { it.copy(conversationId = "conv_${System.currentTimeMillis()}", messages = emptyList(), input = "") }
    }

    fun switchConversation(id: String) {
        if (_state.value.isBusy || id == convId) return
        _state.update { it.copy(conversationId = id, messages = emptyList()) }
        viewModelScope.launch { loadConversationMessages(id) }
    }

    /** Delete a whole conversation; if it's the open one, start a fresh chat. */
    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatDao.deleteConversation(id)
            if (id == convId) newConversation()
        }
    }

    fun deleteMessage(id: Long) {
        val msg = _state.value.messages.firstOrNull { it.id == id } ?: return
        _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == id }) }
        val role = if (msg.role == Role.USER) "user" else "assistant"
        viewModelScope.launch { chatDao.deleteMatching(convId, role, msg.content) }
    }


    /** Re-run the last user turn to get a fresh assistant reply. */
    fun regenerate() {
        if (_state.value.isBusy) return
        val msgs = _state.value.messages
        val lastUser = msgs.lastOrNull { it.role == Role.USER } ?: return
        // Drop any assistant messages after the last user message.
        val idx = msgs.indexOf(lastUser)
        val dropped = msgs.drop(idx + 1).filter { it.role == Role.ASSISTANT && !it.error }
        _state.update { it.copy(messages = it.messages.subList(0, idx + 1).toList(), isBusy = true) }
        val conv = convId
        streamJob = viewModelScope.launch {
            // Remove the replaced reply from storage too, or it reappears on restart.
            dropped.forEach { chatDao.deleteMatching(conv, "assistant", it.content) }
            runTurn(lastUser.content, _state.value.settings)
        }
    }

    fun onInputChange(text: String) = _state.update { it.copy(input = text) }

    fun setPalette(id: String) = viewModelScope.launch { settingsStore.setPalette(id) }
    fun setAgent(id: String) = viewModelScope.launch { settingsStore.setAgent(id) }
    fun setProvider(id: String) = viewModelScope.launch { settingsStore.setProvider(id) }
    fun setModel(model: String) = viewModelScope.launch { settingsStore.setModel(model) }
    fun toggleHarvest(on: Boolean) = viewModelScope.launch { settingsStore.setHarvest(on) }
    fun setLanguage(code: String) = viewModelScope.launch { settingsStore.setLanguage(code) }
    fun setPowerSave(on: Boolean) = viewModelScope.launch { settingsStore.setPowerSave(on) }

    fun stop() {
        streamJob?.cancel()
        _state.update { st ->
            st.copy(isBusy = false, messages = st.messages.map { it.copy(streaming = false) })
        }
    }

    fun send() {
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.isBusy) return

        val userMsg = UiMessage(localIdSeq--, Role.USER, text)
        _state.update { it.copy(messages = it.messages + userMsg, input = "", isBusy = true) }
        viewModelScope.launch {
            chatDao.insert(ChatMessageEntity(
                conversationId = convId, role = "user", content = text,
                providerId = s.settings.providerId, model = s.settings.model, agentId = null))
        }

        streamJob = viewModelScope.launch { runTurn(text, s.settings) }
    }

    private suspend fun runTurn(text: String, settings: AbyssSettings) {
        val provider = ProviderRegistry.byId(settings.providerId) ?: ProviderRegistry.openAi
        val apiKey = keyStore.getKey(provider.id)
        if (apiKey == null && provider.id != "local") {
            emitError(I18n.current.errNoKey(provider.displayName))
            return
        }
        val key = apiKey ?: ""

        // Pick the agent (explicit, or routed in Auto mode).
        val agent: Agent = if (settings.agentId == "auto") {
            val router = Router(completer = { req ->
                runCatching { provider.complete(req, key) }.getOrDefault("")
            })
            router.route(text, settings.model)
        } else {
            AgentRegistry.byId(settings.agentId)
        }
        _state.update { it.copy(routedAgent = if (settings.agentId == "auto") agent.id else null) }

        // Silent memory injection — never shown in the transcript.
        val memoryBlock = runCatching { memory.contextBlock(text) }.getOrDefault("")
        val systemPrompt = buildString {
            if (settings.systemPrompt.isNotBlank()) { append(settings.systemPrompt); append("\n\n") }
            append(agent.systemPrompt)
            if (memoryBlock.isNotBlank()) { append("\n\n"); append(memoryBlock) }
        }

        val history = _state.value.messages
            .filter { !it.error }
            .map { LlmMessage(it.role, it.content) }

        // Placeholder assistant message to stream into.
        val assistantId = localIdSeq--
        _state.update {
            it.copy(messages = it.messages + UiMessage(
                assistantId, Role.ASSISTANT, "", agentLabel = agent.id, streaming = true))
        }

        val request = LlmRequest(
            model = settings.model, messages = history,
            systemPrompt = systemPrompt, temperature = settings.temperature.toDouble(),
            stream = true)

        val sb = StringBuilder()
        // Tokens can arrive 50–100×/s; repainting the chat that often only costs frames.
        // Coalesce to ~30 updates/s — still reads as live typing.
        var lastPush = 0L
        fun pushStreamed() {
            val current = sb.toString()
            _state.update { st ->
                st.copy(messages = st.messages.map {
                    if (it.id == assistantId) it.copy(content = current) else it
                })
            }
        }
        try {
            provider.stream(request, key).collect { chunk ->
                if (chunk.done) return@collect
                sb.append(chunk.delta)
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastPush >= 33L) { lastPush = nowMs; pushStreamed() }
            }
            pushStreamed()
        } catch (e: Throwable) {
            val msg = (e as? LlmError)?.message ?: e.message ?: I18n.current.errRequestFailed
            _state.update { st ->
                st.copy(messages = st.messages.map {
                    if (it.id == assistantId)
                        it.copy(content = sb.toString().ifBlank { "⚠ $msg" }, streaming = false,
                            error = sb.isBlank())
                    else it
                }, isBusy = false)
            }
            persistAssistant(sb.toString(), agent.id, settings)
            return
        }

        _state.update { st ->
            st.copy(messages = st.messages.map {
                if (it.id == assistantId) it.copy(streaming = false) else it
            }, isBusy = false)
        }
        persistAssistant(sb.toString(), agent.id, settings)
        decideTranslation(assistantId, sb.toString(), settings)
    }

    /** X-style: detect the reply's language and auto-translate or offer it. */
    private fun decideTranslation(id: Long, text: String, settings: AbyssSettings) {
        viewModelScope.launch {
            val decision = runCatching {
                ServiceLocator.translate.decide(text, settings)
            }.getOrNull() ?: return@launch
            if (!decision.shouldOffer) return@launch
            if (settings.autoTranslate) {
                _state.update { st -> st.copy(messages = st.messages.map {
                    if (it.id == id) it.copy(translating = true, sourceLang = decision.sourceLang) else it }) }
                val res = ServiceLocator.translate.translate(text, settings, decision.sourceLang)
                _state.update { st -> st.copy(messages = st.messages.map {
                    if (it.id == id) it.copy(
                        translating = false,
                        translatedText = res.getOrNull()?.text,
                        showTranslated = res.isSuccess,
                        offerTranslate = res.isFailure) else it }) }
            } else {
                _state.update { st -> st.copy(messages = st.messages.map {
                    if (it.id == id) it.copy(offerTranslate = true, sourceLang = decision.sourceLang) else it }) }
            }
        }
    }

    fun translateMessage(id: Long) {
        val msg = _state.value.messages.firstOrNull { it.id == id } ?: return
        val settings = _state.value.settings
        if (msg.translatedText != null) { // already have it — just show
            _state.update { st -> st.copy(messages = st.messages.map {
                if (it.id == id) it.copy(showTranslated = true) else it }) }
            return
        }
        viewModelScope.launch {
            _state.update { st -> st.copy(messages = st.messages.map {
                if (it.id == id) it.copy(translating = true) else it }) }
            val res = ServiceLocator.translate.translate(msg.content, settings, msg.sourceLang)
            _state.update { st -> st.copy(messages = st.messages.map {
                if (it.id == id) it.copy(
                    translating = false,
                    translatedText = res.getOrNull()?.text ?: it.translatedText,
                    showTranslated = res.isSuccess,
                    offerTranslate = res.isFailure) else it }) }
        }
    }

    fun toggleOriginal(id: Long) {
        _state.update { st -> st.copy(messages = st.messages.map {
            if (it.id == id) it.copy(showTranslated = !it.showTranslated) else it }) }
    }

    // ---- Voice messages ----

    fun startRecording() {
        if (_state.value.recording || _state.value.isBusy) return
        val ok = runCatching { ServiceLocator.audioRecorder.start() }.isSuccess
        if (ok) _state.update { it.copy(recording = true) }
        else emitError(I18n.current.errMic)
    }

    fun cancelRecording() {
        runCatching { ServiceLocator.audioRecorder.cancel() }
        _state.update { it.copy(recording = false) }
    }

    /** Stop recording, transcribe the clip, then send the transcript as a turn. */
    fun stopRecordingAndSend() {
        if (!_state.value.recording) return
        val file = runCatching { ServiceLocator.audioRecorder.stop() }.getOrNull()
        _state.update { it.copy(recording = false, transcribing = file != null) }
        if (file == null) return
        viewModelScope.launch {
            val res = ServiceLocator.mediaAI.transcribe(file)
            runCatching { file.delete() }
            _state.update { it.copy(transcribing = false) }
            res.onSuccess { text ->
                if (text.isNotBlank()) { onInputChange(text); send() }
                else emitError(I18n.current.errEmptyTranscript)
            }.onFailure { emitError(it.message ?: I18n.current.errTranscription) }
        }
    }

    // ---- Captured media (video / circle / photo) ----

    /** Analyze a captured file and post the model's description/answer. */
    fun analyzeCapturedMedia(path: String, mime: String, kind: String, prompt: String) {
        val file = java.io.File(path)
        if (!file.exists()) { emitError(I18n.current.errCaptureMissing); return }
        val kindLabel = I18n.current.mediaKind(kind)
        val userMsg = UiMessage(localIdSeq--, Role.USER, "[$kindLabel] $prompt")
        val assistantId = localIdSeq--
        _state.update {
            it.copy(messages = it.messages + userMsg +
                UiMessage(assistantId, Role.ASSISTANT, "", agentLabel = "media", streaming = true),
                mediaStatus = I18n.current.analyzing(kindLabel))
        }
        viewModelScope.launch {
            chatDao.insert(ChatMessageEntity(conversationId = convId, role = "user",
                content = "[$kindLabel] $prompt", providerId = "gemini", model = null, agentId = "media"))
            val res = ServiceLocator.mediaAI.understand(file, mime, prompt)
            runCatching { file.delete() }
            val text = res.getOrElse { "⚠ ${it.message}" }
            _state.update { st -> st.copy(mediaStatus = null, messages = st.messages.map {
                if (it.id == assistantId) it.copy(content = text, streaming = false,
                    error = res.isFailure) else it }) }
            if (res.isSuccess) {
                chatDao.insert(ChatMessageEntity(conversationId = convId, role = "assistant",
                    content = text, providerId = "gemini", model = null, agentId = "media"))
            }
        }
    }

    private fun persistAssistant(content: String, agentId: String, settings: AbyssSettings) {
        if (content.isBlank()) return
        viewModelScope.launch {
            chatDao.insert(ChatMessageEntity(
                conversationId = convId, role = "assistant", content = content,
                providerId = settings.providerId, model = settings.model, agentId = agentId))
        }
    }

    private fun emitError(message: String) {
        _state.update {
            it.copy(
                messages = it.messages + UiMessage(localIdSeq--, Role.ASSISTANT, "⚠ $message",
                    error = true),
                isBusy = false)
        }
    }
}
