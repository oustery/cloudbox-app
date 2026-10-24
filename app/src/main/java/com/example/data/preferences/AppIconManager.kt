package com.example.data.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Switches the launcher icon by enabling exactly one launcher entry at a
 * time: the [MainActivity alias] itself for the standard look, or one of the
 * `activity-alias` components declared in the manifest for the alternatives.
 *
 * The change is applied with [PackageManager.DONT_KILL_APP] so the user keeps
 * their session; launchers refresh the icon within a few seconds. Component
 * state survives reboots ( PackageManager stores it), while the DataStore
 * copy in [AppSettings.iconVariant] mirrors the choice for the settings UI.
 */
object AppIconManager {

    private const val TAG = "AppIconManager"

    /** Manifest component (relative name) that owns each icon variant. */
    private val variantComponents = mapOf(
        AppIconVariant.STANDARD.key to "com.example.MainActivity",
        AppIconVariant.GRAPHITE.key to "com.example.LauncherGraphite",
        AppIconVariant.FROST.key to "com.example.LauncherFrost"
    )

    /**
     * Enables the launcher component for [variant] and disables the rest.
     * Safe to call repeatedly: components already in the desired state are
     * left untouched so the launcher does not flash the icon.
     */
    fun apply(context: Context, variant: AppIconVariant) {
        val pm = context.packageManager
        variantComponents.forEach { (variantKey, componentName) ->
            val component = ComponentName(context.packageName, componentName)
            val targetState = if (variantKey == variant.key) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                if (pm.getComponentEnabledSetting(component) != targetState) {
                    pm.setComponentEnabledSetting(
                        component,
                        targetState,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (e: Exception) {
                // A missing component must never crash the settings screen;
                // the launcher simply keeps the previous icon.
                Log.w(TAG, "Failed to switch launcher icon to $variantKey: ${e.message}")
            }
        }
    }
}
