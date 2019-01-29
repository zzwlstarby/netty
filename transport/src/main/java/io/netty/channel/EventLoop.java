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

/**
 * 概述：
 *     该接口同时继承了EventExecutor和EventLoopGroup，因此它既是一个执行器，又是容器。。。
 *
 *     Netty中，实现自己业务逻辑的时候不能阻塞当前线程， 虽然Netty是多线程，但是在高并发的情况下仍然会有问题。因为总的线程数是有限的，
 *     每阻塞一个就会减弱并发性。但是Netty提供了一种比较好的方式来解决此问题：当我们向ChannelPipeline中添加Handler的时候，
 *     你可以指定一个EventExecutorGroup，这个EventExecutorGroup可以提供一个 EventExecutor，
 *     而这个 EventExecutor将用于执行该handler中所有的方法， EventExecutor将会使用一个单独的线程来处理，
 *     这样就将EventLoop给解放出来了。
 *
 */
package io.netty.channel;

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * Will handle all the I/O operations for a {@link Channel} once registered.
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 *
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {

    @Override
    EventLoopGroup parent();

}