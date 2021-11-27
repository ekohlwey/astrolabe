package com.github.ekohlwey.astrolabe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend inline fun <T> doIo(crossinline closure: () -> T?): T? =
    withContext(Dispatchers.IO) { runCatching { closure.invoke() }.getOrNull() }
