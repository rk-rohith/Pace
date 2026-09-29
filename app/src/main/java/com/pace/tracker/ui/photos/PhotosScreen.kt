@file:OptIn(ExperimentalMaterial3Api::class)

package com.pace.tracker.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.ProgramData
import com.pace.tracker.data.db.MeasurementEntity
import com.pace.tracker.data.db.ProgressPhotoEntity
import com.pace.tracker.data.today
import com.pace.tracker.domain.PhotoPose
import com.pace.tracker.photo.PhotoInputButtons
import com.pace.tracker.ui.components.EmptyState
import com.pace.tracker.ui.components.NumberField
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.components.SectionCard
import com.pace.tracker.ui.components.kg
import com.pace.tracker.ui.components.oneDecimal
import com.pace.tracker.ui.components.paceViewModel
import com.pace.tracker.ui.components.shortDate
import com.pace.tracker.ui.components.signedKg
import com.pace.tracker.ui.components.toDecimalOrNull
import com.pace.tracker.ui.theme.PaceColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class PhotosViewModel(private val repository: PaceRepository) : ViewModel() {
    val data: StateFlow<ProgramData> = repository.programData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramData(null))

    fun addPhoto(pose: PhotoPose, path: String) = viewModelScope.launch { repository.addProgressPhoto(today(), pose, path) }
    fun deletePhoto(p: ProgressPhotoEntity) = viewModelScope.launch { repository.deleteProgressPhoto(p) }
    fun saveMeasurement(m: MeasurementEntity) = viewModelScope.launch { repository.saveMeasurement(m) }
    fun deleteMeasurement(m: MeasurementEntity) = viewModelScope.launch { repository.deleteMeasurement(m) }
}

/** Weight logged on (or closest before, else after) a given day. */
fun ProgramData.weightNear(day: Long): Double? {
    val w = logs.values.filter { it.weightKg != null }
    return (w.filter { it.epochDay <= day }.maxByOrNull { it.epochDay } ?: w.minByOrNull { abs(it.epochDay - day) })?.weightKg
}

@Composable
fun PhotosScreen() {
    val vm = paceViewModel { PhotosViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold("Progress") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Timeline", "Compare", "Measure").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }, modifier = Modifier.height(52.dp))
                }
            }
            when (tab) {
                0 -> Timeline(data, vm)
                1 -> Compare(data)
                else -> Measurements(data, vm)
            }
        }
    }
}

