package com.finscope.rpc.search;

import com.finscope.rpc.marketintel.ProviderCallDeadline;

import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class DeadlineBoundSearchCall {
    private static final int MAX_WORKERS = 4;
    private static final int MAX_QUEUED_CALLS = 16;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            MAX_WORKERS,
            MAX_WORKERS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_CALLS),
            runnable -> {
                Thread thread = new Thread(runnable,
                        "deadline-search-" + SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private DeadlineBoundSearchCall() { }

    static <T> T execute(String providerCode, Callable<T> operation) throws Exception {
        long remaining = ProviderCallDeadline.remainingMillis();
        if (remaining == Long.MAX_VALUE) return operation.call();
        if (remaining <= 0L) throw timeout(providerCode);

        Supplier<T> propagated = ProviderCallDeadline.propagate(() -> {
            try {
                return operation.call();
            } catch (Exception error) {
                throw new SearchCallFailure(error);
            }
        });
        Future<T> task;
        try {
            task = EXECUTOR.submit(propagated::get);
        } catch (RejectedExecutionException error) {
            throw unavailable(providerCode, error);
        }
        try {
            return task.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            task.cancel(true);
            EXECUTOR.purge();
            throw timeout(providerCode);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof SearchCallFailure && cause.getCause() instanceof Exception) {
                throw (Exception) cause.getCause();
            }
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("搜索调用异常", cause);
        } catch (InterruptedException error) {
            task.cancel(true);
            EXECUTOR.purge();
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private static WebSearchProviderException timeout(String providerCode) {
        return new WebSearchProviderException(providerCode, 0, true,
                providerCode + " request exceeded provider deadline");
    }

    private static WebSearchProviderException unavailable(String providerCode, Exception cause) {
        return new WebSearchProviderException(providerCode, 0, true,
                providerCode + " deadline worker pool is saturated: " + cause.getMessage());
    }

    private static final class SearchCallFailure extends RuntimeException {
        private SearchCallFailure(Exception cause) { super(cause); }
    }
}
