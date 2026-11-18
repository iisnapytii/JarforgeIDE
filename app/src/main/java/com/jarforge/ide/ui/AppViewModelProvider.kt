package com.jarforge.ide.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jarforge.ide.AppContainer
import com.jarforge.ide.JarforgeApp
import com.jarforge.ide.feature.projects.ProjectsViewModel

@Composable
fun rememberAppContainer(): AppContainer {
    val appContext = LocalContext.current.applicationContext
    return (appContext as JarforgeApp).container
}

/** ViewModel factories. Dependencies come from the manual AppContainer. */
object AppViewModelProvider {

    fun projectsFactory(container: AppContainer): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { ProjectsViewModel(container.projectRepository) }
        }
}
