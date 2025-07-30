package com.stevedaydream.tenantapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.ui.HomeScreen
import com.stevedaydream.tenantapp.ui.RepairScreen
import com.stevedaydream.tenantapp.ui.HistoryScreen
import com.stevedaydream.tenantapp.ui.ContractPreviewScreen
import com.stevedaydream.tenantapp.ui.RoomManageScreen
import com.stevedaydream.tenantapp.ui.ElectricityCalcScreen
import com.stevedaydream.tenantapp.ui.AnnouncementScreen

@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    NavHost(navController, startDestination = "mainhome") {
        composable("mainhome") {
            HomeScreen(onNavigate = { navController.navigate(it) })
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
    }
}