package com.liquidboard.ime

import android.inputmethodservice.InputMethodService
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.liquidboard.ime.ai.GeminiImeRepository
import com.liquidboard.ime.ai.SentimentSnapshot
import com.liquidboard.ime.gesture.GestureDecoder
import com.liquidboard.ime.theme.ThemeConfig
import com.liquidboard.ime.ui.LiquidGlassKeyboardView
import kotlinx.coroutines.*

class LiquidBoardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var aiRepo: GeminiImeRepository
    private val gestureDecoder = GestureDecoder()

    private var themeConfig by mutableStateOf(ThemeConfig())
    private var suggestions by mutableStateOf(listOf("I'm", "Sounds", "Thanks"))
    private var sentiment by mutableStateOf(SentimentSnapshot("Neutral", "Ready to compose"))
    private var liveTranslation by mutableStateOf("")
    private var translateEnabled by mutableStateOf(false)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        aiRepo = GeminiImeRepository(BuildConfig.AI_PROXY_BASE_URL)
        themeConfig = ThemeConfig.fromWallpaper(this)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LiquidBoardService)
            setViewTreeSavedStateRegistryOwner(this@LiquidBoardService)
            setContent {
                LiquidGlassKeyboardView(
                    config = themeConfig,
                    suggestions = suggestions,
                    sentiment = sentiment,
                    translateEnabled = translateEnabled,
                    liveTranslation = liveTranslation,
                    onKeyTap = { key -> commitChar(key, this) },
                    onBackspace = { deleteChar(this) },
                    onDeleteWord = { deletePreviousWord(this) },
                    onCursorScrub = { delta -> moveCursorBy(delta) },
                    onGestureSwipe = { normalizedPoints ->
                        val candidates = gestureDecoder.decode(normalizedPoints)
                        if (candidates.isNotEmpty()) {
                            commitWord(candidates.first().word)
                            refreshAiContext(candidates.map { it.word })
                        }
                    },
                    onSuggestionTap = { word ->
                        replaceCurrentWord(word)
                        refreshAiContext()
                    },
                    onToggleTranslate = {
                        translateEnabled = !translateEnabled
                        refreshAiContext()
                    }
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        themeConfig = ThemeConfig.fromWallpaper(this, themeConfig)
        refreshAiContext()
    }

    private fun commitChar(text: String, view: View) {
        if (themeConfig.hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        currentInputConnection?.commitText(text, 1)
        refreshAiContext()
    }

    private fun commitWord(word: String) {
        currentInputConnection?.commitText("$word ", 1)
    }

    private fun replaceCurrentWord(word: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val trailingWord = before.takeLastWhile { it.isLetterOrDigit() || it == '\'' }
        if (trailingWord.isNotEmpty()) {
            ic.deleteSurroundingText(trailingWord.length, 0)
        }
        ic.commitText("$word ", 1)
    }

    private fun deleteChar(view: View) {
        if (themeConfig.hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        refreshAiContext()
    }

    private fun deletePreviousWord(view: View) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val trimmed = before.trimEnd()
        val wordLen = trimmed.takeLastWhile { !it.isWhitespace() }.length
        val totalDelete = (before.length - trimmed.length) + wordLen
        if (totalDelete > 0) {
            ic.deleteSurroundingText(totalDelete, 0)
            if (themeConfig.hapticsEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            }
        }
        refreshAiContext()
    }

    private fun moveCursorBy(delta: Int) {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val nextPos = (extracted.selectionStart + delta).coerceIn(0, extracted.text.length)
        ic.setSelection(nextPos, nextPos)
    }

    private var aiJob: Job? = null
    private fun refreshAiContext(gestureHints: List<String> = emptyList()) {
        val fullText = currentInputConnection
            ?.getExtractedText(ExtractedTextRequest(), 0)
            ?.text?.toString().orEmpty()
        aiJob?.cancel()
        aiJob = serviceScope.launch {
            delay(180)
            val result = aiRepo.predict(
                text = fullText,
                gestureCandidates = gestureHints,
                targetLanguage = themeConfig.defaultTranslateTarget,
                translateEnabled = translateEnabled
            )
            if (result != null) {
                suggestions = result.suggestions
                sentiment = result.sentiment
                liveTranslation = result.translatedText
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }
}
