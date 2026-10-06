package com.jpd.hz.ui

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController

/**
 * Runs [actionId] only while [fromId] is the current destination. Otherwise a quick double tap
 * would run the action again from the next screen, where it doesn't exist, and crash.
 */
fun Fragment.navigateSafely(@IdRes fromId: Int, @IdRes actionId: Int, args: Bundle? = null) {
    val navController = findNavController()
    if (navController.currentDestination?.id == fromId) navController.navigate(actionId, args)
}

/**
 * For global actions, which work from any screen: runs [actionId] unless [destinationId] is
 * already showing, so a double tap can't stack two copies.
 */
fun NavController.navigateUnlessShowing(@IdRes destinationId: Int, @IdRes actionId: Int) {
    if (currentDestination?.id != destinationId) navigate(actionId)
}
