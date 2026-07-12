package com.example.customkeyboard.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.customkeyboard.R
import com.example.customkeyboard.data.Dictionary
import com.example.customkeyboard.data.KeyModel
import com.example.customkeyboard.data.KeyRow
import com.example.customkeyboard.data.KeyType
import com.example.customkeyboard.data.KeyboardLayout
import com.example.customkeyboard.data.KeyboardLayouts
import com.example.customkeyboard.data.KeyboardSettings
import com.example.customkeyboard.data.SettingsRepository
import com.example.customkeyboard.data.db.ClipboardItem
import com.example.customkeyboard.data.db.ClipboardRepository
import com.example.customkeyboard.gesture.GestureTypingDecoder
import com.example.customkeyboard.gesture.KeyPoint
import com.example.customkeyboard.ui.ClipboardAdapter
import com.example.customkeyboard.ui.KeyboardView
import com.example.customkeyboard.ui.SettingsActivity
import com.example.customkeyboard.util.HapticUtil
import com.example.customkeyboard.util.SoundUtil
import com.example.customkeyboard.voice.VoiceInputHelper
import kotlin.math.abs
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The heart of the keyboard app: an [InputMethodService] that renders the custom [KeyboardView],
 * wires up shift/caps/backspace/enter/space handling, number & symbol layout switching,
 * auto-correction & word prediction, swipe (gesture) typing, voice typing, an emoji page, a
 * one-level undo, cursor-drag, and a full in-keyboard clipboard manager panel (pin / delete /
 * drag-to-reorder) — following an MVVM-style separation where this class is a thin "View
 * controller" and all state/logic lives in repositories ([SettingsRepository],
 * [ClipboardRepository], [Dictionary]).
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
    private var contentArea: FrameLayout? = null
    private var keyboardView: KeyboardView? = null
    private var suggestion1: TextView? = null
    private var suggestion2: TextView? = null
    private var suggestion3: TextView? = null
    private var suggestionBar: View? = null

    // Toolbar
    private var btnSwitchKeyboard: ImageButton? = null
    private var btnSettings: ImageButton? = null
    private var btnEmoji: ImageButton? = null
    private var btnUndo: ImageButton? = null
    private var btnClipboard: ImageButton? = null
    private var btnCursorMove: ImageButton? = null
    private var btnVoice: ImageButton? = null

    // Clipboard panel
    private var clipboardPanel: View? = null
    private var clipboardRecyclerView: RecyclerView? = null
    private var clipboardEmptyText: TextView? = null
    private var clipboardAdapter: ClipboardAdapter? = null
    private var isClipboardPanelOpen = false

    // --- Keyboard state ---
    private enum class Page { LETTERS, NUMBERS, SYMBOLS, EMOJI }
    private var currentPage = Page.LETTERS
    private var pageBeforeEmoji = Page.LETTERS
    private var shiftActive = false
    private var capsLockActive = false
    private var lastShiftTapTime = 0L

    private var composingWord = StringBuilder()
    private var lastCommittedWord: String = ""
    private var isPasswordField = false
    private var isVoiceListening = false

    // One-level undo: remembers the most recent text this IME inserted so it can be reverted.
    private var lastInsertion: String = ""

    // Cursor-drag state (dragging the cursor-move toolbar button left/right).
    private var cursorDragStartX = 0f

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

        contentArea = view.findViewById(R.id.contentArea)
        keyboardView = view.findViewById(R.id.keyboardView)
        suggestion1 = view.findViewById(R.id.suggestion1)
        suggestion2 = view.findViewById(R.id.suggestion2)
        suggestion3 = view.findViewById(R.id.suggestion3)
        suggestionBar = view.findViewById(R.id.suggestionBar)

        btnSwitchKeyboard = view.findViewById(R.id.btnSwitchKeyboard)
        btnSettings = view.findViewById(R.id.btnSettings)
        btnEmoji = view.findViewById(R.id.btnEmoji)
        btnUndo = view.findViewById(R.id.btnUndo)
        btnClipboard = view.findViewById(R.id.btnClipboard)
        btnCursorMove = view.findViewById(R.id.btnCursorMove)
        btnVoice = view.findViewById(R.id.btnVoice)

        clipboardPanel = view.findViewById(R.id.clipboardPanel)
        clipboardRecyclerView = view.findViewById(R.id.clipboardRecyclerView)
        clipboardEmptyText = view.findViewById(R.id.clipboardEmptyText)

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

        setupToolbar()
        setupClipboardPanel()
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

        closeClipboardPanel()
        // Privacy: never learn words or suggest predictions inside password fields.
        clearSuggestions()
        composingWord.clear()
        lastInsertion = ""
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

    /** Scales the overall keyboard height (rows area) per the user's height preference. */
    private fun applyKeyboardHeight(percent: Float) {
        val content = contentArea ?: return
        val baseHeightPx = (resources.displayMetrics.density * BASE_KEYBOARD_HEIGHT_DP).toInt()
        val targetHeight = (baseHeightPx * percent).toInt()
        val params = content.layoutParams
        if (params != null) {
            params.height = targetHeight
            content.layoutParams = params
        }
    }

    // ---------------------------------------------------------------------------------------
    // Toolbar
    // ---------------------------------------------------------------------------------------

    private fun setupToolbar() {
        btnSwitchKeyboard?.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        btnSettings?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
        btnEmoji?.setOnClickListener { toggleEmojiPage() }
        btnUndo?.setOnClickListener { performUndo() }
        btnClipboard?.setOnClickListener { toggleClipboardPanel() }
        btnVoice?.setOnClickListener { toggleVoiceTyping() }

        btnCursorMove?.setOnTouchListener { _, event ->
            handleCursorDrag(event)
            true
        }
    }

    private fun handleCursorDrag(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cursorDragStartX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - cursorDragStartX
                val steps = (delta / CURSOR_DRAG_STEP_PX).toInt()
                if (steps != 0) {
                    repeat(abs(steps)) {
                        sendDownUpKeyEvents(if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                    }
                    cursorDragStartX = event.x
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Key handling
    // ---------------------------------------------------------------------------------------

    private fun handleKeyTap(key: KeyModel) {
        when (key.type) {
            KeyType.CHARACTER -> {
                if (currentPage == Page.EMOJI) handleEmojiCommit(key.label)
                else handleCommitText(applyShiftCase(key.label), true)
            }
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
            KeyType.EMOJI -> toggleEmojiPage()
            KeyType.VOICE -> toggleVoiceTyping()
        }
    }

    /** Commits a plain-text emoji, fully bypassing word-composition, auto-correct, and the
     *  dictionary — emoji are never part of a "word" and should never be case-shifted or
     *  fed through the composing-word buffer. */
    private fun handleEmojiCommit(emojiText: String) {
        val ic = currentInputConnection ?: return
        finalizeComposingWord(commitAutoCorrect = false)
        ic.commitText(emojiText, 1)
        lastInsertion = emojiText
    }

    /** Commits a single character while keeping track of the in-progress word for prediction. */
    private fun handleCommitText(text: String, isWordChar: Boolean) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        lastInsertion = text

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
        val beforeCursor = ic.getTextBeforeCursor(1, 0)?.toString()
        val wordToFinalize = composingWord.toString()

        finalizeComposingWord(commitAutoCorrect = currentSettings.autoCorrectEnabled)

        if (currentSettings.doubleSpacePeriod && beforeCursor == " " && wordToFinalize.isEmpty()) {
            // Replace the previously typed trailing space with ". "
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastInsertion = ". "
            shiftActive = true
            updateShiftVisual()
        } else {
            ic.commitText(" ", 1)
            lastInsertion = " "
        }
        updateSuggestions()
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
        }
        // Delete a full character, not just one UTF-16 unit — otherwise deleting an emoji
        // (which is a surrogate pair) leaves a dangling, unrenderable half-character behind.
        val beforeCursor = ic.getTextBeforeCursor(2, 0)
        val deleteLength = if (beforeCursor != null && beforeCursor.length == 2 &&
            Character.isSurrogatePair(beforeCursor[0], beforeCursor[1])
        ) 2 else 1
        ic.deleteSurroundingText(deleteLength, 0)
        lastInsertion = ""
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
        if (!handled) {
            ic.commitText("\n", 1)
            lastInsertion = "\n"
        }
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

    // ---------------------------------------------------------------------------------------
    // Undo
    // ---------------------------------------------------------------------------------------

    private fun performUndo() {
        val ic = currentInputConnection ?: return
        if (lastInsertion.isEmpty()) return
        ic.deleteSurroundingText(lastInsertion.length, 0)
        lastInsertion = ""
        composingWord.clear()
        updateSuggestions()
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
            Page.EMOJI -> KeyboardLayouts.letters() // placeholder grid swapped below
        }
        updateShiftVisual()
    }

    private fun toggleEmojiPage() {
        if (currentPage == Page.EMOJI) {
            setActiveLayout(pageBeforeEmoji)
        } else {
            pageBeforeEmoji = currentPage
            currentPage = Page.EMOJI
            keyboardView?.layoutModel = EMOJI_LAYOUT
        }
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
        lastInsertion = replacement
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
        // Collapse the whole strip (not just the individual chips) when there's nothing to
        // show, so no empty gap is left between the toolbar and the keyboard rows.
        suggestionBar?.isVisible = suggestions.isNotEmpty()
    }

    private fun acceptSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            ic.deleteSurroundingText(composingWord.length, 0)
        }
        val cased = if (shiftActive || capsLockActive) word.replaceFirstChar { it.uppercase() } else word
        ic.commitText("$cased ", 1)
        lastInsertion = "$cased "
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
        suggestionBar?.isVisible = false
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
        lastInsertion = "$cased "
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
                    lastInsertion = "$it "
                    learnWord(it, it)
                }
            }
        }
        suggestionBar?.isVisible = results.isNotEmpty()
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
                suggestionBar?.isVisible = true
            },
            onFinalResult = { finalText ->
                currentInputConnection?.commitText("$finalText ", 1)
                lastInsertion = "$finalText "
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
    // Clipboard manager panel (full in-keyboard view, not just a strip)
    // ---------------------------------------------------------------------------------------

    private fun setupClipboardPanel() {
        val panel = clipboardPanel ?: return
        val recycler = clipboardRecyclerView ?: return

        clipboardAdapter = ClipboardAdapter(
            onTap = { item ->
                currentInputConnection?.commitText(item.text, 1)
                lastInsertion = item.text
                closeClipboardPanel()
            },
            onTogglePin = { item -> lifecycleScope.launch { clipboardRepository.togglePin(item) } },
            onDelete = { item -> lifecycleScope.launch { clipboardRepository.delete(item) } },
            onReordered = { ordered -> lifecycleScope.launch { clipboardRepository.reorder(ordered) } }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = clipboardAdapter
        clipboardAdapter?.attachDragSupport(recycler)

        panel.findViewById<ImageButton>(R.id.clipboardBackButton)?.setOnClickListener { closeClipboardPanel() }
        panel.findViewById<TextView>(R.id.clipboardClearButton)?.setOnClickListener {
            lifecycleScope.launch { clipboardRepository.clearUnpinned() }
        }
    }

    private fun observeClipboardHistory() {
        clipboardRepository.history.onEach { items ->
            clipboardAdapter?.submitList(items)
            clipboardEmptyText?.isVisible = items.isEmpty()
        }.launchIn(lifecycleScope)
    }

    private fun toggleClipboardPanel() {
        if (isClipboardPanelOpen) closeClipboardPanel() else openClipboardPanel()
    }

    private fun openClipboardPanel() {
        isClipboardPanelOpen = true
        clipboardPanel?.isVisible = true
        keyboardView?.isVisible = false
    }

    private fun closeClipboardPanel() {
        isClipboardPanelOpen = false
        clipboardPanel?.isVisible = false
        keyboardView?.isVisible = true
    }

    // ---------------------------------------------------------------------------------------
    // InputConnection helper overrides
    // ---------------------------------------------------------------------------------------

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

    companion object {
        private const val BASE_KEYBOARD_HEIGHT_DP = 220
        private const val CURSOR_DRAG_STEP_PX = 22f

        /** Builds an emoji string from its raw Unicode code point — avoids embedding literal
         *  multi-byte / variation-selector sequences in source, which some text fields reject. */
        private fun emoji(codePoint: Int): String = String(Character.toChars(codePoint))

        /** A compact, commonly-used emoji grid shown when the emoji toolbar button is tapped.
         *  Deliberately uses plain base code points (no ZWJ / variation-selector combinations)
         *  so every target field accepts them as ordinary text. */
        private val EMOJI_LAYOUT: KeyboardLayout = run {
            val codePoints = listOf(
                0x1F600, 0x1F602, 0x1F60D, 0x1F970, 0x1F60A, 0x1F609, 0x1F60E, 0x1F914, 0x1F634, 0x1F62D,
                0x1F44D, 0x1F44E, 0x1F64F, 0x1F44F, 0x1F64C, 0x1F4AA, 0x1F91D, 0x2764, 0x1F525, 0x1F389,
                0x2705, 0x2B50, 0x2600, 0x1F319, 0x1F355, 0x2615, 0x1F382, 0x26BD, 0x1F697, 0x2708
            )
            val emojis = codePoints.map { emoji(it) }
            val rows = emojis.chunked(10)
                .map { rowEmojis -> KeyRow(rowEmojis.map { KeyModel(it, KeyType.CHARACTER) }) }
                .toMutableList()
            rows.add(
                KeyRow(
                    listOf(
                        KeyModel("ABC", KeyType.LETTERS, widthWeight = 2f),
                        KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 2f)
                    )
                )
            )
            KeyboardLayout(rows)
        }
    }
}
