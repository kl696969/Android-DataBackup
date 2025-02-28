package com.xayah.databackup.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

/**
 * Creates a [SlotScope]. Call it at top-level composable functions,
 * it's just something like a global slot scope for easier usage.
 */
@Composable
fun rememberSlotScope(): SlotScope {
    val dialogState = rememberDialogState()
    val navController = rememberNavController()
    return remember { SlotScope(navController = navController, dialogSlot = dialogState) }
}

data class SlotScope(
    val navController: NavHostController,
    val dialogSlot: DialogState,
)

/**
 * Provide this [androidx.compose.runtime.CompositionLocal] at top-level composable for passing [SlotScope] down through the Composition implicitly.
 */
val LocalSlotScope: ProvidableCompositionLocal<SlotScope?> = staticCompositionLocalOf { null }
