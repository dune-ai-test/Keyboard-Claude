package com.example.customkeyboard.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.customkeyboard.R
import com.example.customkeyboard.data.Dictionary
import com.example.customkeyboard.data.KeyModel
import com.example.customkeyboard.data.KeyType
import com.example.customkeyboard.data.KeyboardLayouts
import com.example.customkeyboard.data.KeyboardSettings
import com.example.customkeyboard.data.SettingsRepository
import com.example.customkeyboard.data.db.ClipboardItem
import com.example.customkeyboard.data.db.ClipboardRepository
import com.example.customkeyboard.gesture.GestureTypingDecoder
import com.example.customkeyboard.gesture.KeyPoint
import com.example.customkeyboard.ui.KeyboardView
import com.example.customkeyboard.util.HapticUtil
import com.example.customkeyboard.util.SoundUtil
import com.example.customkeyboard.voice.VoiceInputHelper
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The heart of the keyboard app: an [InputMethodService] that renders the custom [KeyboardView],
 * wires up shift/caps/backspace/enter/space handling, number & symbol layout switching,
 * auto-correction & word prediction, swipe (gesture) typing, voice typing, and a live clipboard
 * strip — following an MVVM-style separation where this class is a thin "View controller" and
 * all state/logic lives in repositories ([SettingsRepository], [ClipboardRepository], [Dictionary]).
 *
 * Implements [LifecycleOwner] / [SavedStateRegistryOwner] manually since [InputMethodService] is
 * not a native AndroidX lifecycle owner, which is required to safely collect Kotlin Flows here.
 */
