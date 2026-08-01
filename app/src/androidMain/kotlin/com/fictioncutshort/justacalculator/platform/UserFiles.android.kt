package com.fictioncutshort.justacalculator.platform

import com.fictioncutshort.justacalculator.util.writeToDownloads

actual fun writeUserVisibleFile(
    context: AppContext,
    fileName: String,
    content: String,
): Boolean = writeToDownloads(context, fileName, content)

actual fun appPackageSizeBytes(context: AppContext): Long =
    java.io.File(context.applicationInfo.sourceDir).length()
