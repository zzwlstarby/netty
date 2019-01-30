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
 * 概述：
 *      首先它继承了ScheduleEcecutorService ，因此可以把它看成是一个Task的执行器，另外又实现了Iterable接口，
 *      就可以看出它的目的也可以当成一个容器（EventExecutor的容器）。方法也都比较基础，无非就是一些提交任务，
 *      取出可用的EventExecutor，关闭执行器等等。。。
 *
 *      每个EventExecutor是一个单独的线程，可以执行Runnable任务。EventExecutorGroup相当于一个线程组，用于
 *      管理和分配一组EventExecutor.我们知道Netty是基于事件驱动的。这样的封装给我们的开发带来了非常好的顺序。
 *      而且它还封装了定时任务，更加方便我们在一个线程中执行一些定时任务。
 *
 *      EventExecutorGroup接口继承了Java并发包的ScheduledExecutorService接口，覆盖了原接口的方法，
 *      主要区别在于返回值换成了Netty自身的Future实现，另外新添加了几个方法。
 *
 *      该接口继承了ScheduledExecutorService接口和Iterable接口,
 *      对原来的ExecutorService的关闭接口提供了增强,提供了优雅的关闭接口。从接口名称上可以看出它是对多个
 *      EventExecutor的集合,提供了对多个EventExecutor的迭代访问接口。 (起始就是一个EventExecutor的容器)
 *
 *      所谓的优雅的关闭看下面几个方法的介绍
 *       boolean isShuttingDown():只有当这个EventExecutorGroup容器中所有的EventExecutor,调用shutdownGracefully
 *       优雅关闭接口或者shut down后,才会返回true
 *
 *       netty中默认实现了一个DefaultEventExecutorGroup和DefaultEventExecutor。DefaultEventexecutorGroup继承
 *       于MultithreadEventExecutorGroup，MultilthreadEventExecutorGroup是线程管理的核心类。它有一系统的构造
 *       方法，最终执行的构造方法是：

 *
 * The {@link EventExecutorGroup} is responsible for providing the {@link EventExecutor}'s to use
 * via its {@link #next()} method. Besides this, it is also responsible for handling their
 * life-cycle and allows shutting them down in a global fashion.
 *
 * EventExecutorGroup通过next()方法负责提供返回一个EventExecutor（事件执行器），
 * 基于这个。它也负责管理EventExecutor（事件执行器）的生命周期，允许以一个全局的方式关闭它们。
 */

/**
 * EventExecutorGroup 集成了jdk的ScheduledExecutorService接口来计划任务实现线程池。
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * 检查是否已经调用了shutdownGracefully或shutdown方法。
     *
     * Returns {@code true} if and only if all {@link EventExecutor}s managed by this {@link EventExecutorGroup}
     * are being {@linkplain #shutdownGracefully() shut down gracefully} or was {@linkplain #isShutdown() shut down}.
     *
     * 如果仅仅如果所有被管理的EventExecutor（事件执行器）都被优雅的关闭了，或者是调用方法isShutdown（）关闭的。则返回true
     *
     */
    boolean isShuttingDown();

    /**
     *
     *
     *
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     *
     * 快捷的一个通过默认值来实现的正常关闭事件执行器的方法，
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully();

    /**
     *优雅地关闭这个executor, 一旦这个方法被调用，isShuttingDown()方法总是总是返回true。和 shutdown方法不同，
     * 这个方法需要确保在关闭的平静期(由quietPeriod参数决定)没有新的任务被提交，如果平静期有新任务提交，它会接受这个任务，
     * 同时中止关闭动作，等任务执行完毕后从新开始关闭流程。

     *
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
     * 取出一个EventExecutor, 这个方法要实现派发任务的策略。
     *
     * Returns one of the {@link EventExecutor}s managed by this {@link EventExecutorGroup}.
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
