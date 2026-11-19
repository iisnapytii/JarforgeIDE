package com.jarforge.ide.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
                onOpenProject = { projectId ->
                    navController.navigate(Routes.editor(projectId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.NEW_PROJECT) {
            NewProjectScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(Routes.EDITOR_ARG_PROJECT_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments
                ?.getString(Routes.EDITOR_ARG_PROJECT_ID)
                .orEmpty()
            EditorScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
