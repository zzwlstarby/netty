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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.nio.channels.Channels;

/**
 * Enables a {@link ChannelHandler} to interact with its {@link ChannelPipeline}
 * and other handlers. Among other things a handler can notify the next {@link ChannelHandler} in the
 * {@link ChannelPipeline} as well as modify the {@link ChannelPipeline} it belongs to dynamically.
 *
 * <h3>Notify</h3>
 *
 * You can notify the closest handler in the same {@link ChannelPipeline} by calling one of the various methods
 * provided here.
 *
 * Please refer to {@link ChannelPipeline} to understand how an event flows.
 *
 * <h3>Modifying a pipeline</h3>
 *
 * You can get the {@link ChannelPipeline} your handler belongs to by calling
 * {@link #pipeline()}.  A non-trivial application could insert, remove, or
 * replace handlers in the pipeline dynamically at runtime.
 *
 * <h3>Retrieving for later use</h3>
 *
 * You can keep the {@link ChannelHandlerContext} for later use, such as
 * triggering an event outside the handler methods, even from a different thread.
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>Storing stateful information</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a handler and its
 * context.  Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * <h3>A handler can have more than one context</h3>
 *
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * the single instance can be invoked with different
 * {@link ChannelHandlerContext}s if it is added to one or more
 * {@link ChannelPipeline}s more than once.
 * <p>
 * For example, the following handler will have as many independent {@link AttributeKey}s
 * as how many times it is added to pipelines, regardless if it is added to the
 * same pipeline multiple times or added to different pipelines multiple times:
 * <pre>
 * public class FactorialHandler extends {@link ChannelInboundHandlerAdapter} {
 *
 *   private final {@link AttributeKey}&lt;{@link Integer}&gt; counter = {@link AttributeKey}.valueOf("counter");
 *
 *   // This handler will receive a sequence of increasing integers starting
 *   // from 1.
 *   {@code @Override}
 *   public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     Integer a = ctx.attr(counter).get();
 *
 *     if (a == null) {
 *       a = 1;
 *     }
 *
 *     attr.set(a * (Integer) msg);
 *   }
 * }
 *
 * // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 * // they refer to the same handler instance.  Because the FactorialHandler
 * // stores its state in a context object (using an {@link AttributeKey}), the factorial is
 * // calculated correctly 4 times once the two pipelines (p1 and p2) are active.
 * FactorialHandler fh = new FactorialHandler();
 *
 * {@link ChannelPipeline} p1 = {@link Channels}.pipeline();
 * p1.addLast("f1", fh);
 * p1.addLast("f2", fh);
 *
 * {@link ChannelPipeline} p2 = {@link Channels}.pipeline();
 * p2.addLast("f3", fh);
 * p2.addLast("f4", fh);
 * </pre>
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */

/**
 * 概述：
 *      ChannelHandlerContext的主要功能是使ChannelHandler能够与所属ChannelPipeline中的其他ChannelHandler
 *      进行交互，同时，使ChannelHandler可以在上游或下游传递事件，执行I/O操作，动态修改流水线或使用AttributeKeys
 *      存储ChannelHandler特有的信息等等。
 *
 *      事件处理器上下文，pipeline中的实际处理节点。每个处理节点ChannelHandlerContext中包含一个具体的事件处理器
 *      ChannelHandler，同时ChannelHandlerContext中也绑定了对应的pipeline和Channel的信息，方便ChannelHandler
 *      进行调用。
 *
 *      ChannelHandlerContext接口继承了ChannelInboundInvoker，其实现类AbstractChannelhandlerContext因此具有
 *      发起inbound事件的功能。 -->  实际通过调用AbstractChannelHandlerContext的静态invokeXXX()方法，从当前节
 *      点的下一个节点开始接收事件。

 *      对于inbound事件，因为需要进行业务逻辑处理，因此pipeline的head节点会执行fireXXX()方法将事件透传给后面的用户自
 *      己实现inbound处理节点，由用户自己实现的ChannelHandler接收事件并回调执行业务逻辑。
 *
 *  1.Handler的上下文环境
 *  在Netty的设计中，Handler是无状态的，不保存和Channel有关的信息。打个不恰当的比方，Handler就像国际雇佣军一样，谁给钱，给谁打仗。
 *  Handler的目标，是将自己的处理逻辑做得很完成，可以给不同的Channel使用。
 *  与之不同的是，Pipeline是有状态的，保存了Channel的关系。
 *  于是乎，Handler和Pipeline之间，需要一个中间角色，把他们联系起来。这个中间角色是谁呢？
 *  它就是——ChannelHandlerContext 。
 *
 * 2.所以，ChannelPipeline 中维护的，是一个由 ChannelHandlerContext 组成的双向链表。这个链表的头是 HeadContext, 链表的尾是 TailContext。
 *  而无状态的Handler，作为Context的成员，关联在ChannelHandlerContext 中。
 *  在对应关系上，每个 ChannelHandlerContext 中仅仅关联着一个 ChannelHandler(即 ChannelHandlerContext和ChannelHandler是一对一的关系)。
 *
 *  Context的双向链表的主要代码，在 AbstractChannelHandlerContext类中。该类主要包含一个双向链表节点的前置和后置节点引用 prev、next，
 *  以及数据引用 handler，相当于链表数据结构中的 Node 节点。
 *
 *  无论是哪种类型的handler，Pipeline没有单独和分开的入站和出站链表，都是统一在一个双向链表中进行管理。
 *
 *  赘述一下:
 *  Tail是出站执行流程的启动点，但是，它最后一个入站处理器。
 *  Hearder，是入站流程的启动起点，但是，它最后一个出站处理器。
 *  感觉，有点儿饶。容易让人混淆。看完整个的入站流程和出站流程的详细介绍，就清楚了。
 *
 *
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * Return the {@link Channel} which is bound to the {@link ChannelHandlerContext}.
     */
    Channel channel();

    /**
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * The unique name of the {@link ChannelHandlerContext}.The name was used when then {@link ChannelHandler}
     * was added to the {@link ChannelPipeline}. This name can also be used to access the registered
     * {@link ChannelHandler} from the {@link ChannelPipeline}.
     */
    String name();

    /**
     * The {@link ChannelHandler} that is bound this {@link ChannelHandlerContext}.
     */
    ChannelHandler handler();

    /**
     * Return {@code true} if the {@link ChannelHandler} which belongs to this context was removed
     * from the {@link ChannelPipeline}. Note that this method is only meant to be called from with in the
     * {@link EventLoop}.
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * Return the assigned {@link ChannelPipeline}
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
