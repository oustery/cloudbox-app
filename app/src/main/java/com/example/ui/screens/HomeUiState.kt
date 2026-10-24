package com.example.ui.screens

/**
 * Pure UI-state helpers behind the home screen's flexible chrome. Kept free
 * of Compose types so the rules are cheap to unit-test on the JVM.
 */

/**
 * Whether the search field + filter chips bar should be on screen.
 *
 * The bar is "flexible": it collapses as soon as the user dives into the
 * library (freeing vertical space for the actual list) and comes back when
 * the top is reached, when the search field regains focus or when a query is
 * already typed — hiding the bar mid-search would strand the keyboard.
 */
fun homeControlsVisible(
    atTop: Boolean,
    searchQuery: String,
    searchFieldFocused: Boolean,
    hasContent: Boolean
): Boolean = hasContent && (atTop || searchQuery.isNotBlank() || searchFieldFocused)

/** Whether the scroll-to-top shortcut should pop in above the add FAB. */
fun showScrollToTop(firstVisibleItemIndex: Int): Boolean = firstVisibleItemIndex > 2
