package com.pace.tracker.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pace.tracker.ui.charts.ChartsScreen
import com.pace.tracker.ui.history.HistoryScreen
import com.pace.tracker.ui.home.HomeScreen
import com.pace.tracker.ui.log.LogScreen
import com.pace.tracker.ui.onboarding.OnboardingScreen
import com.pace.tracker.ui.photos.PhotosScreen
import com.pace.tracker.ui.plan.MealPlanScreen
import com.pace.tracker.ui.plan.RecipeScreen
import com.pace.tracker.ui.review.ReviewDetailScreen
import com.pace.tracker.ui.review.ReviewListScreen
import com.pace.tracker.ui.settings.EngineInfoScreen
import com.pace.tracker.ui.settings.HealthScreen
import com.pace.tracker.ui.settings.MoreScreen
import com.pace.tracker.ui.settings.ProfileScreen
import com.pace.tracker.ui.settings.RemindersScreen
import com.pace.tracker.ui.streaks.StreaksScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LOG = "log"
    const val CHARTS = "charts"
    const val PHOTOS = "photos"
    const val MORE = "more"
    const val REVIEW = "review"
    const val HISTORY = "history"
    const val STREAKS = "streaks"
    const val REMINDERS = "reminders"
    const val PROFILE = "profile"
    const val HEALTH = "health"
    const val ENGINE = "engine"
    const val PLAN = "plan"
    const val RECIPE = "recipe"

    fun log(day: Long) = "$LOG?day=$day"
    fun review(week: Int) = "$REVIEW/$week"
    fun recipe(id: String) = "$RECIPE/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.LOG, "Log", Icons.Filled.EditNote),
    Tab(Routes.CHARTS, "Charts", Icons.Filled.Insights),
    Tab(Routes.PHOTOS, "Photos", Icons.Filled.PhotoCamera),
    Tab(Routes.MORE, "More", Icons.Filled.MoreHoriz),
)

@Composable
fun PaceNavHost(hasProfile: Boolean, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')?.substringBefore('/')
    val showBar = hasProfile && tabs.any { it.route == currentRoute }

    LaunchedEffect(pendingRoute, hasProfile) {
        if (pendingRoute != null && hasProfile) {
            nav.navigateTab(pendingRoute)
            onRouteHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = if (hasProfile) Routes.HOME else Routes.ONBOARDING) {
                composable(Routes.ONBOARDING) {
                    // Saving the profile flips hasProfile, which rebuilds the graph starting at Home.
                    OnboardingScreen()
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onLogToday = { nav.navigateTab(Routes.LOG) },
                        onOpen = { nav.navigate(it) },
                    )
                }
                composable(
                    "${Routes.LOG}?day={day}",
                    arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    LogScreen(initialDay = entry.arguments?.getLong("day") ?: -1L)
                }
                composable(Routes.CHARTS) { ChartsScreen() }
                composable(Routes.PHOTOS) { PhotosScreen() }
                composable(Routes.MORE) { MoreScreen(onOpen = { nav.navigate(it) }) }
                composable(Routes.REVIEW) {
                    ReviewListScreen(onBack = { nav.popBackStack() }, onOpenWeek = { nav.navigate(Routes.review(it)) })
                }
                composable(
                    "${Routes.REVIEW}/{week}",
                    arguments = listOf(navArgument("week") { type = NavType.IntType }),
                ) { entry ->
                    ReviewDetailScreen(week = entry.arguments?.getInt("week") ?: 1, onBack = { nav.popBackStack() })
                }
                composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.STREAKS) { StreaksScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.REMINDERS) { RemindersScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.HEALTH) { HealthScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ENGINE) { EngineInfoScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PLAN) {
                    MealPlanScreen(onBack = { nav.popBackStack() }, onOpenRecipe = { nav.navigate(Routes.recipe(it)) })
                }
                composable(
                    "${Routes.RECIPE}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    RecipeScreen(recipeId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Bottom-tab style navigation: single copy of each tab, state restored. */
private fun NavHostController.navigateTab(route: String) {
    val base = route.substringBefore('?').substringBefore('/')
    if (tabs.none { it.route == base }) {
        navigate(route)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
