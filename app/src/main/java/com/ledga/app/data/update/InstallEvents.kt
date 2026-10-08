package com.ledga.app.data.update

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** R138: the last install's failure, from Android's answer (`InstallStatusReceiver`) to the screens. Null: none to show. */
@Singleton
class InstallEvents @Inject constructor() {
    val failure = MutableStateFlow<String?>(null)
}
