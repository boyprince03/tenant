@file:OptIn(ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.navigation

import SelectRoomScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
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
import com.stevedaydream.tenantapp.data.*
import com.stevedaydream.tenantapp.ui.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    // --- 統一建立所有 Repositories ---
    val authRepository = remember { AuthRepository(db.userDao()) }
    val roomRepository = remember { RoomRepository(db.roomDao()) }
    val userRepository = remember { UserRepository(db.userDao()) }
    val requestRepository = remember { RoomChangeRequestRepository(db.roomChangeRequestDao()) }
    val adminRepository = remember { AdminRepository() }
    val repairReportRepository = remember { RepairReportRepository(db.repairReportDao()) }
    val announcementRepository = remember { AnnouncementRepository(db.announcementDao()) }
    val electricMeterRepository = remember { ElectricMeterRepository(db.electricMeterDao()) }
    val paymentRepository = remember { PaymentRepository(db.paymentDao()) }


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
                        val destination = when(userFromDb.role) {
                            "tenant" -> "tenant_home/${userFromDb.id}"
                            "landlord" -> "landlord_home/${userFromDb.id}"
                            "admin" -> "admin_home"
                            else -> "visitor_home"
                        }
                        navController.navigate(destination) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    } else {
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
                    val destination = when(user.role) {
                        "tenant" -> "tenant_home/${user.id}"
                        "landlord" -> "landlord_home/${user.id}"
                        "admin" -> "admin_home"
                        else -> "visitor_home"
                    }
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
                onRegisterSuccess = { user ->
                    val destination = if (user.role == "tenant") "tenant_home/${user.id}" else "landlord_home/${user.id}"
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                    }
                },
                authRepository = authRepository,
                navController = navController
            )
        }
        composable("visitor_home") {
            // 建立 Factory 並傳入
            val factory = VisitorViewModelFactory(db.announcementDao(), db.roomDao())
            VisitorHomeScreen(
                navController = navController,
                viewModelFactory = factory
            )
        }
        // --- 管理員頁面 ---
        composable("admin_home") {
            AdminHomeScreen(
                navController = navController,
                adminRepository = adminRepository,
                onLogout = {
                    authRepository.logout(context)
                    navController.navigate("visitor_home") { popUpTo(0) }
                }
            )
        }

        // --- 管理員「更多」頁面路由 ---
        composable("user_list") {
            UserListScreen(
                navController = navController,
                adminRepository = adminRepository,
                authRepository = authRepository
            )
        }

        composable("request_list_admin") {
            Scaffold(topBar = { TopAppBar(title = { Text("所有換房請求")}, navigationIcon = { IconButton(onClick = { navController.popBackStack()}) { Icon(Icons.Default.ArrowBack, null)}})}) {
                Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) { Text("換房請求列表頁面") }
            }
        }

        composable("room_list_admin") {
            var adminUser by remember { mutableStateOf<User?>(null) }
            LaunchedEffect(Unit) {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    adminUser = db.userDao().getUserById(firebaseUser.uid)
                }
            }
            if (adminUser != null) {
                RoomManageScreen(
                    roomRepository = roomRepository,
                    currentUser = adminUser,
                    navController = navController
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        composable("repair_history_admin") {
            RepairHistoryScreen(
                navController = navController,
                dao = db.repairReportDao(),
                isLandlord = true
            )
        }

        composable("announcement_admin") {
            var adminUser by remember { mutableStateOf<User?>(null) }
            LaunchedEffect(Unit) {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    adminUser = db.userDao().getUserById(firebaseUser.uid)
                }
            }
            AnnouncementScreen(
                dao = db.announcementDao(),
                onNavigateBack = { navController.popBackStack() },
                currentUser = adminUser,
                landlordCode = null
            )
        }


        // --- 租客相關頁面 ---
        composable(
            "tenant_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            // 建立 Factory 並傳入
            val factory = TenantViewModelFactory(userId, db, requestRepository)
            TenantHomeScreen(
                navController = navController,
                viewModelFactory = factory,
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
            // 【*** 修正：傳遞 Repositories 和 DAOs ***】
            TenantPaymentScreen(
                userId = userId,
                navController = navController,
                userDao = db.userDao(),
                roomDao = db.roomDao(),
                electricMeterRepository = electricMeterRepository,
                paymentRepository = paymentRepository
            )
        }

        composable(
            "select_room/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            SelectRoomScreen(
                userId = userId,
                userRepository = userRepository,
                roomRepository = roomRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            "request_room_change/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            RoomChangeRequestScreen(
                userId = userId,
                db = db,
                navController = navController,
                requestRepository = requestRepository
            )
        }


        // --- 房東相關頁面 ---
        composable(
            "landlord_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            // 建立 Factory 並傳入
            val factory = LandlordViewModelFactory(userId, db, requestRepository, adminRepository)
            LandlordHomeScreen(
                navController = navController,
                viewModelFactory = factory,
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
            var currentUser by remember { mutableStateOf<User?>(null) }
            LaunchedEffect(userId) {
                currentUser = db.userDao().getUserById(userId)
            }

            RoomManageScreen(
                roomRepository = roomRepository,
                currentUser = currentUser,
                navController = navController
            )
        }
        composable(
            "room_change_approval/{landlordId}",
            arguments = listOf(navArgument("landlordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val landlordId = backStackEntry.arguments?.getString("landlordId") ?: ""
            RoomChangeApprovalScreen(
                landlordId = landlordId,
                db = db,
                navController = navController,
                userRepository = userRepository,
                roomRepository = roomRepository,
                requestRepository = requestRepository
            )
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
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(userId) {
                user = db.userDao().getUserById(userId)
                isLoading = false
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AnnouncementScreen(
                    dao = db.announcementDao(),
                    onNavigateBack = { navController.popBackStack() },
                    currentUser = user,
                    landlordCode = if (user?.role == "landlord") user?.landlordCode else null
                )
            }
        }
        composable("repair_screen/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            RepairScreen(
                navController = navController,
                dao = db.repairReportDao(),
                db = db,
                userId = userId
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
            // 【*** 修正：傳遞 meterRepository ***】
            ElectricityCalcScreen(
                roomDao = db.roomDao(),
                meterRepository = electricMeterRepository, // 改為傳遞 Repository
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

