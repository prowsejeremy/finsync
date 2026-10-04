package com.jpd.finsync.ui

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

/**
 * Runs [actionId] only while [fromId] is the current destination. Otherwise a quick double tap
 * would run the action again from the next screen, where it doesn't exist, and crash.
 */
fun Fragment.navigateSafely(@IdRes fromId: Int, @IdRes actionId: Int) {
    val navController = findNavController()
    if (navController.currentDestination?.id == fromId) navController.navigate(actionId)
}
