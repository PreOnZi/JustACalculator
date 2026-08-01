package com.fictioncutshort.justacalculator.platform

/**
 * Writes a file the player can find **outside the app**.
 *
 * This is not a convenience API — step 112 hinges on the user leaving the
 * calculator, opening a file manager and reading a code out of the file. If the
 * file is not reachable from a file browser, the puzzle is unsolvable.
 *
 *  - Android: the shared Downloads collection, via MediaStore.
 *  - iOS: the app's Documents directory, which surfaces in the Files app under
 *    "On My iPhone" because Info.plist sets UIFileSharingEnabled and
 *    LSSupportsOpeningDocumentsInPlace. Without both keys the directory stays
 *    private and the beat breaks.
 *
 * @return true if the file was written somewhere the user can reach.
 */
expect fun writeUserVisibleFile(context: AppContext, fileName: String, content: String): Boolean

/** Installed size of the app, used by the console's "storage" readout. */
expect fun appPackageSizeBytes(context: AppContext): Long
