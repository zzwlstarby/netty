/*
 * Copyright 2012 The Netty Project
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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * EventExecutor ( 事件执行器 )的分组接口
 *
 * The {@link EventExecutorGroup} is responsible for providing the {@link EventExecutor}'s to use
 * via its {@link #next()} method. Besides this, it is also responsible for handling their
 * life-cycle and allows shutting them down in a global fashion.
 *
 * EventExecutorGroup通过next()方法负责提供返回一个EventExecutor（事件执行器），
 * 基于这个。它也负责管理EventExecutor（事件执行器）的生命周期，允许以一个全局的方式关闭它们。
 *
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * Returns {@code true} if and only if all {@link EventExecutor}s managed by this {@link EventExecutorGroup}
     * are being {@linkplain #shutdownGracefully() shut down gracefully} or was {@linkplain #isShutdown() shut down}.
     *
     * 如果仅仅如果所有被管理的EventExecutor（事件执行器）都被优雅的关闭了，或者是调用方法isShutdown（）关闭的。则返回true
     *
     */
    boolean isShuttingDown();

    /**
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     *
     * 快捷的一个通过默认值来实现的正常关闭事件执行器的方法，
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully();

    /**
     * Signals this executor that the caller wants the executor to be shut down.  Once this method is called,
     * {@link #isShuttingDown()} starts to return {@code true}, and the executor prepares to shut itself down.
     * Unlike {@link #shutdown()}, graceful shutdown ensures that no tasks are submitted for <i>'the quiet period'</i>
     * (usually a couple seconds) before it shuts itself down.  If a task is submitted during the quiet period,
     * it is guaranteed to be accepted and the quiet period will start over.
     *
     *  若调用者希望关闭此执行器，则通过此方法发信号给执行器。一旦这个方法被调用。则再调用isShuttingDown（）时就开始返回true，并且执行器准备关闭自己。
     *  与shutdown()不同，正常关闭（此方法）能够确保正常关闭期间（通常是几秒钟）没有新的任务被提交。如果一个新的任务再此期间被提交了，
     *  它确保了这个新的任务被接受，然后重新开始关闭期。
     *
     *
     * @param quietPeriod the quiet period as described in the documentation
     *
     * @param timeout     the maximum amount of time to wait until the executor is {@linkplain #shutdown()}
     *                    regardless if a task was submitted during the quiet period
     *                    执行器被关闭的最大超时时间，无论再关闭期是否有新的任务被提交。
     * @param unit        the unit of {@code quietPeriod} and {@code timeout}
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * Returns the {@link Future} which is notified when all {@link EventExecutor}s managed by this
     * {@link EventExecutorGroup} have been terminated.
     *
     * 当此{@link EventExecutorGroup}管理的所有{@link EventExecutor}终止时，将通知{@link Future}，并返回此future
     */
    Future<?> terminationFuture();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     *
     * 此方法弃用 ，用shutdownGracefully 代替。
     */
    @Override
    @Deprecated
    void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     *
     * 此方法废弃，用shutdownGracefully代替。
     */
    @Override
    @Deprecated
    List<Runnable> shutdownNow();

    /**
     * Returns one of the {@link EventExecutor}s managed by this {@link EventExecutorGroup}.
     *
     * 返回被EventExecutorGroup管理的EventExecutor集合的下一个。
     */
    EventExecutor next();

    /**
     * 返回EventExecutor迭代器。
     * @return
     */
    @Override
    Iterator<EventExecutor> iterator();

    /**
     * 提交任务，并返回future
     * @param task
     * @return
     */
    @Override
    Future<?> submit(Runnable task);

    /**
     * 能通过传入的载体result间接获得线程的返回值或者准确来说交给线程处理一下
     * @param task
     * @param result
     * @param <T>
     * @return
     */
    @Override
    <T> Future<T> submit(Runnable task, T result);

    /**
     * 提交任务。
     * @param task
     * @param <T>
     * @return
     */
    @Override
    <T> Future<T> submit(Callable<T> task);

    /**
     * 提交计划任务
     * @param command
     * @param delay
     * @param unit
     * @return
     */
    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
