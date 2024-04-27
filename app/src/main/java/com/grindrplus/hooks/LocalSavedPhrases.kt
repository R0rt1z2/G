package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField
import java.lang.reflect.Constructor
import java.lang.reflect.Proxy

class LocalSavedPhrases: Hook("Local saved phrases",
    "Save unlimited phrases locally") {
    private val phrasesRestService = "v5.k"
    private val createSuccessResult = "ka.a\$b"
    private val retrofit = "retrofit2.Retrofit"
    private val chatRestService = "com.grindrapp.android.chat.api.ChatRestService"
    private val addSavedPhraseResponse = "com.grindrapp.android.chat.api.model.AddSavedPhraseResponse"
    private val phrasesResponse = "com.grindrapp.android.model.PhrasesResponse"
    private val phraseModel = "com.grindrapp.android.persistence.model.Phrase"

override fun init() {
    val chatRestServiceClass = findClass(chatRestService)
    val createSuccess = findClass(createSuccessResult)?.constructors?.firstOrNull()
    val phrasesRestServiceClass = findClass(phrasesRestService)

    findClass(retrofit)?.hook("create", HookStage.AFTER) { param ->
        val service = param.getResult() ?: return@hook
        param.setResult(
            when {
                chatRestServiceClass?.isAssignableFrom(service.javaClass) == true ->
                    createChatRestServiceProxy(service, createSuccess!!)
                phrasesRestServiceClass?.isAssignableFrom(service.javaClass) == true ->
                    createPhrasesRestServiceProxy(service, createSuccess!!)
                else -> service
            }
        )
    }
}

    fun createChatRestServiceProxy(
        originalService: Any,
        createSuccess: Constructor<*>
    ): Any {
        val invocationHandler = Proxy.getInvocationHandler(originalService)
        return Proxy.newProxyInstance(
            originalService.javaClass.classLoader,
            arrayOf(findClass(chatRestService))
        ) { proxy, method, args ->
            when (method.name) {
                "w" -> { // Annotated with @POST("v3/me/prefs/phrases")
                    val phrase = getObjectField(args[0], "phrase") as String
                    val currentPhrases = GrindrPlus.database.getPhraseList()
                    var index = GrindrPlus.database.getCurrentPhraseIndex() + 1
                    while (currentPhrases.any { it.get("phraseId") == index }) index++
                    GrindrPlus.database.addPhrase(index, phrase, 0, System.currentTimeMillis())
                    val response = findClass(addSavedPhraseResponse)?.constructors?.first()
                        ?.newInstance(index.toString())
                    createSuccess.newInstance(response)
                }
                "o" -> { // Annotated with @DELETE("v3/me/prefs/phrases/{id}")
                    val index = GrindrPlus.database.getCurrentPhraseIndex()
                    GrindrPlus.database.deletePhrase(index)
                    createSuccess.newInstance(Unit)
                }
                "C" -> { // Annotated with @POST("v4/phrases/frequency/{id}")
                    val index = GrindrPlus.database.getCurrentPhraseIndex()
                    val phrase = GrindrPlus.database.getPhrase(index)
                    GrindrPlus.database.updatePhrase(index,
                        phrase.getAsString("text"),
                        phrase.getAsInteger("frequency") + 1,
                        System.currentTimeMillis()
                    )
                    createSuccess.newInstance(Unit)
                }
                else -> invocationHandler.invoke(proxy, method, args)
            }
        }
    }

    fun createPhrasesRestServiceProxy(
        originalService: Any,
        createSuccess: Constructor<*>
    ): Any {
        val invocationHandler = Proxy.getInvocationHandler(originalService)
        return Proxy.newProxyInstance(
            originalService.javaClass.classLoader,
            arrayOf(findClass(phrasesRestService))
        ) { proxy, method, args ->
            when (method.name) {
                "a" -> { // Annotated with @GET("v3/me/prefs")
                    val currentPhrases = GrindrPlus.database.getPhraseList()
                    val phrases = currentPhrases.associateWith { phrase ->
                        val text = phrase.getAsString("text")
                        val timestamp = phrase.getAsLong("timestamp")
                        val frequency = phrase.getAsInteger("frequency")
                        GrindrPlus.loadClass(phraseModel)?.constructors?.first()?.newInstance(
                            phrase.getAsString("phraseId"), text, timestamp, frequency
                        )
                    }
                    val phrasesResponse = findClass(phrasesResponse)
                        ?.constructors?.find { it.parameterTypes.size == 1 }?.newInstance(phrases)
                    createSuccess.newInstance(phrasesResponse)
                }
                else -> invocationHandler.invoke(proxy, method, args)
            }
        }
    }
}
