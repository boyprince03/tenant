package com.stevedaydream.tenantapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.properties.ReadOnlyProperty

// 定義計費模式
enum class CalculationMode {
    TIERED,  // 累進制
    FIXED    // 固定制
}

// 用於儲存設定值的資料類別
data class CalculationSettings(
    val mode: CalculationMode,
    val fixedRate: Float,
    val tiers: List<Pair<Double, Double>> // Pair(級距範圍, 費率)
)

// 使用 DataStore 管理設定
class SettingsManager(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("electricity_settings")


        // Keys for DataStore
        val CALCULATION_MODE_KEY = stringPreferencesKey("calculation_mode")
        val FIXED_RATE_KEY = floatPreferencesKey("fixed_rate")

        // 預設的台電總級距 (分租套房前的原始數據)
        val DEFAULT_TIERS = listOf(
            Pair(120.0, 1.68),
            Pair(210.0, 2.305), // 330 - 120
            Pair(170.0, 3.365), // 500 - 330
            Pair(200.0, 4.59),  // 700 - 500
            Pair(300.0, 5.655), // 1000 - 700
            Pair(Double.MAX_VALUE, 7.545) // 1001 以上
        )
    }

    // 提供一個 Flow 讓 ViewModel 監聽設定變化
    val settingsFlow: Flow<CalculationSettings> = context.dataStore.data
        .map { preferences ->
            val mode = CalculationMode.valueOf(
                preferences[CALCULATION_MODE_KEY] ?: CalculationMode.TIERED.name
            )
            val fixedRate = preferences[FIXED_RATE_KEY] ?: 5.0f

            // 從 DataStore 讀取自定義的級距，如果沒有就用預設值
            val tiers = DEFAULT_TIERS.mapIndexed { index, (defaultRange, defaultRate) ->
                val range = preferences[doublePreferencesKey("tier_${index}_range")] ?: defaultRange
                val rate = preferences[doublePreferencesKey("tier_${index}_rate")] ?: defaultRate
                Pair(range, rate)
            }

            CalculationSettings(mode, fixedRate, tiers)
        }

    // 儲存設定
    suspend fun saveSettings(settings: CalculationSettings) {
        context.dataStore.edit { preferences ->
            preferences[CALCULATION_MODE_KEY] = settings.mode.name
            preferences[FIXED_RATE_KEY] = settings.fixedRate
            settings.tiers.forEachIndexed { index, (range, rate) ->
                preferences[doublePreferencesKey("tier_${index}_range")] = range
                preferences[doublePreferencesKey("tier_${index}_rate")] = rate
            }
        }
    }
}