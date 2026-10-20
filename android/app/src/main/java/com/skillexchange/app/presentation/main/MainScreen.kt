package com.skillexchange.app.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import com.skillexchange.app.navigation.Screen
import com.skillexchange.app.presentation.home.HomeScreen
import com.skillexchange.app.presentation.notification.NotificationScreen
import com.skillexchange.app.presentation.notification.NotificationViewModel
import com.skillexchange.app.presentation.profile.myprofile.MyProfileScreen
import org.koin.androidx.compose.koinViewModel

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

    object Booking : BottomNavItem(
        route = Screen.BookingList.route,
        title = "Trao đổi",
        selectedIcon = Icons.Default.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    )

    object Notifications : BottomNavItem(
        route = Screen.Notifications.route,
        title = "Thông báo",
        selectedIcon = Icons.Default.Notifications,
        unselectedIcon = Icons.Outlined.Notifications
    )

    object Profile : BottomNavItem(
        route = Screen.MyProfile.route,
        title = "Hồ sơ",
        selectedIcon = Icons.Default.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

@Composable
fun MainScreen(
    rootNavController: NavHostController,
    bottomNavController: NavHostController = rememberNavController(),
    notificationViewModel: NotificationViewModel = koinViewModel()
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Booking,
        BottomNavItem.Notifications,
        BottomNavItem.Profile
    )

    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val notifState by notificationViewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val unreadCount = if (item == BottomNavItem.Notifications) notifState.unreadCount else 0L

                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(
                                            containerColor = Brand500,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            }
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
                        onNavigateToProfileDetail = { userId ->
                            rootNavController.navigate(Screen.ProfileDetail.createRoute(userId))
                        },
                        onNavigateToSkillGraph = {
                            rootNavController.navigate(Screen.SkillGraph.route)
                        },
                        onNavigateToProfileSetup = {
                            bottomNavController.navigate(Screen.MyProfile.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToNotifications = {
                            bottomNavController.navigate(Screen.Notifications.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToChat = {
                            rootNavController.navigate(Screen.ChatList.route)
                        }
                    )
                }

                // Compatibility alias: discovery -> home
                composable(Screen.Discovery.route) {
                    LaunchedEffect(Unit) {
                        bottomNavController.navigate(Screen.Home.route) {
                            popUpTo(bottomNavController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                composable(Screen.BookingList.route) {
                    com.skillexchange.app.presentation.booking.list.BookingListScreen(
                        onNavigateToDetail = { exchangeId ->
                            rootNavController.navigate(Screen.BookingDetail.createRoute(exchangeId))
                        }
                    )
                }

                composable(Screen.Notifications.route) {
                    NotificationScreen(
                        onNavigateBack = null,
                        showBackButton = false,
                        onNavigateToEntity = { type, entityId ->
                            when (type) {
                                "NEW_MESSAGE" -> rootNavController.navigate(Screen.ChatDetail.createRoute(entityId))
                                "RATING_REMINDER" -> rootNavController.navigate(Screen.Rating.createRoute(entityId))
                                else -> rootNavController.navigate(Screen.BookingDetail.createRoute(entityId))
                            }
                        },
                        viewModel = notificationViewModel
                    )
                }

                composable(Screen.MyProfile.route) {
                    MyProfileScreen(
                        onNavigateToEditProfile = {
                            rootNavController.navigate(Screen.ProfileSetup.route)
                        },
                        onNavigateToManageSkills = {
                            rootNavController.navigate(Screen.SkillSelection.route)
                        },
                        onNavigateToLogin = {
                            rootNavController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateToDashboard = {
                            rootNavController.navigate(Screen.Dashboard.route)
                        }
                    )
                }
            }
        }
    }
}
