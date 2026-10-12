package com.habitsheet.platform

import androidx.compose.runtime.Composable

/**
 * True when the person asked the system to reduce motion (iOS "Reduce Motion", Android animations off).
 * Programmatic scrolling and other decorative movement must jump instead of animating when it is set.
 */
@Composable
expect fun rememberReduceMotion(): Boolean