@Composable
private fun Timeline(data: ProgramData, vm: PhotosViewModel) {
    var viewing by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    val todays = data.photos.filter { it.epochDay == today() }
    val byDay = data.photos.groupBy { it.epochDay }.toSortedMap(compareByDescending { it })
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(3) }) {
            SectionCard("This week's photos", icon = Icons.Filled.PhotoCamera) {
                Text(
                    "Same spot, same light, same time of day. Add front, side and back.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PhotoPose.entries.forEach { pose ->
                    val done = todays.any { it.pose == pose }
                    Text(pose.label + if (done) " ✓ added today" else "", fontWeight = FontWeight.SemiBold,
                        color = if (done) PaceColors.Ahead else MaterialTheme.colorScheme.onSurface)
                    PhotoInputButtons(onPhoto = { vm.addPhoto(pose, it) })
                }
            }
        }
        if (byDay.isEmpty()) {
            item(span = { GridItemSpan(3) }) {
                EmptyState("No progress photos yet", "Your weekly photos will appear here as a timeline.", icon = Icons.Filled.PhotoCamera)
            }
        }
        byDay.forEach { (day, photos) ->
            item(span = { GridItemSpan(3) }) {
                val w = data.weightNear(day)
                Text(
                    "${day.shortDate()}" + (w?.let { " · ${it.kg()}" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(photos.sortedBy { it.pose.ordinal }, key = { it.id }) { photo ->
                PhotoTile(photo, overlay = "${photo.pose.label}\n${day.shortDate()}" + (data.weightNear(day)?.let { " · ${it.oneDecimal()}" } ?: ""),
                    modifier = Modifier.clickable { viewing = photo })
            }
        }
    }
    viewing?.let { photo ->
        FullPhotoDialog(photo, data.weightNear(photo.epochDay), onDelete = { vm.deletePhoto(photo); viewing = null }, onDismiss = { viewing = null })
    }
}

@Composable
private fun PhotoTile(photo: ProgressPhotoEntity, overlay: String, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))) {
        AsyncImage(model = File(photo.path), contentDescription = photo.pose.label, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(6.dp),
        ) {
            Text(overlay, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FullPhotoDialog(photo: ProgressPhotoEntity, weight: Double?, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }) {
            AsyncImage(model = File(photo.path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(16.dp)) {
                Text("${photo.pose.label} · ${photo.epochDay.shortDate()}" + (weight?.let { " · ${it.kg()}" } ?: ""), color = Color.White)
                Row {
                    TextButton(onClick = { confirm = true }) { Text("Delete", color = PaceColors.Error) }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete photo?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Compare(data: ProgramData) {
    var pose by rememberSaveable { mutableStateOf(PhotoPose.FRONT) }
    val poses = data.photos.filter { it.pose == pose }.sortedBy { it.epochDay }
    val days = poses.map { it.epochDay }.distinct()
    var leftPick by rememberSaveable(pose) { mutableStateOf<Long?>(null) }
    var rightPick by rememberSaveable(pose) { mutableStateOf<Long?>(null) }
    // Default: first vs latest date for this pose.
    val left = leftPick?.takeIf { it in days } ?: days.firstOrNull()
    val right = rightPick?.takeIf { it in days } ?: days.lastOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoPose.entries.forEach { FilterChip(selected = pose == it, onClick = { pose = it }, label = { Text(it.label) }, modifier = Modifier.height(48.dp)) }
        }
        if (days.size < 2) {
            EmptyState("Need two dates to compare", "Add ${pose.label.lowercase()} photos on at least two different days.", icon = Icons.Filled.PhotoCamera)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatePicker("Before", days, left, { leftPick = it }, Modifier.weight(1f))
            DatePicker("After", days, right, { rightPick = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(left, right).forEach { d ->
                val photo = poses.lastOrNull { it.epochDay == d }
                Column(Modifier.weight(1f)) {
                    if (photo != null && d != null) {
                        PhotoTile(photo, "${d.shortDate()}" + (data.weightNear(d)?.let { "\n${it.kg()}" } ?: ""))
                    }
                }
            }
        }
        val wl = left?.let { data.weightNear(it) }
        val wr = right?.let { data.weightNear(it) }
        if (wl != null && wr != null && left != null && right != null) {
            SectionCard {
                Text("${(right - left)} days apart · ${(wr - wl).signedKg()}", style = MaterialTheme.typography.titleMedium,
                    color = if (wr <= wl) PaceColors.Ahead else PaceColors.Behind)
                val ml = data.measurements.lastOrNull { it.epochDay <= left }
                val mr = data.measurements.lastOrNull { it.epochDay <= right }
                if (ml?.waistCm != null && mr?.waistCm != null) {
                    Text("Waist ${ml.waistCm.oneDecimal()} → ${mr.waistCm.oneDecimal()} cm")
                }
            }
        }
    }
}

@Composable
private fun DatePicker(label: String, days: List<Long>, selected: Long?, onSelect: (Long) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.shortDate() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            days.forEach { d ->
                DropdownMenuItem(text = { Text(d.shortDate()) }, onClick = { onSelect(d); expanded = false })
            }
        }
    }
}

private val measurementFields = listOf("Chest", "Waist", "Hips", "Arms", "Thighs", "Neck")

private fun MeasurementEntity.values(): List<Double?> = listOf(chestCm, waistCm, hipsCm, armsCm, thighsCm, neckCm)

@Composable
private fun Measurements(data: ProgramData, vm: PhotosViewModel) {
    val existing = data.measurements.firstOrNull { it.epochDay == today() }
    val inputs = remember(existing) {
        measurementFields.indices.map { i -> mutableStateOf(existing?.values()?.get(i)?.oneDecimal() ?: "") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Today's measurements (cm)", icon = Icons.Filled.Straighten) {
            measurementFields.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEachIndexed { col, name ->
                        val idx = row * 2 + col
                        NumberField(inputs[idx].value, { inputs[idx].value = it }, name, Modifier.weight(1f), suffix = "cm")
                    }
                }
            }
            Button(
                onClick = {
                    val v = inputs.map { it.value.toDecimalOrNull()?.takeIf { x -> x in 10.0..250.0 } }
                    vm.saveMeasurement(MeasurementEntity(today(), v[0], v[1], v[2], v[3], v[4], v[5]))
                },
                enabled = inputs.any { it.value.toDecimalOrNull() != null },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (existing == null) "Save measurements" else "Update today's measurements") }
            Text("Arms and thighs: measure the same side each week, at the widest point.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (data.measurements.isEmpty()) {
            EmptyState("No measurements yet", "Weekly tape measurements show fat loss the scale can miss.", icon = Icons.Filled.Straighten)
        } else {
            val first = data.measurements.first()
            data.measurements.reversed().forEach { m ->
                SectionCard(m.epochDay.shortDate(), trailing = {
                    TextButton(onClick = { vm.deleteMeasurement(m) }) { Text("Delete") }
                }) {
                    measurementFields.forEachIndexed { i, name ->
                        val v = m.values()[i] ?: return@forEachIndexed
                        val base = first.values()[i]
                        val delta = if (base != null && m != first) " (${String.format(java.util.Locale.US, "%+.1f", v - base)})" else ""
                        Row {
                            Text(name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${v.oneDecimal()} cm$delta")
                        }
                    }
                }
            }
        }
    }
}
