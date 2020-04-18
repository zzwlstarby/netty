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
package io.netty.channel;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in
 * its {@link ChannelPipeline}.
 *
 * <h3>Sub-types</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide many methods, but you usually have to implement one of its subtypes:
 * <ul>
 * <li>{@link ChannelInboundHandler} to handle inbound I/O events, and</li>
 * <li>{@link ChannelOutboundHandler} to handle outbound I/O operations.</li>
 * </ul>
 * </p>
 * <p>
 * Alternatively, the following adapter classes are provided for your convenience:
 * <ul>
 * <li>{@link ChannelInboundHandlerAdapter} to handle inbound I/O events,</li>
 * <li>{@link ChannelOutboundHandlerAdapter} to handle outbound I/O operations, and</li>
 * <li>{@link ChannelDuplexHandler} to handle both inbound and outbound events</li>
 * </ul>
 * </p>
 * <p>
 * For more information, please refer to the documentation of each subtype.
 * </p>
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (using {@link AttributeKey}s) which is specific to the handler.
 *
 * <h3>State management</h3>
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void channelRead0({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Channel} ch = e.getChannel();
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where a unauthenticated client can get
 * the confidential information:
 * <pre>
 * // Create a new handler instance per channel.
 * // See {@link ChannelInitializer#initChannel(Channel)}.
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>Using {@link AttributeKey}s</h4>
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use {@link AttributeKey}s which is provided by
 * {@link ChannelHandlerContext}:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         {@link Channel} ch = ctx.channel();
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is attached to the {@link ChannelHandlerContext}, you can add the
 * same handler instance to different pipelines:
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * <p>
 * In the example above which used an {@link AttributeKey},
 * you might have noticed the {@code @Sharable} annotation.
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 *
 * 概述：
 *      该组件实现了服务器对从客户端接收的数据的处理，即它的业务逻辑
 *      它是一个接口族的父接口，它的实现负责接收并响应事件通知。
 *      在 Netty 应用程序中，所有的数据处理逻辑都包含在这些核心抽象的实现中。
 *      服务器会响应传入的消息，所以它需要实现 ChannelInboundHandler 接口， 用
 *      来定义响应入站事件的方法。这个简单的应用程序只需要用到少量的这些方法，所以继承 ChannelInboundHandlerAdapter 类也就足够了，
 *      它提供了 ChannelInboundHandler 的默认实现
 *
 *      针对不同类型的事件来调用 ChannelHandler
 *      应用程序通过实现或者扩展 ChannelHandler 来挂钩到事件的生命周期，并且提供自定义的应用程序逻辑；
 *      在架构上， ChannelHandler 有助于保持业务逻辑与网络处理代码的分离。这简化了开发过程，因为代码必须不断地演化以响应不断变化的需求。
 *
 *      ChannelHandler可以看作是用于处理在ChannelPipeline中传输的数据的工具。但它们的作用远不止这些。
 *
 * 1.Reactor模式是Netty的基础和灵魂，掌握了经典的Reactor模式实现，彻底掌握Netty就事半功倍了。《Reactor模式（netty源码死磕3）》对Reactor模式的经典实现，
 * 进行了详细介绍。作为本文的阅读准备，可以去温习一下。
 * Reactor模式的两个重要的组件，一个是Reactor反应器，在Netty中的对应的实现是EventLoop，在文章《EventLoop（netty源码死磕4）》中，已经有了非常详细的介绍。
 * 此文聚焦于Reactor模式的另一个重要的组成部分Handler。
 *
 * 2.Handler在经典Reactor中的角色
 *  在Reactor经典模型中，Reactor查询到NIO就绪的事件后，分发到Handler，由Handler完成NIO操作和计算的操作。
 *  Handler主要的操作为Channel缓存读、数据解码、业务处理、写Channel缓存，然后由Channel（代表client）发送到最终的连接终端。
 *
 *  Handler在Netty中的坐标
 *  经典的Reactor模式，更多在于演示和说明，仅仅是有一种浓缩和抽象。
 *  由于Netty更多用于生产，在实际开发中的业务处理这块，主要通过Handler来实现，所以Netty中在Handler的组织设计这块，远远比经典的Reactor模式实现，要纷繁复杂得多。
 *
 *  一个Netty Channel对应于一个Client连接，内部封装了一个Java NIO SelectableChannel 可查询通道。
 *  再回到Handler。
 *  Hander的根本使命，就是处理Channel的就绪事件，根据就绪事件，完成NIO处理和业务操作。比方Channel读就绪时，
 *  Hander就开始读；Channel写就绪时，Hander就开始写。
 *
 *3.Netty中Handler的类型
 *  从应用程序开发人员的角度来看，Netty的主要组件是ChannelHandler，所以，对ChannelHandler的分类，也是从应用开发的角度来的。
 *  ChannelHandler -> ChannelInboundHandler
 *  ChannelHandler -> ChannelOutboundHandler
 *
 *  从应用程序开发人员的角度来看，数据有入站和出站两种类型。
 *  这里的出站和入站，不是网络通信方面的入站和出站。而是相对于Netty Channel与Java NIO Channel而言的。
 *  数据入站，指的是数据从底层的Java NIO channel到Netty的Channel。数据出站，指的是通过Netty的Channel来操作底层的 Java NIO chanel。
 *  从入站和出战的角度出发，Netty中的ChannelHandler主要由两种类型，ChannelInboundHandler和ChannelOutboundHandler。
 *
 * 3.Handler和Channel的关系
 *  打个比方，如果Hander是太阳系的行星，那么Channel就是太阳系的恒星。Hander的服务对象和公转的轴心，就是Channel。
 *  这可能是最为不恰当的一个比方，但是说的是事实。
 */
public interface ChannelHandler {

    /**
     * Gets called after the {@link ChannelHandler} was added to the actual context and it's ready to handle events.
     *
     * ChannelHandler 已经成功被添加到 ChannelPipeline 中，可以进行处理事件。
     *
     * 该方法，一般用于 ChannelHandler 的初始化的逻辑
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     *
     * ChannelHandler 已经成功从 ChannelPipeline 中被移除，不再进行处理事件。
     *
     * 该方法，一般用于 ChannelHandler 的销毁的逻辑
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     *
     * 抓取到异常。目前被废弃，移到 ChannelInboundHandler 接口中，作为对 Exception Inbound 事件的处理
     *
     * @deprecated is part of {@link ChannelInboundHandler}
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }

}