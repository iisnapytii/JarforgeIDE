package com.jarforge.ide.feature.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.jarforge.ide.R
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.MinecraftVersions
import com.jarforge.ide.data.storage.templates.LoaderTemplates

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

data class AppInfo(
    val name: String,
    val versionName: String,
    val buildNumber: String,
    val packageName: String
)

/** Reads the real version data of the installed app. */
@Suppress("DEPRECATION")
fun readAppInfo(context: Context): AppInfo {
    val packageName = context.packageName
    val info: PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageInfo(packageName, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    val buildNumber = when {
        info == null -> "Unknown"
        Build.VERSION.SDK_INT >= 28 -> info.longVersionCode.toString()
        else -> info.versionCode.toString()
    }

    return AppInfo(
        name = context.getString(R.string.app_name),
        versionName = info?.versionName ?: "Unknown",
        buildNumber = buildNumber,
        packageName = packageName
    )
}

// ---------------------------------------------------------------------------
// Build environment
// ---------------------------------------------------------------------------

data class EnvironmentItem(
    val name: String,
    val ready: Boolean,
    val statusText: String
)

/**
 * Fixed description of the build environment at the current development stage.
 * This is NOT a live system check. Update it when a real build engine exists.
 */
object BuildEnvironmentInfo {

    const val SUMMARY =
        "Build environment is not configured. Jarforge cannot compile or build mods yet."

    val items: List<EnvironmentItem> = listOf(
        EnvironmentItem("Build engine", ready = false, statusText = "Not configured"),
        EnvironmentItem("Java compiler", ready = false, statusText = "Not configured"),
        EnvironmentItem("Gradle", ready = false, statusText = "Not configured"),
        EnvironmentItem("Mod JAR export", ready = false, statusText = "Not available")
    )

    val isConfigured: Boolean
        get() = items.all { it.ready }
}

// ---------------------------------------------------------------------------
// Supported targets
// ---------------------------------------------------------------------------

data class JavaGroup(
    val javaVersion: Int,
    val minecraftVersions: List<String>
)

data class LoaderTargetInfo(
    val loader: Loader,
    val javaGroups: List<JavaGroup>,
    val mixinSupported: Boolean,
    val buildVerified: Boolean
)

/** Built from the same data the New Project screen uses, so the two cannot disagree. */
fun loadLoaderTargets(): List<LoaderTargetInfo> =
    Loader.entries.map { loader ->
        val template = LoaderTemplates.forLoader(loader)
        val groups = MinecraftVersions.forLoader(loader)
            .groupBy { it.javaVersion }
            .entries
            .sortedByDescending { it.key }
            .map { entry ->
                JavaGroup(
                    javaVersion = entry.key,
                    minecraftVersions = entry.value.map { it.id }
                )
            }
        LoaderTargetInfo(
            loader = loader,
            javaGroups = groups,
            mixinSupported = template.supportsMixin,
            buildVerified = template.buildVerified
        )
    }
