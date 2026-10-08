package com.ledga.app.di

import com.ledga.app.data.update.UpdateEndpoint
import com.ledga.app.data.update.UpdateEndpoints
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Spec §13.4: the release build reads GitHub and offers what it finds. Ledga dev has its own (R140). */
@Module
@InstallIn(SingletonComponent::class)
object UpdateEndpointModule {
    @Provides
    fun updateEndpoints(): UpdateEndpoints = UpdateEndpoints { UpdateEndpoint(UpdateEndpoint.GITHUB, offersUpdates = true) }
}
