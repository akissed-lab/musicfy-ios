package com.example.musicfy.viewmodels

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.reflect.KClass

/**
 * Replaces Hilt's generated factories. Lifecycle 2.10's common `Factory` takes
 * [CreationExtras], which we don't need: everything comes from [AppContainer].
 */
@Suppress("UNCHECKED_CAST")
fun <VM : ViewModel> viewModelFactory(initializer: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            initializer() as T
    }

/** Convenience wrapper for the usual `AppContainer`-first constructor signature. */
fun <VM : ViewModel> containerViewModelFactory(
    container: AppContainer,
    initializer: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory { initializer(container) }

/** Obtain a container-backed view model from a composable without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    factory: ViewModelProvider.Factory,
): VM = viewModel<VM>(key = key, factory = factory)
