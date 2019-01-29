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

/**
 * 事件执行器接口
 *
 * The {@link EventExecutor} is a special {@link EventExecutorGroup} which comes
 * with some handy methods to see if a {@link Thread} is executed in a event loop.
 * Besides this, it also extends the {@link EventExecutorGroup} to allow for a generic
 * way to access methods.
 *
 * EventExecutor 是一个带有一些方便的方法来查看是否在事件循环中执行特殊的EventExecutorGroup
 * 基于此它扩展了EventExecutorGroup 来允许通用的方式来访问这些方法。
 *
 * 概述：
 *   它继承了EventExecutorGroup类，因此也可以将它堪称是一个任务的执行器，不过稍微有点不同的是它的next方法返回的是自己的一个引用，
 *
 *   说到Executor，很容易联想到jdk中 java.util.concurrent.Executor 接口，这个接口非常简单，就一个方法void execute(Runnable command);从方法签名上就能看出这个是为了支持异步模式的。command表示一个命令。
 *   当前线程就是命令者角色，Executor内部的去运行Runnable的线程就是执行者。这里没有提供明确的地方供命令者去取得命令的执行结果。
 *
 *   ExecutorService 继承了Executor 接口，增加了对自身生命周期管理的方法，同时提供了一个Future给命令者去获取命令的执行结果。
 *
 *   ScheduledExecutorService 继承了ExecutorService接口，增加了对定时任务的支持。
 *
 *   EventExecutorGroup 继承了ScheduledExecutorService接口，对原来的ExecutorService的关闭接口提供了增强，提供了优雅的关闭接口。从接口名称上可以看出它是对多个EventExecutor的集合，提供了对多个EventExecutor的迭代访问接口。
 *
 *   EventExecutor 继承EventExecutorGroup 看着这个关系真心有些纠结啊。不过细想下还是能理解的。
 *   A是B中的一员，但是A也能迭代访问B中的其他成员。这个继承关系支持了迭代访问这个行为。自然的他提供了一个parent接口，
 *   来获取所属的EventExecutorGroup 。另外提供了inEventLoop 方法支持查询某个线程是否在EventExecutor所管理的线程中。
 *   还有其他一些创建Promise和Future的方法。
 *
 *
 * 在Netty中，netty对线程模型进行了重新封装，它们分别是EventExecutorGroup和EventExecutor
 * 每个EventExecutor是一个单独的线程，可以执行Runnable任务。
 * EventExecutorGroup相当于一个线程组，用于管理和分配一组EventExecutor.
 * Netty是基于事件驱动的。这样的封装给我们的开发带来了非常好的顺序。而且它还封装了定时任务，更加方便我们在一个线程中执行一些定时任务。
 *
 * {@link EventExecutor}是一个特殊的{@link EventExecutorGroup}，它提供了一些方便的方法来查看{@link线程}是否在事件循环中执行。
 * 除此之外，它还扩展了{@link EventExecutorGroup}，以便提供一种访问方法的通用方法。

 */
public interface EventExecutor extends EventExecutorGroup {

    /**
     * 返回自身对象的一个引用。
     *
     * Returns a reference to itself.
     */
    @Override
    EventExecutor next();

    /**
     * 返回持有这个Executor的EventExecutorGroup
     *
     * Return the {@link EventExecutorGroup} which is the parent of this {@link EventExecutor},
     * 所属 EventExecutorGroup.
     * 返回{@link EventExecutorGroup}，它是这个{@link EventExecutor}的父元素
     */
    EventExecutorGroup parent();

    /**
     * 如果当前线程是这个Executor返回true
     *
     * 调用以{@link Thread#currentThread()}为参数的{@link #inEventLoop(Thread)}
     * Calls {@link #inEventLoop(Thread)} with {@link Thread#currentThread()} as argument
     */
    boolean inEventLoop();

    /**
     * 指定线程是否是 EventLoop 线程
     * 如果给定的{@link线程}在事件循环中执行，则返回{@code true}，否则返回{@code false}。
     * Return {@code true} if the given {@link Thread} is executed in the event loop,
     * {@code false} otherwise.
     */
    boolean inEventLoop(Thread thread);

    /**
     * 创建一个 Promise 对象
     * 返回一个新的{@link Promise}.可写的{@link Future}
     * Return a new {@link Promise}.
     */
    <V> Promise<V> newPromise();

    /**
     * 创建一个 ProgressivePromise 对象
     * 返回一个新的{@link ProgressivePromise}.可写的用于指示操作进度的{@link Future}。
     * Create a new {@link ProgressivePromise}.
     */
    <V> ProgressivePromise<V> newProgressivePromise();

    /**
     * 创建成功结果的 Future 对象
     *
     * Create a new {@link Future} which is marked as succeeded already. So {@link Future#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     * 创建一个新的{@link Future}，标记为已成功。因此{@link Future#isSuccess()}将返回{@code true}。所有添加到它的{@link FutureListener}将被直接通知。
     * 而且阻塞方法的每个调用都将返回，而不会阻塞。
     */
    <V> Future<V> newSucceededFuture(V result);

    /**
     * 创建失败结果的 Future 对象
     *
     * Create a new {@link Future} which is marked as failed already. So {@link Future#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     * 创建一个新的{@link Future}，它已经被标记为失败。因此{@link Future#isSuccess()}将返回{@code false}。所有添加到它的{@link FutureListener}将被直接通知。
     * 而且阻塞方法的每个调用都将返回，而不会阻塞
     */
    <V> Future<V> newFailedFuture(Throwable cause);

}
