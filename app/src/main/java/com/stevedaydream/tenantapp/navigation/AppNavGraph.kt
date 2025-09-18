package com.stevedaydream.tenantapp.navigation

import SelectRoomScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.ui.TenantHomeScreen
import com.stevedaydream.tenantapp.ui.RepairScreen
import com.stevedaydream.tenantapp.ui.RepairHistoryScreen
import com.stevedaydream.tenantapp.ui.ContractPreviewScreen
import com.stevedaydream.tenantapp.ui.RoomManageScreen
import com.stevedaydream.tenantapp.ui.ElectricityCalcScreen
import com.stevedaydream.tenantapp.ui.AnnouncementScreen
import com.stevedaydream.tenantapp.ui.ElectricityQueryScreen
import com.stevedaydream.tenantapp.ui.ExcelImportScreen
import com.stevedaydream.tenantapp.ui.LandlordHomeScreen
import com.stevedaydream.tenantapp.ui.LoginScreen
import com.stevedaydream.tenantapp.ui.RegisterScreen
import androidx.compose.runtime.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.stevedaydream.tenantapp.data.User

import com.stevedaydream.tenantapp.ui.VisitorHomeScreen
import kotlinx.coroutines.launch


@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    NavHost(navController, startDestination = "visitor_home") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { user ->
                    if (user.role == "tenant")
                        navController.navigate("tenant_home/${user.id}")
                    else
                        navController.navigate("landlord_home/${user.id}")
                },
                onNavigateRegister = { navController.navigate("register") },
                userDao = db.userDao(),
                navController = navController
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = { navController.popBackStack() },
                userDao = db.userDao(),
                navController = navController
            )
        }
        composable(
            "tenant_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            TenantHomeScreen(
                userId = userId,
                onNavigate = { navController.navigate(it) },
                // 【核心修改】傳入登出邏輯
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) // 清除所有返回堆疊
                    }
                }
            )
        }
        composable("landlord_home/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            LandlordHomeScreenWrapper(
                userId = userId,
                db = db,
                onNavigate = { navController.navigate(it) },
                // 【核心修改】傳入登出邏輯
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) // 清除所有返回堆疊
                    }
                }
            )
        }
        composable("visitor_home") {
            VisitorHomeScreen(
                onNavigate = { navController.navigate(it) },
                announcementDao = db.announcementDao(),
                roomDao = db.roomDao(),
                navController = navController
            )
        }

        composable("home") {
            RepairScreen(navController, db.repairReportDao())
        }
        composable("history") {
            RepairHistoryScreen(navController, db.repairReportDao())
        }
        composable("contract") {
            ContractPreviewScreen(navController)
        }
        composable("room_manage") {
            RoomManageScreen(db.roomDao(), navController)
        }
        composable(
            "electricity/{userRole}",
            arguments = listOf(navArgument("userRole") { type = NavType.StringType })
        ) { backStackEntry ->
            val userRole = backStackEntry.arguments?.getString("userRole") ?: "tenant"
            ElectricityCalcScreen(
                roomDao = db.roomDao(),
                meterDao = db.electricMeterDao(),
                navController = navController,
                onNavigateToQuery = { navController.navigate("electricity_query") },
                userRole = userRole
            )
        }
        composable(
            "announcement/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            var user by remember { mutableStateOf<User?>(null) }
            LaunchedEffect(userId) {
                if (userId != 0) {
                    user = db.userDao().getUserById(userId)
                }
            }
            AnnouncementScreen(
                dao = db.announcementDao(),
                onNavigateBack = { navController.popBackStack() },
                currentUser = user,
                landlordCode = if (user?.role == "landlord") user?.landlordCode else null
            )
        }
        composable("electricity_query") {
            ElectricityQueryScreen(db.roomDao(), db.electricMeterDao(), navController)
        }
        composable("excel_import") {
            ExcelImportScreen(db.roomDao(), db.electricMeterDao(), navController)
        }
        composable(
            "select_room/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            SelectRoomScreen(
                userId = userId,
                db = db,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
@Composable
fun LandlordHomeScreenWrapper(
    userId: Int,
    db: AppDatabase,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit // 新增登出回呼
) {
    var user by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        scope.launch {
            user = db.userDao().getUserById(userId)
        }
    }

    user?.let {
        LandlordHomeScreen(
            landlordCode = it.landlordCode ?: "無",
            onNavigate = { route ->
                if (route == "announcement") {
                    onNavigate("announcement/${it.id}")
                } else {
                    onNavigate(route)
                }
            },
            onLogout = onLogout // 傳遞登出事件
        )
    }
}