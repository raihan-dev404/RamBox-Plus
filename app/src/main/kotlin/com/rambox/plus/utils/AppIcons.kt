// every launcher icon rambox can wear, each an activity-alias in the manifest with exactly one switched on

package com.rambox.plus.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.rambox.plus.R

enum class AppIconFamily(@StringRes val title: Int) {
    Mark(R.string.app_icon_family_mark),
}

/** [background] null is see-through: the launcher shows the wallpaper behind the art */
enum class AppIcon(
    val alias: String,
    val family: AppIconFamily,
    @DrawableRes val foreground: Int,
    @ColorRes val background: Int?,
    @StringRes val label: Int,
    /** ARGB colours of a gradient background, drawn in the picker; [background] stays the flat colour for the splash */
    val gradient: List<Long>? = null,
) {
    Default("IconDefault", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.ic_launcher_background, R.string.app_icon_default),
    Gradient("IconMarkGradient", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.app_icon_gradient, R.string.app_icon_gradient, listOf(0xFF6A5CFF, 0xFFC04DFF, 0xFFFF5FA2)),
    White("IconMarkWhite", AppIconFamily.Mark, R.drawable.ic_launcher_mark_dark, R.color.app_icon_white, R.string.app_icon_white),
    Transparent("IconMarkTransparent", AppIconFamily.Mark, R.drawable.ic_launcher_mark, null, R.string.app_icon_transparent),
    Ocean("IconMarkOcean", AppIconFamily.Mark, R.drawable.ic_launcher_mark, R.color.app_icon_ocean, R.string.app_icon_ocean, listOf(0xFF0EA5E9, 0xFF6366F1)),
}

object AppIcons {

    fun current(context: Context): AppIcon =
        prefs(context).getString(Key, null)?.let { name -> AppIcon.entries.firstOrNull { it.name == name } } ?: AppIcon.Default

    /** the icon a launch came from, read off the alias that started it; null for anything but a tap on an icon */
    fun launchedFrom(intent: Intent?): AppIcon? {
        val started = intent?.component?.className ?: return null
        return AppIcon.entries.firstOrNull { started.endsWith(".${it.alias}") }
    }

    /** whether this launch's splash showed [icon] itself: only an anime one, and only on android 13+, which can pick a splash per launch */
    fun splashShows(icon: AppIcon?): Boolean = false

    // the next launch's splash: the launcher icon for an anime one to bounce out of, the bare mark for the rest
    fun syncSplash(activity: Activity, icon: AppIcon = current(activity)) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val theme = R.style.Theme_App_Starting
        runCatching { activity.splashScreen.setSplashScreenTheme(theme) }
    }

    /** switches the launcher to [icon]; the launcher redraws it on its own time, a few seconds on some phones */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        // the new one first, so for a moment there are two icons rather than none
        pm.setComponentEnabledSetting(componentOf(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(componentOf(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        prefs(context).edit { putString(Key, icon.name) }
    }

    // the aliases are named after the code's package, which a debug build's app id doesn't share
    private fun componentOf(context: Context, icon: AppIcon) = ComponentName(context, "com.rambox.plus.${icon.alias}")

    private fun prefs(context: Context) = context.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    private const val Key = "icon"
}
