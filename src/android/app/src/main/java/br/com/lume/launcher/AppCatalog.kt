package br.com.lume.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import android.util.LruCache
import br.com.lume.launcher.core.LauncherRules
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

data class InstalledApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val category: String
)

class AppCatalog(context: Context) {
    private val resources = context.resources
    private val iconSize = (48 * resources.displayMetrics.density).roundToInt()
    private val icons = object : LruCache<String, Bitmap>(2048) {
        override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val ownPackage = context.packageName
    private val packages = context.packageManager

    fun load(): List<InstalledApp> {
        val apps = linkedMapOf<String, InstalledApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (entry in packages.queryIntentActivities(intent, 0)) {
                val info = entry.activityInfo ?: continue
                if (info.packageName == ownPackage || !info.exported || !info.enabled ||
                    !info.applicationInfo.enabled || apps.containsKey(info.packageName)) continue
                apps[info.packageName] = InstalledApp(
                    info.packageName,
                    entry.loadLabel(packages).toString().ifBlank { info.packageName },
                    ComponentName(info.packageName, info.name),
                    when (info.applicationInfo.category) {
                        ApplicationInfo.CATEGORY_VIDEO -> resources.getString(R.string.common_video)
                        ApplicationInfo.CATEGORY_AUDIO -> resources.getString(R.string.common_music)
                        ApplicationInfo.CATEGORY_GAME -> resources.getString(R.string.common_games)
                        else -> resources.getString(R.string.common_apps)
                    }
                )
            }
        }
        val collator = Collator.getInstance(resources.configuration.locales[0])
        return apps.values.sortedWith { first, second ->
            val compared = collator.compare(first.label, second.label)
            if (compared != 0) compared else first.packageName.compareTo(second.packageName)
        }
    }

    fun icon(app: InstalledApp): Drawable {
        icons.get(app.packageName)?.let { return BitmapDrawable(resources, it) }
        val original = originalIcon(app)
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888).apply {
            density = resources.displayMetrics.densityDpi
        }
        val sourceBitmap = (original as? BitmapDrawable)?.bitmap
        val width = sourceBitmap?.width ?: original.intrinsicWidth.coerceAtLeast(1)
        val height = sourceBitmap?.height ?: original.intrinsicHeight.coerceAtLeast(1)
        val (targetWidth, targetHeight) = LauncherRules.fitIcon(iconSize, width, height, sourceBitmap == null)
        val left = (iconSize - targetWidth) / 2
        val top = (iconSize - targetHeight) / 2
        original.setBounds(left, top, left + targetWidth, top + targetHeight)
        original.draw(Canvas(bitmap))
        icons.put(app.packageName, bitmap)
        return BitmapDrawable(resources, bitmap)
    }

    private fun originalIcon(app: InstalledApp): Drawable {
        try {
            val info = packages.getActivityInfo(app.component, 0)
            val packageResources = packages.getResourcesForApplication(info.applicationInfo)
            if (info.iconResource != 0) {
                return packageResources.getDrawableForDensity(info.iconResource, DisplayMetrics.DENSITY_XXXHIGH, null)
                    ?: packages.getActivityIcon(app.component)
            }
            if (info.bannerResource != 0) {
                packageResources.getDrawableForDensity(info.bannerResource, DisplayMetrics.DENSITY_XXXHIGH, null)
                    ?.let { return it }
            }
            packages.getActivityBanner(app.component)?.let { return it }
            packages.getApplicationBanner(app.packageName)?.let { return it }
        } catch (_: PackageManager.NameNotFoundException) {
            // The app may have been removed since the catalog was loaded.
        } catch (_: Resources.NotFoundException) {
            // A missing declared resource must not prevent opening the launcher.
        } catch (_: SecurityException) {
            // Resource visibility can change between listing and rendering.
        }
        return packages.defaultActivityIcon
    }
}
