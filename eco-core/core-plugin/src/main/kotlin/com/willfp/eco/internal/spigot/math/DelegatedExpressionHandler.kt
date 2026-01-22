package com.willfp.eco.internal.spigot.math

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.placeholder.context.PlaceholderContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DelegatedExpressionHandler(
    plugin: EcoPlugin,
    private val handler: ExpressionHandler
) : ExpressionHandler {
    private val cacheTtl = plugin.configYml.getInt("math-cache-ttl")
    private val evaluationCache: AsyncCache<Int, Double?> = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtl.toLong(), TimeUnit.MILLISECONDS)
        .buildAsync()

    override fun evaluate(expression: String, context: PlaceholderContext): Double? {
        expression.fastToDoubleOrNull()?.let { return it }

        // Check if caching is disabled (TTL = 0)
        if (cacheTtl <= 0) {
            return handler.evaluate(expression, context)
                ?.let { if (it.isFinite()) it else null }
        }

        // Peak performance (totally not having fun with bitwise operators)
        val hash = (((expression.hashCode() shl 5) - expression.hashCode()) xor
                (context.player?.uniqueId?.hashCode() ?: 0)
                ) xor context.injectableContext.hashCode()

        // Try to get a completed result from the cache first (non-blocking)
        val cachedFuture = evaluationCache.getIfPresent(hash)
        if (cachedFuture?.isDone == true && !cachedFuture.isCompletedExceptionally) {
            return try {
                cachedFuture.get() // This won't block since isDone == true
            } catch (_: Exception) {
                null
            }
        }

        // If not in cache or not completed, compute synchronously
        // This is necessary because the ExpressionHandler interface is synchronous
        val result = handler.evaluate(expression, context)
            ?.let { if (it.isFinite()) it else null }

        // Cache the result for future use
        evaluationCache.put(hash, CompletableFuture.completedFuture(result))

        return result
    }
}
