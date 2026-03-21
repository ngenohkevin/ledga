package com.ledga.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledga.app.data.repository.FontScale
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.ui.navigation.AppNavigation
import com.ledga.app.ui.navigation.HomeRoute
import com.ledga.app.ui.navigation.LedgaBottomNavBar
import com.ledga.app.ui.navigation.OnboardingRoute
import com.ledga.app.ui.navigation.UnparsedSmsRoute
import com.ledga.app.ui.theme.LedgaTheme
import com.ledga.app.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val hasCompletedOnboarding: StateFlow<Boolean> = settingsRepository.hasCompletedOnboarding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fontScale: StateFlow<FontScale> = settingsRepository.getFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontScale.SYSTEM)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
            val fontScale by viewModel.fontScale.collectAsState()

            LedgaTheme(themeMode = themeMode) {
                val density = LocalDensity.current
                val scaledDensity = if (fontScale == FontScale.SYSTEM) density
                else Density(density.density, fontScale.scale)

                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val startDestination: Any = if (hasCompletedOnboarding) HomeRoute else OnboardingRoute

                val showBottomBar = currentRoute != null &&
                        !currentRoute.contains("Onboarding") &&
                        !currentRoute.contains("UnparsedSms") &&
                        !currentRoute.contains("Budget")

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            LedgaBottomNavBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    AppNavigation(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                }
            }
        }
    }
}
