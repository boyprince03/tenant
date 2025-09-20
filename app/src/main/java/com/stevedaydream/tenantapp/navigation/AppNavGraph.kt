package com.stevedaydream.tenantapp.navigation

// ... (imports保持不變)
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
import com.stevedaydream.tenantapp.ui.RoomChangeApprovalScreen
import com.stevedaydream.tenantapp.ui.RoomChangeRequestScreen
import com.stevedaydream.tenantapp.ui.TenantPaymentScreen

import com.stevedaydream.tenantapp.ui.VisitorHomeScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    NavHost(navController, startDestination = "visitor_home") {
        // ... (login, register, homescreens etc. 保持不變)
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
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0)
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
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0)
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

        // --- 【核心修改：修改此路由】 ---
        composable(
            "home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            RepairScreen(
                navController = navController,
                dao = db.repairReportDao(),
                db = db, // 傳入 db 以便查詢使用者資料
                userId = userId
            )
        }

        // --- 【核心修改：修改此路由以傳遞 userId】 ---
        composable(
            "history/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            var user by remember { mutableStateOf<User?>(null) }

            // 取得使用者資料來判斷角色
            LaunchedEffect(userId) {
                user = db.userDao().getUserById(userId)
            }

            user?.let {
                RepairHistoryScreen(
                    navController = navController,
                    dao = db.repairReportDao(),
                    isLandlord = it.role == "landlord" // 傳入角色判斷結果
                )
            }
        }

        composable("contract") {
            ContractPreviewScreen(navController)
        }
        composable(
            "room_manage/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            RoomManageScreen(
                userId = userId,
                db = db,
                navController = navController
            )
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
                onNavigateToQuery = { userId -> navController.navigate("electricity_query/$userId") },
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

        composable(
            "electricity_query/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            ElectricityQueryScreen(userId, db, navController)
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
        composable(
            "tenant_payment/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            TenantPaymentScreen(
                userId = userId,
                db = db,
                navController = navController
            )
        }
        // 【新增】房客申請更換房間頁面
        composable(
            "request_room_change/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            RoomChangeRequestScreen(
                userId = userId,
                db = db,
                navController = navController
            )
        }

        // 【新增】房東審核頁面
        composable(
            "room_change_approval/{landlordId}",
            arguments = listOf(navArgument("landlordId") { type = NavType.IntType })
        ) { backStackEntry ->
            val landlordId = backStackEntry.arguments?.getInt("landlordId") ?: 0
            RoomChangeApprovalScreen(
                landlordId = landlordId,
                db = db,
                navController = navController
            )
        }
    }
}

@Composable
fun LandlordHomeScreenWrapper(
    userId: Int,
    db: AppDatabase,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
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
            landlord = it,
            onNavigate = { route ->
                when (route) {
                    "announcement" -> onNavigate("announcement/${it.id}")
                    "room_manage" -> onNavigate("room_manage/${it.id}")
                    "electricity_query" -> onNavigate("electricity_query/${it.id}")
                    // --- 【核心修改：更新導航路徑】 ---
                    "history" -> onNavigate("history/${it.id}")
                    "room_change_approval" -> onNavigate("room_change_approval/${it.id}") // 新增
                    else -> onNavigate(route)
                }
            },
            onLogout = onLogout
        )
    }
}