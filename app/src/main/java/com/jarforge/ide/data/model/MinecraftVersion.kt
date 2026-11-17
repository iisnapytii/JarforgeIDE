package com.jarforge.ide.data.model

data class MinecraftVersion(
    val id: String,
    val javaVersion: Int
)

/**
 * Minecraft targets offered in the app. These are generation targets only;
 * generated projects are not build-verified against them.
 */
object MinecraftVersions {
    private val all = listOf(
        MinecraftVersion("26.3", 25),
        MinecraftVersion("26.2", 25),
        MinecraftVersion("1.21.11", 21)
    )

    fun forLoader(loader: Loader): List<MinecraftVersion> {
        // Same list for every loader for now; kept per-loader for future divergence.
        return when (loader) {
            Loader.FABRIC, Loader.FORGE, Loader.NEOFORGE -> all
        }
    }

    fun find(loader: Loader, id: String): MinecraftVersion? =
        forLoader(loader).firstOrNull { it.id == id }
}
