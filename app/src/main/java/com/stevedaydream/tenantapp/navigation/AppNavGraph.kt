// tenantapp/navigation/AppNavGraph.kt
package com.stevedaydream.tenantapp.navigation

import SelectRoomScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.AuthRepository
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.ui.* // 匯入所有 UI
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    val authRepository = remember { AuthRepository(db.userDao()) }
    val context = LocalContext.current

    val startDestination = remember {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            "loading_user"
        } else {
            "visitor_home"
        }
    }

    NavHost(navController, startDestination = startDestination) {

        composable("loading_user") {
            LaunchedEffect(Unit) {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    val userFromDb = db.userDao().getUserById(firebaseUser.uid)
                    if (userFromDb != null) {
                        val destination = if (userFromDb.role == "tenant") "tenant_home/${userFromDb.id}" else "landlord_home/${userFromDb.id}"
                        navController.navigate(destination) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    } else {
                        // 【*** 這就是修正點：將 context 傳入 logout ***】
                        authRepository.logout(context)
                        navController.navigate("visitor_home") { popUpTo(0) }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        composable("login") {
            LoginScreen(
                onLoginSuccess = { user ->
                    val destination = if (user.role == "tenant") "tenant_home/${user.id}" else "landlord_home/${user.id}"
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                    }
                },
                onNavigateRegister = { navController.navigate("register") },
                authRepository = authRepository,
                navController = navController
            )
        }
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = { navController.popBackStack() },
                authRepository = authRepository,
                navController = navController
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


        // --- 租客相關頁面 ---
        composable(
            "tenant_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            TenantHomeScreen(
                userId = userId,
                onNavigate = { navController.navigate(it) },
                onLogout = {
                    authRepository.logout(context)
                    navController.navigate("visitor_home") { popUpTo(0) }
                }
            )
        }

        composable(
            "home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            RepairScreen(
                navController = navController,
                dao = db.repairReportDao(),
                db = db,
                userId = userId
            )
        }

        composable(
            "tenant_payment/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            TenantPaymentScreen(userId = userId, db = db, navController = navController)
        }

        composable(
            "select_room/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            SelectRoomScreen(userId = userId, db = db, onNavigateBack = { navController.popBackStack() })
        }

        composable(
            "request_room_change/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            RoomChangeRequestScreen(userId = userId, db = db, navController = navController)
        }


        // --- 房東相關頁面 ---
        composable(
            "landlord_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            LandlordHomeScreenWrapper(
                userId = userId,
                db = db,
                onNavigate = { navController.navigate(it) },
                onLogout = {
                    authRepository.logout(context)
                    navController.navigate("visitor_home") { popUpTo(0) }
                }
            )
        }
        composable("contract") {
            ContractPreviewScreen(navController = navController)
        }
        composable(
            "room_manage/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            RoomManageScreen(userId = userId, db = db, navController = navController)
        }
        composable(
            "room_change_approval/{landlordId}",
            arguments = listOf(navArgument("landlordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val landlordId = backStackEntry.arguments?.getString("landlordId") ?: ""
            RoomChangeApprovalScreen(landlordId = landlordId, db = db, navController = navController)
        }
        composable("excel_import") {
            ExcelImportScreen(
                roomDao = db.roomDao(),
                meterDao = db.electricMeterDao(),
                navController = navController
            )
        }

        // --- 共用頁面 ---
        composable(
            "announcement/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            var user by remember { mutableStateOf<User?>(null) }
            LaunchedEffect(userId) {
                user = db.userDao().getUserById(userId)
            }
            AnnouncementScreen(
                dao = db.announcementDao(),
                onNavigateBack = { navController.popBackStack() },
                currentUser = user,
                landlordCode = if (user?.role == "landlord") user?.landlordCode else null
            )
        }
        composable(
            "history/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            var user by remember { mutableStateOf<User?>(null) }
            LaunchedEffect(userId) {
                user = db.userDao().getUserById(userId)
            }
            val isLandlord = user?.role == "landlord"
            RepairHistoryScreen(
                navController = navController,
                dao = db.repairReportDao(),
                isLandlord = isLandlord
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
            "electricity_query/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ElectricityQueryScreen(userId = userId, db = db, navController = navController)
        }
    }
}


/**
 * 輔助用的 Wrapper (保持不變)
 */
@Composable
fun LandlordHomeScreenWrapper(
    userId: String,
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

    user?.let { landlord ->
        LandlordHomeScreen(
            landlord = landlord,
            onNavigate = { route ->
                when (route) {
                    "announcement" -> onNavigate("announcement/${landlord.id}")
                    "room_manage" -> onNavigate("room_manage/${landlord.id}")
                    "electricity_query" -> onNavigate("electricity_query/${landlord.id}")
                    "history" -> onNavigate("history/${landlord.id}")
                    "room_change_approval" -> onNavigate("room_change_approval/${landlord.id}")
                    "electricity/landlord" -> onNavigate("electricity/landlord")
                    else -> onNavigate(route)
                }
            },
            onLogout = onLogout
        )
    }
}
