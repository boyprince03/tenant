@file:OptIn(ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.navigation


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.stevedaydream.tenantapp.data.*
import com.stevedaydream.tenantapp.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(navController: NavHostController, db: AppDatabase) {
    val scope = rememberCoroutineScope()

    // 【*** 核心修改 1：調整 Repository 初始化順序 ***】
    val roomRepository = remember { RoomRepository(db.roomDao(), scope) }
    val adminRepository = remember {
        AdminRepository(
            userDao = db.userDao(),
            roomDao = db.roomDao(),
            repairReportDao = db.repairReportDao(),
            announcementDao = db.announcementDao(),
            paymentDao = db.paymentDao(),
            electricMeterDao = db.electricMeterDao(),
            roomChangeRequestDao = db.roomChangeRequestDao(),
            roomRepository = roomRepository, // Pass RoomRepository
            db = db,
            coroutineScope = scope
        )
    }
    // 【*** 核心修改 2：將 adminRepository 注入 AuthRepository ***】
    val authRepository = remember { AuthRepository(db.userDao(), adminRepository) }

    val userRepository = remember { UserRepository(db.userDao(), scope) }
    val requestRepository = remember { RoomChangeRequestRepository(db.roomChangeRequestDao()) }
    val repairReportRepository = remember { RepairReportRepository(db.repairReportDao(), scope) }
    val announcementRepository = remember { AnnouncementRepository(db.announcementDao(), scope) }
    val electricMeterRepository = remember { ElectricMeterRepository(db.electricMeterDao(), scope) }
    val paymentRepository = remember { PaymentRepository(db.paymentDao(), scope) }

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
            var userFromDbState by remember { mutableStateOf<User?>(null) }
            var isLoadingUser by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    db.userDao().getUserById(firebaseUser.uid).collectLatest { user ->
                        userFromDbState = user
                        isLoadingUser = false
                    }
                } else {
                    isLoadingUser = false
                }
            }

            if (isLoadingUser) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                userFromDbState?.let {
                    val destination = when(it.role) {
                        "tenant" -> "tenant_home/${it.id}"
                        "landlord" -> "landlord_home/${it.id}"
                        "admin" -> "admin_home"
                        else -> "visitor_home"
                    }
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                } ?: run {
                    authRepository.logout(context)
                    navController.navigate("visitor_home") { popUpTo(0) }
                }
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
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
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
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                },
                authRepository = authRepository,
                navController = navController
            )
        }
        composable("visitor_home") {
            val factory = VisitorViewModelFactory(db.announcementDao(), db.roomDao())
            VisitorHomeScreen(
                navController = navController,
                viewModelFactory = factory
            )
        }
        // Admin Pages
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
        composable("admin_test_data") {
            AdminTestDataScreen(
                navController = navController,
                adminRepository = adminRepository
            )
        }
        composable("user_list") {
            UserListScreen(
                navController = navController,
                adminRepository = adminRepository,
                authRepository = authRepository
            )
        }

        composable(
            "history/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            var user by remember { mutableStateOf<User?>(null) }
            var isLoadingUser by remember { mutableStateOf(true) }
            LaunchedEffect(userId) {
                if (userId.isNotBlank()) {
                    db.userDao().getUserById(userId).collectLatest { userFromFlow ->
                        user = userFromFlow
                        isLoadingUser = false
                    }
                } else {
                    isLoadingUser = false
                }
            }
            if (isLoadingUser) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val isLandlord = user?.role == "landlord"
                RepairHistoryScreen(
                    navController = navController,
                    repository = repairReportRepository,
                    isLandlord = isLandlord
                )
            }
        }

        // Tenant Pages
        composable(
            "tenant_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            // 【*** 核心修改：將 userRepository 和 roomRepository 傳遞給工廠 ***】
            val factory = TenantViewModelFactory(userId, db, requestRepository, userRepository, roomRepository)
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
                repairReportRepository = repairReportRepository,
                userId = userId,
                db = db
            )
        }
        composable(
            "tenant_payment/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
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
                requestRepository = requestRepository,
                adminRepository = adminRepository // <-- 【*** 修改點 ***】
            )
        }

        // Landlord Pages
        composable(
            "landlord_home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val factory = LandlordViewModelFactory(userId, db, requestRepository)
            LandlordHomeScreen(
                navController = navController,
                viewModelFactory = factory,
                onLogout = {
                    authRepository.logout(context)
                    navController.navigate("visitor_home") { popUpTo(0) }
                }
            )
        }
        composable("contract") { ContractPreviewScreen(navController = navController) }
        composable(
            "room_manage/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            var currentUser by remember { mutableStateOf<User?>(null) }
            var isLoadingUser by remember { mutableStateOf(true) }
            LaunchedEffect(userId) {
                if (userId.isNotBlank()) {
                    db.userDao().getUserById(userId).collectLatest { user ->
                        currentUser = user; isLoadingUser = false
                    }
                } else { isLoadingUser = false }
            }
            if(isLoadingUser) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LandlordRoomManageScreen(roomRepository = roomRepository, currentUser = currentUser, navController = navController)
            }
        }
        composable(
            "room_change_approval/{landlordId}",
            arguments = listOf(navArgument("landlordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val landlordId = backStackEntry.arguments?.getString("landlordId") ?: ""
            RoomChangeApprovalScreen(
                landlordId = landlordId, db = db, navController = navController,
                userRepository = userRepository, roomRepository = roomRepository, requestRepository = requestRepository
            )
        }
        composable(
            "excel_import/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            val factory = ExcelImportViewModelFactory(roomRepository, electricMeterRepository, userRepository)
            val viewModel: ExcelImportViewModel = viewModel(factory = factory)
            ExcelImportScreen(
                navController = navController,
                viewModel = viewModel,
                userId = userId
            )
        }

        // Common Pages
        composable(
            "announcement/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            var user by remember { mutableStateOf<User?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            LaunchedEffect(userId) {
                if (userId.isNotBlank()) {
                    db.userDao().getUserById(userId).collectLatest { userFromFlow ->
                        user = userFromFlow; isLoading = false
                    }
                } else { isLoading = false }
            }
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                AnnouncementScreen(
                    dao = db.announcementDao(), onNavigateBack = { navController.popBackStack() },
                    currentUser = user, landlordCode = if (user?.role == "landlord") user?.landlordCode else null
                )
            }
        }
        composable(
            "electricity/{userRole}",
            arguments = listOf(navArgument("userRole") { type = NavType.StringType })
        ) { backStackEntry ->
            val userRole = backStackEntry.arguments?.getString("userRole") ?: "tenant"
            ElectricityCalcScreen(
                roomDao = db.roomDao(), meterRepository = electricMeterRepository, navController = navController,
                onNavigateToQuery = { userId -> navController.navigate("electricity_query/$userId") }, userRole = userRole
            )
        }
        composable(
            "electricity_query/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ElectricityQueryScreen(
                userId = userId,
                db = db,
                navController = navController,
                electricMeterRepository = electricMeterRepository
            )
        }
        // Admin "More" Pages
        composable("request_list_admin") {
            Scaffold(topBar = { TopAppBar(title = { Text("所有換房請求")}, navigationIcon = { IconButton(onClick = { navController.popBackStack()}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null)}})}) {
                Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) { Text("換房請求列表頁面") }
            }
        }
        composable("room_list_admin") {
            AdminRoomListScreen(navController = navController, adminRepository = adminRepository)
        }
        composable("repair_history_admin") {
            RepairHistoryScreen(navController = navController, repository = repairReportRepository, isLandlord = true)
        }
        composable("announcement_admin") {
            AnnouncementScreen(dao = db.announcementDao(), onNavigateBack = { navController.popBackStack() }, currentUser = null, landlordCode = null)
        }
    }
}