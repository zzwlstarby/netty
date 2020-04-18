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
 * 1.阅读netty的源码，首先从最为核心的、也是最为基础的EventLoop系列类入手。EventLoop 系列类，
 *   就像netty这座大厦的钢筋混凝土框架，是非常重要的基础设施。弄清楚EventLoop 的原理，
 *   是研读和学习netty架构的前提。
 * 2.EventLoop 不是Netty中的一个类，而是一系列的类，或者说一组类。这一组类的作用，
 *   对应于Reactor模式的Reactor 角色。
 *
 * 3.channel和selector属于 java.nio 包中的类，分别为网络通信中的通道（连接）和选择器。
 *   Reactor和 handler 属于Reactor模型高性能编程中的应用程序角色，分别为反应器和处理器。
 *
 * 4.从开发或者执行流程上，Reactor模式可以被清晰的被分成三大步：注册、轮询、分发。
 *   第一步：注册
 *      将channel 通道的就绪事件，注册到选择器Selector。在文章《基础篇：netty源码  死磕3-传说中神一样的Reactor反应器模式》的例子中，
 *      这块注册的代码，放在Reactor的构造函数中完成。一般来说，一个Reactor 对应一个选择器Selector，一个Reactor拥有一个Selector成员属性。
 *      对于Java NIO而言，第一步首先是channel到 seletor的事件就绪状态的注册。对于Netty而言，也是类似的。
 *      在此之前，Netty有一些启动的工作需要完成。这些启动的工作，包含了EventLoop、Channel的创建。 这块BootStrap 的启动类和系列启动工作，后面有文章专门介绍。
 *      下面假定启动工作已经完成和就绪，开始进行管道的注册。
 *
 *      4.1Netty中Channel注册流程总览
 *      Channel向EventLoop注册的过程，是在启动时进行的。注册的入口代码，在启动类AbstractBootstrap.initAndRegister 方法中。
 *
 *
 *   第二步：轮询
 *       轮询的代码，是Reactor重要的一个组成部分，或者说核心的部分。轮询选择器是否有就绪事件。
 *       前面讲到，Netty中，一个 NioEventLoop 本质上是和一个特定的线程绑定, 这个线程保存在EvnentLoop的父类属性中。
 *       在EvnentLoop的父类SingleThreadEventExecutor 中，有一个 Thread thread 属性, 存储了一个本地 Java 线程。
 *       线程在哪里启动的呢？
 *       细心的你，有可能在前面已经发现了。
 *       在前面的倒数第三步的注册中，函数 AbstractChannel.AbstractUnsafe.register中，有一个eventLoop.execute（）方法调用，这个调用，就是启动EvnentLoop的本地线程的的入口。
 *
 *       事件的轮询，在NioEventLoop.run() 方法
 *
 *
     第三步：分发
 *   将就绪事件，分发到事件附件的处理器handler中，由handler完成实际的处理。
 *   总体上，Netty是基于Reactor模式实现的，对于就绪事件的处理总的流程，基本上就是上面的三步。
 *
 *   Reactor三步曲之分派
 *   在AbstractNioByteChannel 中，可以找到 unsafe.read( ) 调用的实现代码。 unsafe.read( )负责的是 Channel 的底层数据的 IO 读取，并且将读取的结果，dispatch（分派）给最终的Handler。
 *
 *
 *
 * 5.Netty中的Channel系列类型，对应于经典Reactor模型中的client， 封装了用户的通讯连接。
 *   Netty中的EventLoop系列类型，对应于经典Reactor模型中的Reactor，完成Channel的注册、轮询、分发。
 *   Netty中的Handler系列类型，对应于经典Reactor模型中的Handler，不过Netty中的Handler设计得更加的高级和巧妙，
 *   使用了Pipeline模式。这块非常精彩，后面专门开文章介绍。
 *
 *   总之，基本上一一对应。所以，如果熟悉经典的Reactor模式，学习Netty，会比较轻松。
 *
 * 概述：
 *     该接口同时继承了EventExecutor和EventLoopGroup，因此它既是一个执行器，又是容器。。。
 *
 *     Netty中，实现自己业务逻辑的时候不能阻塞当前线程， 虽然Netty是多线程，但是在高并发的情况下仍然会有问题。因为总的线程数是有限的，
 *     每阻塞一个就会减弱并发性。但是Netty提供了一种比较好的方式来解决此问题：当我们向ChannelPipeline中添加Handler的时候，
 *     你可以指定一个EventExecutorGroup，这个EventExecutorGroup可以提供一个 EventExecutor，
 *     而这个 EventExecutor将用于执行该handler中所有的方法， EventExecutor将会使用一个单独的线程来处理，
 *     这样就将EventLoop给解放出来了。
 *
 *
 *
 *
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