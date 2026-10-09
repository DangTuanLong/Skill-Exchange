package com.skillexchange.app.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import com.skillexchange.app.navigation.PlaceholderScreen
import com.skillexchange.app.navigation.Screen
import com.skillexchange.app.presentation.discovery.DiscoveryScreen
import com.skillexchange.app.presentation.home.HomeScreen
import com.skillexchange.app.presentation.profile.ProfileSetupScreen

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem(
        route = Screen.Home.route,
        title = "Trang chủ",
        selectedIcon = Icons.Default.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Discovery : BottomNavItem(
        route = Screen.Discovery.route,
        title = "Khám phá",
        selectedIcon = Icons.Default.Search,
        unselectedIcon = Icons.Outlined.Search
    )

    object Chat : BottomNavItem(
        route = Screen.ChatList.route,
        title = "Tin nhắn",
        selectedIcon = Icons.Default.Chat,
        unselectedIcon = Icons.Outlined.Chat
    )

    object Booking : BottomNavItem(
        route = Screen.BookingList.route,
        title = "Lịch hẹn",
        selectedIcon = Icons.Default.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    )

    object Profile : BottomNavItem(
        route = Screen.ProfileSetup.route,
        title = "Hồ sơ",
        selectedIcon = Icons.Default.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

@Composable
fun MainScreen(
    rootNavController: NavHostController,
    bottomNavController: NavHostController = rememberNavController()
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Discovery,
        BottomNavItem.Chat,
        BottomNavItem.Booking,
        BottomNavItem.Profile
    )

    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (currentRoute != item.route) {
                                bottomNavController.navigate(item.route) {
                                    popUpTo(bottomNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand500,
                            selectedTextColor = Brand500,
                            indicatorColor = Color(0xFFEFF6FF),
                            unselectedIconColor = TextSecondaryLight,
                            unselectedTextColor = TextSecondaryLight
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = bottomNavController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToDiscovery = {
                            bottomNavController.navigate(Screen.Discovery.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToProfileSetup = {
                            bottomNavController.navigate(Screen.ProfileSetup.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(Screen.Discovery.route) {
                    DiscoveryScreen(
                        onNavigateToProfileDetail = { userId ->
                            rootNavController.navigate(Screen.ProfileDetail.createRoute(userId))
                        }
                    )
                }

                composable(Screen.ChatList.route) {
                    com.skillexchange.app.presentation.chat.list.ChatListScreen(
                        onNavigateToChat = { chatId ->
                            rootNavController.navigate(Screen.ChatDetail.createRoute(chatId))
                        }
                    )
                }

                composable(Screen.BookingList.route) {
                    com.skillexchange.app.presentation.booking.list.BookingListScreen(
                        onNavigateToDetail = { exchangeId ->
                            rootNavController.navigate(Screen.BookingDetail.createRoute(exchangeId))
                        }
                    )
                }

                composable(Screen.ProfileSetup.route) {
                    ProfileSetupScreen(
                        onNavigateToSkillSelection = {
                            rootNavController.navigate(Screen.SkillSelection.route)
                        },
                        onNavigateToHome = {
                            bottomNavController.navigate(Screen.Home.route)
                        }
                    )
                }
            }
        }
    }
}
