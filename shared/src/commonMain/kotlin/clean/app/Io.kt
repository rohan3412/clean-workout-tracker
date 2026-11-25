package clean.app

import kotlinx.coroutines.CoroutineDispatcher

/** The dispatcher for blocking file and database work: `Dispatchers.IO`, which Kotlin/Native does not expose to common code. */
expect val ioDispatcher: CoroutineDispatcher
