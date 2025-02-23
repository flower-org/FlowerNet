package com.flower.utils;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;

public class ImmediatePromise<T> extends DefaultPromise<T> {
    public static <T> Promise<T> of(T result) {
        return new ImmediatePromise<>(result);
    }

    private ImmediatePromise(T result) {
        super();
        setSuccess(result);
    }
}
