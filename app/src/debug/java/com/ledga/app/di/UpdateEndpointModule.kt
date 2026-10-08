package com.ledga.app.di

import android.content.Context
import com.ledga.app.data.update.UpdateEndpoints
import com.ledga.app.debug.DebugUpdateEndpoints
import com.ledga.app.debug.DebugUpdateSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** R140: Ledga dev reads GitHub for Version history only, or the local test source adb has set (owner call A). */
@Module
@InstallIn(SingletonComponent::class)
object UpdateEndpointModule {
    @Provides
    fun updateEndpoints(@ApplicationContext context: Context): UpdateEndpoints = DebugUpdateEndpoints(DebugUpdateSource(context))
}
