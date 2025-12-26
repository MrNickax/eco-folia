package com.willfp.eco.internal.factory

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.factory.RunnableFactory
import com.willfp.eco.core.scheduling.RunnableTask
import com.willfp.eco.internal.scheduling.EcoRunnableTask
import java.util.function.Consumer

class EcoRunnableFactory(private val plugin: EcoPlugin) : RunnableFactory {
    override fun create(consumer: Consumer<RunnableTask>): RunnableTask {
        throw UnsupportedOperationException("Not supported in 1.20.5+")
    }
}