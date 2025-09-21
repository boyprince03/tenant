package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stevedaydream.tenantapp.data.RepairReportRepository
import com.stevedaydream.tenantapp.data.UserDao

class RepairViewModelFactory(
    private val repairReportRepository: RepairReportRepository,
    private val userDao: UserDao,
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RepairViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RepairViewModel(repairReportRepository, userDao, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}