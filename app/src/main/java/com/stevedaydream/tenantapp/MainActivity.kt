package com.stevedaydream.tenantapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        setContent {
            val navController = rememberNavController()
            AppNavGraph(navController, db)
        }
    }
}
