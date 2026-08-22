package com.example.autotexttapper.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServiceState { DISABLED, READY, RUNNING, ERROR }

enum class LogTag { TICK, ACTION, WARN, FAIL, OK }

enum class Target { NONE, FANTIK, TIKTOK }

data class LogEntry(
    val timestamp: Long,
    val tag: LogTag,
    val message: String
)

/**
 * Process-wide bridge between the accessibility service (writer) and the Compose UI (reader).
 * The service only ever calls the methods below additively at existing decision points; it does
 * not otherwise change behaviour based on anything in here.
 */
object AutomationState {

    private val _state = MutableStateFlow(ServiceState.READY)
    val state = _state.asStateFlow()

    private val _phase = MutableStateFlow("idle")
    val phase = _phase.asStateFlow()

    private val _likeCount = MutableStateFlow(0)
    val likeCount = _likeCount.asStateFlow()

    private val _skipCount = MutableStateFlow(0)
    val skipCount = _skipCount.asStateFlow()

    private val _sessionStart = MutableStateFlow<Long?>(null)
    val sessionStart = _sessionStart.asStateFlow()

    /** 0..3 — which cycle-dial arc (SCAN/DISPATCH/COLLECT/RETURN) is currently active. */
    private val _segment = MutableStateFlow(0)
    val segment = _segment.asStateFlow()

    /** Highest segment reached so far this cycle, so completed arcs stay lit at reduced alpha. */
    private val _cycleDone = MutableStateFlow(-1)
    val cycleDone = _cycleDone.asStateFlow()

    private val _foreground = MutableStateFlow(Target.NONE)
    val foreground = _foreground.asStateFlow()

    /** Bumped (to SystemClock.uptimeMillis()) every time a heart is confirmed — fires the collect moment. */
    private val _collectTick = MutableStateFlow(0L)
    val collectTick = _collectTick.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    fun setPhase(text: String) {
        _phase.value = text
    }

    fun setSegment(index: Int) {
        _segment.value = index
        if (index > _cycleDone.value) _cycleDone.value = index
    }

    fun resetCycle() {
        _segment.value = 0
        _cycleDone.value = -1
    }

    fun setForeground(target: Target) {
        _foreground.value = target
    }

    fun log(tag: LogTag, message: String) {
        _logs.value = (_logs.value + LogEntry(System.currentTimeMillis(), tag, message)).takeLast(120)
    }

    fun incrementSkips() {
        _skipCount.value += 1
    }

    /** Call at exactly one place: where the heart confirmation check succeeds. */
    fun collected() {
        _likeCount.value += 1
        _collectTick.value = android.os.SystemClock.uptimeMillis()
    }

    fun startSession() {
        _likeCount.value = 0
        _skipCount.value = 0
        _sessionStart.value = System.currentTimeMillis()
        _state.value = ServiceState.RUNNING
        _phase.value = "idle"
        resetCycle()
    }

    fun stopSession() {
        _sessionStart.value = null
        _state.value = ServiceState.READY
        _phase.value = "idle"
        _foreground.value = Target.NONE
        resetCycle()
    }
}
