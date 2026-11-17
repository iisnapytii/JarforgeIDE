package com.jarforge.ide.data.model

enum class Loader(val id: String, val displayName: String) {
    FABRIC("fabric", "Fabric"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge");

    companion object {
        fun fromId(id: String): Loader? = entries.firstOrNull { it.id == id }
    }
}
