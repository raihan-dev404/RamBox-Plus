// NavControllerUtils.kt

package com.rambox.plus.ui.utils

import androidx.navigation.NavController
import com.rambox.plus.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
