package com.stevedaydream.tenantapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.ui.TenantHomeScreen
import com.stevedaydream.tenantapp.ui.RepairScreen
import com.stevedaydream.tenantapp.ui.HistoryScreen
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

import com.stevedaydream.tenantapp.ui.LandlordHomeScreen
import kotlinx.coroutines.launch


@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { user ->
                    if (user.role == "tenant")
                        navController.navigate("tenant_home/${user.id}")
                    else
                        navController.navigate("landlord_home/${user.id}")
                },
                onNavigateRegister = { navController.navigate("register") },
                userDao = db.userDao()
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = { navController.popBackStack() },
                userDao = db.userDao()
            )
        }
        composable("tenant_home/{userId}") { backStackEntry ->
            // 你可以類似設一個 TenantHomeScreenWrapper
            TenantHomeScreen(onNavigate = { navController.navigate(it) })
        }
        composable("landlord_home/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            LandlordHomeScreenWrapper(userId = userId, db = db, onNavigate = { navController.navigate(it) })
        }
        composable("home") {
            RepairScreen(navController, db.repairReportDao())
        }
        composable("history") {
            HistoryScreen(navController, db.repairReportDao())
        }
        composable("contract") {
            ContractPreviewScreen(navController)
        }
        composable("room_manage") {
            RoomManageScreen(db.roomDao(), navController)
        }
        composable("electricity") {
            ElectricityCalcScreen(db.roomDao(), db.electricMeterDao(), navController)
        }
        composable("announcement") {
            AnnouncementScreen(db.announcementDao())
        }
        composable("electricity_query") {
            ElectricityQueryScreen(db.roomDao(), db.electricMeterDao(), navController)
        }
        composable("excel_import") {
            ExcelImportScreen(db.roomDao(), db.electricMeterDao(), navController)
        }
    }
}
@Composable
fun LandlordHomeScreenWrapper(
    userId: Int,
    db: AppDatabase,
    onNavigate: (String) -> Unit
) {
    var user by remember { mutableStateOf<com.stevedaydream.tenantapp.data.User?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        scope.launch {
            user = db.userDao().getUserById(userId)
        }
    }

    user?.let {
        LandlordHomeScreen(
            landlordCode = it.landlordCode ?: "無",
            onNavigate = onNavigate
        )
    }
}