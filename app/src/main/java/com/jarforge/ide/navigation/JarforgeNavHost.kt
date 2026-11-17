package com.jarforge.ide.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarforge.ide.feature.editor.EditorScreen
import com.jarforge.ide.feature.newproject.NewProjectScreen
import com.jarforge.ide.feature.projects.ProjectsScreen
import com.jarforge.ide.feature.settings.SettingsScreen

@Composable
fun JarforgeNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.PROJECTS
    ) {
        composable(Routes.PROJECTS) {
            ProjectsScreen(
                onNewProject = { navController.navigate(Routes.NEW_PROJECT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenEditor = { navController.navigate(Routes.EDITOR) }
            )
        }
        composable(Routes.NEW_PROJECT) {
            NewProjectScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EDITOR) {
            EditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
