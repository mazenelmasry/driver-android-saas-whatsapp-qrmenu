package app.qrmenu.driver.location.battery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.qrmenu.driver.location.R

/**
 * "قتلة البطارية… جزء من المنتج لا تفصيل" (CLAUDE.md). Several OEMs kill a
 * foreground service anyway unless the driver ALSO disables the manufacturer's
 * own battery manager for this app — the standard
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog does not cover that on
 * these OEM skins, so a manufacturer-specific screenshot-guide is the real fix,
 * not a nice-to-have.
 */
enum class Manufacturer(val displayNameRes: Int, val guideStepsRes: Array<Int>) {
    XIAOMI(
        R.string.battery_manufacturer_xiaomi,
        arrayOf(
            R.string.battery_guide_xiaomi_step1,
            R.string.battery_guide_xiaomi_step2,
            R.string.battery_guide_xiaomi_step3,
        ),
    ),
    OPPO(
        R.string.battery_manufacturer_oppo,
        arrayOf(R.string.battery_guide_oppo_step1, R.string.battery_guide_oppo_step2),
    ),
    VIVO(
        R.string.battery_manufacturer_vivo,
        arrayOf(R.string.battery_guide_vivo_step1, R.string.battery_guide_vivo_step2),
    ),
    HUAWEI(
        R.string.battery_manufacturer_huawei,
        arrayOf(
            R.string.battery_guide_huawei_step1,
            R.string.battery_guide_huawei_step2,
            R.string.battery_guide_huawei_step3,
        ),
    ),
    HONOR(
        R.string.battery_manufacturer_honor,
        arrayOf(R.string.battery_guide_honor_step1, R.string.battery_guide_honor_step2),
    ),
    SAMSUNG(
        R.string.battery_manufacturer_samsung,
        arrayOf(R.string.battery_guide_samsung_step1, R.string.battery_guide_samsung_step2),
    ),

    /** Stock/other Android: the system dialog alone is normally sufficient. */
    GENERIC(R.string.battery_manufacturer_generic, arrayOf(R.string.battery_guide_generic_step1)),
    ;

    companion object {
        fun detect(): Manufacturer = when (Build.MANUFACTURER.trim().lowercase()) {
            "xiaomi", "poco", "redmi" -> XIAOMI
            "oppo", "realme" -> OPPO
            "vivo", "iqoo" -> VIVO
            "huawei" -> HUAWEI
            "honor" -> HONOR
            "samsung" -> SAMSUNG
            else -> GENERIC
        }
    }
}

data class BatteryOptimizationState(
    val manufacturer: Manufacturer,
    /** Whether the standard system dialog is even needed — some OEM builds
     *  omit doze exemptions entirely, so this can legitimately be true already. */
    val isIgnoringBatteryOptimizations: Boolean,
)

object BatteryOptimizationGuide {

    fun currentState(context: Context): BatteryOptimizationState {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoring = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        return BatteryOptimizationState(
            manufacturer = Manufacturer.detect(),
            isIgnoringBatteryOptimizations = ignoring,
        )
    }

    /** The standard AOSP dialog. Always worth showing first, even on an OEM
     *  build with an extra manager — it is the one Android actually enforces. */
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /** `null` when there is nowhere OEM-specific to send the driver — the caller
     *  falls back to plain Android app-info settings. */
    fun manufacturerSettingsIntent(context: Context, manufacturer: Manufacturer): Intent? {
        val candidates: List<Intent> = when (manufacturer) {
            Manufacturer.XIAOMI -> listOf(
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            )
            Manufacturer.OPPO -> listOf(
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
            )
            Manufacturer.VIVO -> listOf(
                Intent().setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
            )
            Manufacturer.HUAWEI, Manufacturer.HONOR -> listOf(
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            )
            Manufacturer.SAMSUNG -> listOf(
                Intent().setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity",
                ),
            )
            Manufacturer.GENERIC -> emptyList()
        }
        val packageManager = context.packageManager
        return candidates.firstOrNull { it.resolveActivity(packageManager) != null }
    }

    /** Always resolves — falls back to the app's own settings page. */
    fun appSettingsFallbackIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
