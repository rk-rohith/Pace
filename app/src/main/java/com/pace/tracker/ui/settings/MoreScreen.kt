package com.pace.tracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pace.tracker.ui.components.ScreenScaffold
import com.pace.tracker.ui.nav.Routes

private data class MoreItem(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

private val items = listOf(
    MoreItem("Weekly review", "Summaries, recalibrations & reflections", Icons.Filled.CalendarMonth, Routes.REVIEW),
    MoreItem("Target history", "How your calorie target evolved", Icons.Filled.History, Routes.HISTORY),
    MoreItem("Streaks & badges", "Logging, steps, calories, workouts", Icons.Filled.EmojiEvents, Routes.STREAKS),
    MoreItem("Reminders", "Weigh-in, meals, water, workouts, photos", Icons.Filled.Notifications, Routes.REMINDERS),
    MoreItem("Profile & goal", "Body stats, pace, targets", Icons.Filled.Person, Routes.PROFILE),
    MoreItem("Health & steps", "Health Connect and step counter", Icons.Filled.HealthAndSafety, Routes.HEALTH),
    MoreItem("How the adaptive engine works", "Formula and tunable constants", Icons.Filled.Info, Routes.ENGINE),
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    ScreenScaffold("More") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(item.route) },
                ) {
                    Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }
            Text(
                "Pace · offline, single-user. All data and photos stay on this device.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
