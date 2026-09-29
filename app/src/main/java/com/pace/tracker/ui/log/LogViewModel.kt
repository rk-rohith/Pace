package com.pace.tracker.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.tracker.AppContainer
import com.pace.tracker.data.db.DailyLogEntity
import com.pace.tracker.data.db.MealEntity
import com.pace.tracker.data.db.ProfileEntity
import com.pace.tracker.data.db.WorkoutEntity
import com.pace.tracker.data.targetForDay
import com.pace.tracker.data.today
import com.pace.tracker.ui.components.toDecimalOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Text-field state for the auto-saving parts of the daily log. */
data class LogForm(
    val weight: String = "",
    val bodyFat: String = "",
    val stepsManual: String = "",
    val mood: Float = 3f,
    val moodSet: Boolean = false,
    val energy: Float = 3f,
    val energySet: Boolean = false,
    val sleep: Float = 7f,
    val sleepSet: Boolean = false,
    val notes: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(private val container: AppContainer, initialDay: Long) : ViewModel() {
    private val repository = container.repository

    private val _day = MutableStateFlow(if (initialDay > 0) initialDay else today())
    val day: StateFlow<Long> = _day

    val log: StateFlow<DailyLogEntity?> = _day.flatMapLatest { repository.observeLog(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val meals: StateFlow<List<MealEntity>> = _day.flatMapLatest { repository.observeMeals(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workouts: StateFlow<List<WorkoutEntity>> = _day.flatMapLatest { repository.observeWorkouts(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val profile: StateFlow<ProfileEntity?> = repository.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val targetKcal: StateFlow<Int> = combine(_day, repository.programData) { d, data -> data.targetForDay(d) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    var form by mutableStateOf(LogForm())
        private set
    var formLoaded by mutableStateOf(false)
        private set
    var savedAt by mutableStateOf<Long?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    private var saveJob: Job? = null
    private var dirty = false

    init {
        loadForm()
    }

    private fun loadForm() {
        formLoaded = false
        val d = _day.value
        viewModelScope.launch {
            val l = repository.getLog(d)
            form = LogForm(
                weight = l?.weightKg?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "",
                bodyFat = l?.bodyFatPct?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "",
                stepsManual = l?.stepsManual?.toString() ?: "",
                mood = l?.mood?.toFloat() ?: 3f, moodSet = l?.mood != null,
                energy = l?.energy?.toFloat() ?: 3f, energySet = l?.energy != null,
                sleep = l?.sleepHours?.toFloat() ?: 7f, sleepSet = l?.sleepHours != null,
                notes = l?.notes ?: "",
            )
            formLoaded = true
        }
    }

    fun setDay(newDay: Long) {
        if (newDay == _day.value || newDay > today()) return
        flushNow()
        _day.value = newDay
        loadForm()
    }

    fun update(transform: (LogForm) -> LogForm) {
        form = transform(form)
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            persist(_day.value, form)
        }
    }

    private suspend fun persist(day: Long, f: LogForm) {
        repository.updateLog(day) {
            it.copy(
                weightKg = f.weight.toDecimalOrNull()?.takeIf { w -> w in 25.0..400.0 }?.let { w -> Math.round(w * 10) / 10.0 },
                bodyFatPct = f.bodyFat.toDecimalOrNull()?.takeIf { b -> b in 2.0..70.0 },
                stepsManual = f.stepsManual.toIntOrNull(),
                mood = if (f.moodSet) f.mood.toInt() else null,
                energy = if (f.energySet) f.energy.toInt() else null,
                sleepHours = if (f.sleepSet) f.sleep.toDouble() else null,
                notes = f.notes,
            )
        }
        dirty = false
        savedAt = System.currentTimeMillis()
    }

    /** Writes any pending edit immediately (on day switch / screen exit) using the app scope. */
    private fun flushNow() {
        if (!dirty) return
        saveJob?.cancel()
        val d = _day.value
        val f = form
        dirty = false
        container.appScope.launch { persist(d, f) }
    }

    override fun onCleared() {
        flushNow()
        super.onCleared()
    }

    fun changeWater(delta: Int) = viewModelScope.launch {
        repository.updateLog(_day.value) { it.copy(waterGlasses = (it.waterGlasses + delta).coerceAtLeast(0)) }
    }

    fun saveMeal(meal: MealEntity) = viewModelScope.launch { repository.saveMeal(meal.copy(epochDay = _day.value)) }
    fun deleteMeal(meal: MealEntity) = viewModelScope.launch { repository.deleteMeal(meal) }
    fun saveWorkout(w: WorkoutEntity) = viewModelScope.launch { repository.saveWorkout(w.copy(epochDay = _day.value)) }
    fun deleteWorkout(w: WorkoutEntity) = viewModelScope.launch { repository.deleteWorkout(w) }

    fun syncHealthConnect() = viewModelScope.launch {
        val hc = container.healthConnect
        message = if (!hc.hasPermission()) {
            "Connect Health Connect first: More → Health & steps."
        } else if (hc.sync()) {
            "Steps synced from Health Connect."
        } else {
            "No step data found in Health Connect."
        }
    }
}
