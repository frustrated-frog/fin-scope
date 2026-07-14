package com.finscope.service.marketdata;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** 合并同一规范化范围内的并发刷新，失败任务不会残留。 */
@Component
public class MarketDataSingleFlight {
    private final ConcurrentMap<String, CompletableFuture<Object>> flights =
            new ConcurrentHashMap<String, CompletableFuture<Object>>();

    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Supplier<T> supplier) {
        CompletableFuture<Object> created = new CompletableFuture<Object>();
        CompletableFuture<Object> existing = flights.putIfAbsent(key, created);
        if (existing != null) {
            try {
                return (T) existing.join();
            } catch (CompletionException error) {
                throw propagate(error.getCause());
            }
        }
        try {
            T value = supplier.get();
            created.complete(value);
            return value;
        } catch (Throwable error) {
            created.completeExceptionally(error);
            throw propagate(error);
        } finally {
            flights.remove(key, created);
        }
    }

    private RuntimeException propagate(Throwable error) {
        return error instanceof RuntimeException
                ? (RuntimeException) error : new IllegalStateException(error);
    }
}
