package com.andloading.assistant.agent

import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import com.andloading.assistant.data.SettingsStore
import com.andloading.assistant.data.model.Role
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class ChatMessage(
    val role: String,
    val content: JsonElement? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
) {
    companion object {
        fun text(role: String, text: String, toolCalls: List<ToolCallDto>? = null,
                 toolCallId: String? = null, name: String? = null) =
            ChatMessage(role, JsonPrimitive(text), toolCalls, toolCallId, name)

        fun multimodalUser(text: String, imageDataUrl: String): ChatMessage {
            val arr = JsonArray()
            arr.add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", text)
            })
            arr.add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply { addProperty("url", imageDataUrl) })
            })
            return ChatMessage(Role.USER, arr)
        }
    }

    fun contentText(): String = when (content) {
        is JsonPrimitive -> content.asString
        else -> ""
    }

    fun hasImage(): Boolean = imageCount() > 0

    fun imageCount(): Int = when (content) {
        is com.google.gson.JsonArray ->
            content.count { it.isJsonObject && it.asJsonObject.has("image_url") }
        else -> 0
    }
}

data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDto,
)

data class FunctionCallDto(
    val name: String,
    val arguments: String,
)

data class FunctionSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class ToolSpec(
    val type: String = "function",
    val function: FunctionSpec,
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec>? = null,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val temperature: Double = 0.2,
    @SerializedName("stream") val stream: Boolean? = null,
)

data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ApiError? = null,
)

data class ApiError(val message: String? = null)

data class Choice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String? = null,
)

interface ChatCompletionsApi {
    @POST("chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

class LLMClient(private val settings: SettingsStore) {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${settings.apiKey}")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    private val api: ChatCompletionsApi by lazy {
        Retrofit.Builder()
            .baseUrl(settings.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatCompletionsApi::class.java)
    }

    private fun pickModel(messages: List<ChatMessage>): String {
        val vision = settings.visionModel.trim()
        return if (vision.isNotEmpty() && messages.any { it.hasImage() }) vision else settings.model
    }

    suspend fun chat(messages: List<ChatMessage>, tools: List<ToolSpec> = emptyList()): Choice {
        val response = api.chat(
            ChatRequest(
                model = pickModel(messages),
                messages = messages,
                tools = tools.ifEmpty { null },
            ),
        )
        response.error?.let { throw IllegalStateException("API 错误: ${it.message}") }
        val choice = response.choices?.firstOrNull()
            ?: throw IllegalStateException("API 返回为空")
        checkNotNull(choice.message) { "API 返回 message 为空" }
        return choice
    }

    sealed interface StreamEvent {
        data class TextDelta(val text: String) : StreamEvent
        data class ToolCallsReady(val calls: List<ToolCallDto>) : StreamEvent
    }

    fun chatStreamTools(messages: List<ChatMessage>, tools: List<ToolSpec>): Flow<StreamEvent> = flow {
        val payload = gson.toJson(
            ChatRequest(
                model = pickModel(messages),
                messages = messages,
                tools = tools.ifEmpty { null },
                stream = true,
            ),
        )
        val request = Request.Builder()
            .url(settings.baseUrl + "chat/completions")
            .post(payload.toRequestBody(jsonType))
            .build()

        val pendingCalls = LinkedHashMap<Int, ToolCallDto>()
        val argBuilders = HashMap<Int, StringBuilder>()

        val call = client.newCall(request)
        val completionHandle = currentCoroutineContext().let { ctx ->
            ctx.job.invokeOnCompletion { call.cancel() }
        }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty().take(300)
                    throw IOException("HTTP ${resp.code}: $errBody")
                }
                val source = resp.body?.source() ?: throw IOException("响应体为空")
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { gson.fromJson(data, JsonObject::class.java) }
                        .getOrNull() ?: continue
                    val choice = chunk.getAsJsonArray("choices")?.firstOrNull()
                        ?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val delta = choice.getAsJsonObject("delta") ?: continue
                    delta.get("content")?.takeIf { it.isJsonPrimitive }?.let { c ->
                        if (c.asString.isNotEmpty()) emit(StreamEvent.TextDelta(c.asString))
                    }
                    delta.getAsJsonArray("tool_calls")?.forEach { tcEl ->
                        if (!tcEl.isJsonObject) return@forEach
                        val tc = tcEl.asJsonObject
                        val idx = tc.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                        val fn = tc.getAsJsonObject("function")
                        val existing = pendingCalls[idx]
                        val id = tc.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: existing?.id ?: ""
                        val name = fn?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: existing?.function?.name ?: ""
                        val frag = fn?.get("arguments")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                        argBuilders.getOrPut(idx) { StringBuilder() }.append(frag)
                        pendingCalls[idx] = ToolCallDto(
                            id = id,
                            function = FunctionCallDto(name, argBuilders[idx]?.toString() ?: frag),
                        )
                    }
                }
            }
            if (pendingCalls.isNotEmpty()) {
                emit(StreamEvent.ToolCallsReady(pendingCalls.values.toList()))
            }
        } finally {
            completionHandle.dispose()
        }
    }.flowOn(Dispatchers.IO)
}
