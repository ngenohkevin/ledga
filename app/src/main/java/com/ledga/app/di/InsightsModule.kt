package com.ledga.app.di

import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.rules.AnomalyRule
import com.ledga.app.data.insights.rules.FeeTipRule
import com.ledga.app.data.insights.rules.FulizaAutoPayRule
import com.ledga.app.data.insights.rules.FulizaRule
import com.ledga.app.data.insights.rules.PositiveNudgeRule
import com.ledga.app.data.insights.rules.RecurringRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InsightsModule {

    @Provides
    @Singleton
    fun provideInsightRules(
        anomaly: AnomalyRule,
        recurring: RecurringRule,
        feeTip: FeeTipRule,
        fuliza: FulizaRule,
        fulizaAutoPay: FulizaAutoPayRule,
        positive: PositiveNudgeRule,
    ): List<InsightRule> = listOf(anomaly, recurring, feeTip, fuliza, fulizaAutoPay, positive)
}
