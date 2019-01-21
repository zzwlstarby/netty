/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;


/**
 * The result of an asynchronous operation.
 * 此接口抽象异步操作的结果。
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully
     * 当且仅当I / O操作成功完成*时，才返回{@code true}
     */
    boolean isSuccess();

    /**
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel(boolean)}.
     * 当且仅当可以通过{@link #cancel（boolean）}取消操作时返回{@code true}。
     */
    boolean isCancellable();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     * 如果I / O操作失败，则返回I / O操作失败的原因的异常。
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not
     *         completed yet.
     * 如果成功或此未来尚未完成，则返回null。
     */
    Throwable cause();

    /**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     *
     * 将指定的监听器添加到此Future中。当这个future {@linkplain #isDone（）done}时，会通知指定的监听器。
     * 如果此future已完成，则立即通知指定的监听器。
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * Adds the specified listeners to this future.  The
     * specified listeners are notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listeners are notified immediately.
     *
     *将指定的多个监听器添加到此Future中。当这个future {@linkplain #isDone（）done}时，会通知指定的多个监听器。
     *如果此future已完成，则立即通知指定的多个监听器。
     *
     */
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * Removes the first occurrence of the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     *
     * 从此future删除第一次出现的指定监听器。
     * 当此future为{@linkplain #isDone（）done}时，不再通知移除的此监听器。
     * 如果指定的监听器未与此future关联，则此方法不执行任何操作并以静默方式返回。
     *
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * Removes the first occurrence for each of the listeners from this future.
     * The specified listeners are no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listeners are not associated with this future, this method
     * does nothing and returns silently.
     *
     *
     * 从此future删除第一次出现的指定监听器集合。
     * 当此future为{@linkplain #isDone（）done}时，不再通知移除的此监听器集合。
     * 如果指定的监听器集合未与此future关联，则此方法不执行任何操作并以静默方式返回。
     */
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);


    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * 等待这个future，直到它完成，如果这个future失败，重新抛出失败的原因。
     */
    Future<V> sync() throws InterruptedException;

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * 不可中断的等待这个future，直到它完成，如果这个future失败，重新抛出失败的原因。
     */
    Future<V> syncUninterruptibly();

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     *
     * 等待此future完成。如果当前线程被中断了则抛中断异常(InterruptedException)
     */
    Future<V> await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     *
     * 等待这个future完成而不能中断。此方法捕获{@link InterruptedException}并以静默方式丢弃它。
     */
    Future<V> awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * 在指定时间内等待此future完成，如果此future在指定时间内完成了则返回true
     * 如果当前线程被中断，则返回中断异常（InterruptedException）。
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * 在指定毫秒时间内等待此future完成，如果此future在指定时间内完成了则返回true
     * 如果当前线程被中断，则返回中断异常（InterruptedException）。
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * 等待此future在指定的时间限制内完成，不会中断。
     * 此方法捕获* {@link InterruptedException}并以静默方式丢弃它。
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     * 当且仅当future在指定的时限内完成时返回true
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     *
     * 等待此future在指定的毫秒时间限制内完成，不会中断。
     * 此方法捕获* {@link InterruptedException}并以静默方式丢弃它。
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     *当且仅当future在指定的时限内完成时返回true
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * Return the result without blocking. If the future is not done yet this will return {@code null}.
     *
     * 无阻塞地返回结果。如果future尚未完成，则返回{@code null}。
     *
     * As it is possible that a {@code null} value is used to mark the future as successful you also need to check
     * if the future is really done with {@link #isDone()} and not relay on the returned {@code null} value.
     *
     * 由于有可能使用null值将future标记为成功，
     * 因此您还需要检查future是否真的使用{@link #isDone（）}并且不继续返回null值
     */
    V getNow();

    /**
     * {@inheritDoc}
     *
     * If the cancellation was successful it will fail the future with an {@link CancellationException}.
     *
     * 如果取消成功，则future不抛CancellationException异常。
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}
