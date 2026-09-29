package com.pace.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pace.tracker.reminders.Notifications
import com.pace.tracker.ui.nav.PaceNavHost
import com.pace.tracker.ui.nav.Routes
import com.pace.tracker.ui.theme.PaceTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PaceApp).container
        pendingRoute = routeFrom(intent)

        // null = still loading, false = no profile yet (onboarding), true = ready.
        val hasProfileFlow = container.repository.profile.map { it != null }

        setContent {
            PaceTheme {
                val hasProfile by hasProfileFlow.collectAsStateWithLifecycle(initialValue = null)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    hasProfile?.let { ready ->
                        // A fresh nav graph once onboarding completes (start destination changes).
                        key(ready) {
                            PaceNavHost(
                                hasProfile = ready,
                                pendingRoute = pendingRoute,
                                onRouteHandled = { pendingRoute = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)?.let { pendingRoute = it }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as PaceApp).container
        container.stepTracker.start()
        lifecycleScope.launch {
            runCatching { container.repository.runPendingRecalibrations() }
            if (container.repository.setting(KEY_HC_ENABLED) == "true") runCatching { container.healthConnect.sync() }
        }
    }

    override fun onStop() {
        (application as PaceApp).container.stepTracker.stop()
        super.onStop()
    }

    private fun routeFrom(intent: Intent?): String? {
        intent ?: return null
        intent.getStringExtra(Notifications.EXTRA_ROUTE)?.let { return it }
        // Health Connect "privacy policy / rationale" entry points.
        if (intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent.action == "android.intent.action.VIEW_PERMISSION_USAGE"
        ) return Routes.HEALTH
        return null
    }

    companion object {
        const val KEY_HC_ENABLED = "health_connect_enabled"
    }
}
