@file:OptIn(ExperimentalLayoutApi::class)

package com.pace.tracker.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pace.tracker.data.db.FoodItemEntity
import com.pace.tracker.data.db.RecentFood
import com.pace.tracker.domain.FoodLibrary
import com.pace.tracker.domain.MealType
import com.pace.tracker.ui.components.grouped
import com.pace.tracker.ui.theme.PaceColors
import java.util.Locale
import kotlin.math.roundToInt

/** A row in the quick-add picker, from any source. Values are per serving. */
private data class PickItem(
    val key: String,
    val name: String,
    val serving: String,
    val kcal: Int,
    val protein: Double,
    val saved: FoodItemEntity? = null,
)

private fun fmtServings(s: Double) = if (s % 1.0 == 0.0) s.toInt().toString() else String.format(Locale.US, "%.1f", s)

/** Full-screen quick-add: recent foods, the built-in Indian food library and "My foods", with servings. */
@Composable
fun FoodPicker(
    initialType: MealType,
    recent: List<RecentFood>,
    myFoods: List<FoodItemEntity>,
    onAdd: (type: MealType, description: String, kcal: Int, protein: Double) -> Unit,
    onDeleteFood: (FoodItemEntity) -> Unit,
    onCustom: (MealType) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var tab by remember { mutableIntStateOf(if (recent.isEmpty()) 1 else 0) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PickItem?>(null) }
    var servings by remember { mutableDoubleStateOf(1.0) }
    var added by remember { mutableStateOf<String?>(null) }

    val recentItems = recent.map {
        PickItem("r:${it.description}:${it.calories}", it.description, "as logged before", it.calories, it.protein ?: 0.0)
    }
    val libraryItems = FoodLibrary.foods.map { PickItem("l:${it.name}:${it.serving}", it.name, it.serving, it.kcal, it.protein) }
    val myItems = myFoods.map { PickItem("m:${it.id}", it.name, it.serving, it.kcal, it.protein, saved = it) }
    val source = when (tab) {
        0 -> recentItems
        1 -> libraryItems
        else -> myItems
    }
    val list = if (query.isBlank()) source else source.filter { it.name.contains(query, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick add", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onCustom(type) }) { Text("Custom entry") }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Close") }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MealType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search foods") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Recent", "Library", "My foods").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i; selected = null }, text = { Text(t) }, modifier = Modifier.height(48.dp))
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (list.isEmpty()) {
                    item {
                        Text(
                            when (tab) {
                                0 -> "Foods you log will show up here for one-tap re-logging."
                                2 -> "Save a food from Custom entry (tick \"Save to My foods\") to see it here."
                                else -> "No foods match."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(list, key = { it.key }) { item ->
                    val isSel = selected?.key == item.key
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                            .clickable { selected = item; servings = 1.0; added = null }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(item.serving, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${item.kcal.grouped()} kcal", fontWeight = FontWeight.SemiBold)
                            Text(String.format(Locale.US, "%.0f g protein", item.protein), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.saved != null) {
                            IconButton(onClick = { onDeleteFood(item.saved); if (isSel) selected = null }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Filled.Delete, "Delete ${item.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            val sel = selected
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sel == null) {
                    Text(added ?: "Tap a food, choose servings, then Add.", color = if (added != null) PaceColors.Ahead else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
                } else {
                    val kcal = (sel.kcal * servings).roundToInt()
                    val protein = (sel.protein * servings * 10).roundToInt() / 10.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sel.name, style = MaterialTheme.typography.titleMedium)
                            Text("${kcal.grouped()} kcal · ${String.format(Locale.US, "%.0f", protein)} g protein",
                                color = MaterialTheme.colorScheme.primary)
                        }
                        FilledIconButton(onClick = { if (servings > 0.5) servings -= 0.5 }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Remove, "Fewer servings")
                        }
                        Text("${fmtServings(servings)}×", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(56.dp).padding(horizontal = 4.dp))
                        FilledIconButton(onClick = { if (servings < 10) servings += 0.5 }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Add, "More servings")
                        }
                    }
                    Button(
                        onClick = {
                            val desc = if (servings == 1.0) sel.name else "${sel.name} × ${fmtServings(servings)}"
                            onAdd(type, desc, kcal, protein)
                            added = "Added $desc to ${type.label.lowercase()}."
                            selected = null
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Add to ${type.label.lowercase()}") }
                }
            }
        }
    }
}
