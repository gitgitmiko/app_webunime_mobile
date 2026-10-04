package com.webunime.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.webunime.mobile.ui.account.AccountScreen
import com.webunime.mobile.ui.calendar.CalendarScreen
import com.webunime.mobile.ui.detail.DetailScreen
import com.webunime.mobile.ui.home.CatalogChooserScreen
import com.webunime.mobile.ui.home.HomeScreen
import com.webunime.mobile.ui.search.SearchScreen
import com.webunime.mobile.ui.theme.WebunimeTheme
import com.webunime.mobile.ui.theme.WuSurface
import com.webunime.mobile.ui.update.AppUpdateHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebunimeTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route.orEmpty()
                var catalogMode by rememberSaveable { mutableStateOf<String?>(null) }
                val showBottom = catalogMode != null &&
                    route in setOf("home", "search", "calendar", "settings")
                var updateCheckTrigger by remember { mutableIntStateOf(0) }

                fun openTitle(collection: String, slug: String, episode: Int = -1) {
                    nav.navigate("detail/$collection/$slug?ep=$episode")
                }

                fun goChoose() {
                    catalogMode = null
                    nav.navigate("choose") {
                        popUpTo("choose") { inclusive = true }
                        launchSingleTop = true
                    }
                }

                AppUpdateHost(autoCheck = true, checkTrigger = updateCheckTrigger)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showBottom,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            NavigationBar(
                                containerColor = WuSurface.copy(alpha = 0.96f),
                                tonalElevation = 0.dp,
                            ) {
                                val colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                NavigationBarItem(
                                    selected = route == "home",
                                    onClick = {
                                        nav.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("Home") },
                                    colors = colors,
                                )
                                NavigationBarItem(
                                    selected = route == "search",
                                    onClick = {
                                        nav.navigate("search") { launchSingleTop = true }
                                    },
                                    icon = { Icon(Icons.Default.Search, null) },
                                    label = { Text("Cari") },
                                    colors = colors,
                                )
                                if (catalogMode == "anime") {
                                    NavigationBarItem(
                                        selected = route == "calendar",
                                        onClick = {
                                            nav.navigate("calendar") { launchSingleTop = true }
                                        },
                                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                                        label = { Text("Jadwal") },
                                        colors = colors,
                                    )
                                }
                                NavigationBarItem(
                                    selected = route == "settings",
                                    onClick = {
                                        nav.navigate("settings") { launchSingleTop = true }
                                    },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    label = { Text("Settings") },
                                    colors = colors,
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "choose",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("choose") {
                            CatalogChooserScreen(
                                onPick = { picked ->
                                    catalogMode = picked
                                    nav.navigate("home") {
                                        popUpTo("choose") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                catalogMode = catalogMode ?: "anime",
                                onOpenAnime = { slug -> openTitle("anime", slug) },
                                onOpenTitle = { collection, slug, episode ->
                                    openTitle(collection, slug, episode)
                                },
                                onChangeCatalog = { goChoose() },
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                catalogMode = catalogMode ?: "anime",
                                onOpenTitle = { collection, slug ->
                                    openTitle(collection, slug)
                                },
                            )
                        }
                        composable("calendar") {
                            CalendarScreen(
                                onOpenAnime = { slug -> openTitle("anime", slug) },
                            )
                        }
                        composable("settings") {
                            AccountScreen(
                                onCheckUpdate = { updateCheckTrigger++ },
                            )
                        }
                        composable(
                            route = "detail/{collection}/{slug}?ep={ep}",
                            arguments = listOf(
                                navArgument("collection") { type = NavType.StringType },
                                navArgument("slug") { type = NavType.StringType },
                                navArgument("ep") {
                                    type = NavType.IntType
                                    defaultValue = -1
                                },
                            ),
                        ) { entry ->
                            val slug = entry.arguments?.getString("slug").orEmpty()
                            val collection = entry.arguments?.getString("collection").orEmpty()
                            val episode = entry.arguments?.getInt("ep") ?: -1
                            DetailScreen(
                                slug = slug,
                                collection = collection,
                                initialEpisode = episode,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