class CustomIME : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // --- Repositories / engines (shared singletons, no network I/O) ---
    private lateinit var dictionary: Dictionary
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var gestureDecoder: GestureTypingDecoder
    private lateinit var hapticUtil: HapticUtil
    private lateinit var soundUtil: SoundUtil
    private lateinit var voiceInputHelper: VoiceInputHelper

    private var currentSettings = KeyboardSettings()

    // --- Views ---
    private var rootView: View? = null
    private var keyboardView: KeyboardView? = null
    private var suggestion1: TextView? = null
    private var suggestion2: TextView? = null
    private var suggestion3: TextView? = null
    private var clipboardButton: ImageButton? = null
    private var clipboardStripScroll: HorizontalScrollView? = null
    private var clipboardStrip: LinearLayout? = null

    // --- Keyboard state ---
    private enum class Page { LETTERS, NUMBERS, SYMBOLS }
    private var currentPage = Page.LETTERS
    private var shiftActive = false
    private var capsLockActive = false
    private var lastShiftTapTime = 0L

    private var composingWord = StringBuilder()
    private var lastCommittedWord: String = ""
    private var isPasswordField = false
    private var isVoiceListening = false

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        dictionary = Dictionary.getInstance(this)
        settingsRepository = SettingsRepository.getInstance(this)
        clipboardRepository = ClipboardRepository.getInstance(this)
        gestureDecoder = GestureTypingDecoder(dictionary)
        hapticUtil = HapticUtil(this)
        soundUtil = SoundUtil(this).apply { init() }
        voiceInputHelper = VoiceInputHelper(this)

        settingsRepository.settingsFlow.onEach { settings ->
            currentSettings = settings
            keyboardView?.swipeTypingEnabled = settings.swipeTypingEnabled
            applyKeyboardHeight(settings.keyboardHeightPercent)
        }.launchIn(lifecycleScope)

        // Monitor system clipboard changes and persist them (with a max history size, purely local).
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val clip: ClipData? = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank() && !isPasswordField) {
                lifecycleScope.launch { clipboardRepository.addOrUpdate(text) }
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_keyboard_container, null)
        rootView = view

        keyboardView = view.findViewById(R.id.keyboardView)
        suggestion1 = view.findViewById(R.id.suggestion1)
        suggestion2 = view.findViewById(R.id.suggestion2)
        suggestion3 = view.findViewById(R.id.suggestion3)
        clipboardButton = view.findViewById(R.id.clipboardButton)
        clipboardStripScroll = view.findViewById(R.id.clipboardStripScroll)
        clipboardStrip = view.findViewById(R.id.clipboardStrip)

        keyboardView?.applyThemeColors()
        keyboardView?.swipeTypingEnabled = currentSettings.swipeTypingEnabled
        keyboardView?.listener = object : KeyboardView.Listener {
            override fun onKeyTap(key: KeyModel) = handleKeyTap(key)
            override fun onKeyLongPressChar(char: String) = handleCommitText(applyShiftCase(char), true)
            override fun onShiftToggled(caps: Boolean, capsLock: Boolean) {}
            override fun onGestureTypingResult(path: List<KeyPoint>, keyLayout: Map<Char, KeyPoint>) =
                handleGestureResult(path, keyLayout)

            override fun onKeyDownFeedback() {
                if (currentSettings.vibrationEnabled) hapticUtil.keyPress(currentSettings.vibrationStrength)
                if (currentSettings.soundEnabled) soundUtil.playClick(currentSettings.soundVolume)
            }
        }

        clipboardButton?.setOnClickListener { toggleClipboardStrip() }
        setActiveLayout(Page.LETTERS)
        observeClipboardHistory()

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val inputType = info?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS

        isPasswordField = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            ) || cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        // Privacy: never learn words or suggest predictions inside password fields.
        clearSuggestions()
        composingWord.clear()
        shiftActive = shouldAutoCapitalize(info)
        capsLockActive = false
        updateShiftVisual()
        setActiveLayout(Page.LETTERS)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voiceInputHelper.stopListening()
        isVoiceListening = false
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        voiceInputHelper.stopListening()
        soundUtil.release()
    }

    // ---------------------------------------------------------------------------------------
    // Key handling
    // ---------------------------------------------------------------------------------------

    private fun handleKeyTap(key: KeyModel) {
        when (key.type) {
            KeyType.CHARACTER -> handleCommitText(applyShiftCase(key.label), true)
            KeyType.COMMA -> handleCommitText(",", false)
            KeyType.PERIOD -> handleCommitText(".", false)
            KeyType.SPACE -> handleSpace()
            KeyType.BACKSPACE -> handleBackspace()
            KeyType.ENTER -> handleEnter()
            KeyType.SHIFT -> handleShiftTap()
            KeyType.CAPS_LOCK -> { capsLockActive = !capsLockActive; updateShiftVisual() }
            KeyType.NUMBERS -> setActiveLayout(Page.NUMBERS)
            KeyType.SYMBOLS -> setActiveLayout(Page.SYMBOLS)
            KeyType.LETTERS -> setActiveLayout(Page.LETTERS)
            KeyType.EMOJI -> handleCommitText("😀", false) // simplified: full emoji picker could extend this
            KeyType.VOICE -> toggleVoiceTyping()
        }
    }

    /** Commits a single character while keeping track of the in-progress word for prediction. */
    private fun handleCommitText(text: String, isWordChar: Boolean) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)

        if (isWordChar && text.length == 1 && text[0].isLetterOrDigit()) {
            composingWord.append(text)
        } else {
            finalizeComposingWord(commitAutoCorrect = false)
        }

        if (!capsLockActive && shiftActive) {
            shiftActive = false
            updateShiftVisual()
        }
        updateSuggestions()
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return

        // Double-space-to-period convenience feature.
        if (currentSettings.doubleSpacePeriod) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before == "  " || (before.length == 2 && before[1] == ' ' && before[0] != ' ' && composingWord.isEmpty())) {
                // handled below via lastTwoChars check instead for correctness
            }
        }

        val beforeCursor = ic.getTextBeforeCursor(1, 0)?.toString()
        val wordToFinalize = composingWord.toString()

        finalizeComposingWord(commitAutoCorrect = currentSettings.autoCorrectEnabled)

        if (currentSettings.doubleSpacePeriod && beforeCursor == " " && wordToFinalize.isEmpty()) {
            // Replace the previously typed trailing space with ". "
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            shiftActive = true
            updateShiftVisual()
        } else {
            ic.commitText(" ", 1)
        }
        updateSuggestions()
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
        }
        ic.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        finalizeComposingWord(commitAutoCorrect = currentSettings.autoCorrectEnabled)
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val handled = when (action) {
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> {
                ic.performEditorAction(action)
                true
            }
            else -> false
        }
        if (!handled) ic.commitText("\n", 1)
    }

    private fun handleShiftTap() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTapTime < 350) {
            // Double-tap shift -> caps lock
            capsLockActive = true
            shiftActive = true
        } else {
            if (capsLockActive) {
                capsLockActive = false
                shiftActive = false
            } else {
                shiftActive = !shiftActive
            }
        }
        lastShiftTapTime = now
        updateShiftVisual()
    }

    private fun applyShiftCase(label: String): String =
        if ((shiftActive || capsLockActive) && label.length == 1 && label[0].isLetter()) label.uppercase() else label

    private fun updateShiftVisual() {
        keyboardView?.shiftActive = shiftActive
        keyboardView?.capsLockActive = capsLockActive
    }

    private fun shouldAutoCapitalize(info: EditorInfo?): Boolean {
        if (!currentSettings.autoCapitalize) return false
        val ic = currentInputConnection
        val textBefore = ic?.getTextBeforeCursor(3, 0)?.toString().orEmpty()
        return textBefore.isEmpty() || textBefore.trimEnd().endsWith(".") ||
            textBefore.trimEnd().endsWith("!") || textBefore.trimEnd().endsWith("?")
    }

    /** Scales the overall keyboard height (rows area) per the user's height preference. */
    private fun applyKeyboardHeight(percent: Float) {
        val kv = keyboardView ?: return
        val baseHeightPx = (resources.displayMetrics.density * BASE_KEYBOARD_HEIGHT_DP).toInt()
        val targetHeight = (baseHeightPx * percent).toInt()
        val params = kv.layoutParams
        if (params != null) {
            params.height = targetHeight
            kv.layoutParams = params
        }
    }

    // ---------------------------------------------------------------------------------------
    // Layout switching
    // ---------------------------------------------------------------------------------------

    private fun setActiveLayout(page: Page) {
        currentPage = page
        keyboardView?.layoutModel = when (page) {
            Page.LETTERS -> KeyboardLayouts.letters()
            Page.NUMBERS -> KeyboardLayouts.numbers()
            Page.SYMBOLS -> KeyboardLayouts.symbols()
        }
        updateShiftVisual()
    }

    // ---------------------------------------------------------------------------------------
    // Prediction / auto-correction
    // ---------------------------------------------------------------------------------------

    private fun finalizeComposingWord(commitAutoCorrect: Boolean) {
        if (composingWord.isEmpty() || isPasswordField) {
            composingWord.clear()
            return
        }
        val word = composingWord.toString()
        composingWord.clear()

        if (commitAutoCorrect && currentSettings.autoCorrectEnabled) {
            val correction = dictionary.getAutoCorrection(word)
            if (correction != null && correction != word.lowercase()) {
                replaceLastTypedWord(word, matchCase(word, correction))
                learnWord(correction, word)
                return
            }
        }
        learnWord(word, word)
    }

    private fun learnWord(finalWord: String, originalTyped: String) {
        if (isPasswordField) return
        dictionary.learn(finalWord)
        if (lastCommittedWord.isNotEmpty()) dictionary.learnBigram(lastCommittedWord, finalWord)
        lastCommittedWord = finalWord.lowercase()
    }

    /** Replaces the just-typed word (still immediately before the cursor) with a corrected version. */
    private fun replaceLastTypedWord(original: String, replacement: String) {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(original.length, 0)
        ic.commitText(replacement, 1)
    }

    private fun matchCase(original: String, correction: String): String = when {
        original.isNotEmpty() && original[0].isUpperCase() ->
            correction.replaceFirstChar { it.uppercase() }
        else -> correction
    }

    private fun updateSuggestions() {
        if (isPasswordField || !currentSettings.predictionEnabled) {
            clearSuggestions()
            return
        }
        val suggestions = if (composingWord.isNotEmpty()) {
            dictionary.getSuggestions(composingWord.toString(), 3)
        } else {
            dictionary.predictNextWord(lastCommittedWord, 3)
        }
        val views = listOf(suggestion1, suggestion2, suggestion3)
        for (i in views.indices) {
            val s = suggestions.getOrNull(i)
            views[i]?.text = s ?: ""
            views[i]?.setOnClickListener { s?.let { acceptSuggestion(it) } }
            views[i]?.isVisible = s != null
        }
    }

    private fun acceptSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            ic.deleteSurroundingText(composingWord.length, 0)
        }
        val cased = if (shiftActive || capsLockActive) word.replaceFirstChar { it.uppercase() } else word
        ic.commitText("$cased ", 1)
        learnWord(word, word)
        composingWord.clear()
        if (!capsLockActive) { shiftActive = false; updateShiftVisual() }
        updateSuggestions()
    }

    private fun clearSuggestions() {
        listOf(suggestion1, suggestion2, suggestion3).forEach {
            it?.text = ""
            it?.isVisible = false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Swipe / gesture typing
    // ---------------------------------------------------------------------------------------

    private fun handleGestureResult(path: List<KeyPoint>, keyLayout: Map<Char, KeyPoint>) {
        if (!currentSettings.swipeTypingEnabled || isPasswordField) return
        val results = gestureDecoder.decode(path, keyLayout, maxResults = 3)
        if (results.isEmpty()) return

        val ic = currentInputConnection ?: return
        val best = results.first()
        val cased = if (shiftActive || capsLockActive) best.replaceFirstChar { it.uppercase() } else best
        ic.commitText("$cased ", 1)
        learnWord(best, best)

        // Populate suggestion strip with the alternates so the user can tap-correct.
        val views = listOf(suggestion1, suggestion2, suggestion3)
        for (i in views.indices) {
            val s = results.getOrNull(i)
            views[i]?.text = s ?: ""
            views[i]?.isVisible = s != null
            views[i]?.setOnClickListener {
                s?.let {
                    ic.deleteSurroundingText(best.length + 1, 0)
                    ic.commitText("$it ", 1)
                    learnWord(it, it)
                }
            }
        }
        if (!capsLockActive) { shiftActive = false; updateShiftVisual() }
    }

    // ---------------------------------------------------------------------------------------
    // Voice typing
    // ---------------------------------------------------------------------------------------

    private fun toggleVoiceTyping() {
        if (!currentSettings.voiceTypingEnabled) return
        if (isVoiceListening) {
            voiceInputHelper.stopListening()
            isVoiceListening = false
            return
        }
        isVoiceListening = true
        voiceInputHelper.startListening(
            onPartialResult = { partial ->
                // Show partial transcription live in the first suggestion slot as feedback.
                suggestion1?.text = partial
                suggestion1?.isVisible = true
            },
            onFinalResult = { finalText ->
                currentInputConnection?.commitText("$finalText ", 1)
                isVoiceListening = false
                clearSuggestions()
            },
            onError = { _ ->
                isVoiceListening = false
                clearSuggestions()
            },
            onReadyForSpeech = {}
        )
    }

    // ---------------------------------------------------------------------------------------
    // Clipboard manager strip
    // ---------------------------------------------------------------------------------------

    private fun observeClipboardHistory() {
        clipboardRepository.history.onEach { items -> renderClipboardStrip(items) }.launchIn(lifecycleScope)
    }

    private fun toggleClipboardStrip() {
        val scroll = clipboardStripScroll ?: return
        scroll.isVisible = !scroll.isVisible
    }

    private fun renderClipboardStrip(items: List<ClipboardItem>) {
        val strip = clipboardStrip ?: return
        strip.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (item in items.take(20)) {
            val chip = inflater.inflate(R.layout.item_clipboard_chip, strip, false) as TextView
            chip.text = if (item.isPinned) "📌 ${item.previewLabel}" else item.previewLabel
            chip.setOnClickListener {
                currentInputConnection?.commitText(item.text, 1)
            }
            chip.setOnLongClickListener {
                lifecycleScope.launch { clipboardRepository.togglePin(item) }
                true
            }
            strip.addView(chip)
        }
    }

    // ---------------------------------------------------------------------------------------
    // InputConnection helper overrides
    // ---------------------------------------------------------------------------------------

    companion object {
        private const val BASE_KEYBOARD_HEIGHT_DP = 220
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (newSelStart != newSelEnd) {
            // User selected a text range externally; don't keep stale composing state.
            composingWord.clear()
        }
    }
}
