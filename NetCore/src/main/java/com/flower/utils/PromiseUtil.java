package com.flower.utils;

import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.channel.EventLoop;
import java.util.concurrent.TimeUnit;

public class PromiseUtil {
    public static <T> Promise<T> withTimeout(EventLoop eventLoop, Promise<T> promise, long timeoutMillis) {
        ScheduledFuture<?> timeoutFuture = eventLoop.schedule(() -> {
            if (!promise.isDone()) {
                promise.setFailure(new RuntimeException("Operation timed out"));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        promise.addListener(future -> timeoutFuture.cancel(false));
        return promise;
    }
}
