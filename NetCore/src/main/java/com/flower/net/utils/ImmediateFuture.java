package com.flower.net.utils;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ImmediateFuture<T> implements Future<T> {
    public static <T> Future<T> of(T result) {
        return new ImmediateFuture<>(result);
    }

    final T result;

    private ImmediateFuture(T result) {
        this.result = result;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }

    @Override
    @Nullable
    public Throwable cause() {
        return null;
    }

    @Override
    public Future<T> addListener(GenericFutureListener<? extends Future<? super T>> listener) {
        @SuppressWarnings("unchecked")
        GenericFutureListener<Future<? super T>> safeListener = (GenericFutureListener<Future<? super T>>) listener;
        try {
            safeListener.operationComplete(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @SafeVarargs
    @Override
    public final Future<T> addListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
        for (GenericFutureListener<? extends Future<? super T>> listener : listeners) {
            addListener(listener);
        }
        return this;
    }

    @Override
    public Future<T> removeListener(GenericFutureListener<? extends Future<? super T>> listener) {
        return this;
    }

    @SafeVarargs
    @Override
    public final Future<T> removeListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
        return this;
    }

    @Override
    public Future<T> sync() throws InterruptedException {
        return this;
    }

    @Override
    public Future<T> syncUninterruptibly() {
        return this;
    }

    @Override
    public Future<T> await() throws InterruptedException {
        return this;
    }

    @Override
    public Future<T> awaitUninterruptibly() {
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return true;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return true;
    }

    @Override
    public T getNow() {
        return result;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return result;
    }
}
