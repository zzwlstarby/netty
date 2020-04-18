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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts inbound events and outbound operations of a
 * {@link Channel}.  {@link ChannelPipeline} implements an advanced form of the
 * <a href="http://www.oracle.com/technetwork/java/interceptingfilter-142169.html">Intercepting Filter</a> pattern
 * to give a user full control over how an event is handled and how the {@link ChannelHandler}s in a pipeline
 * interact with each other.
 *
 * <h3>Creation of a pipeline</h3>
 *
 * Each channel has its own pipeline and it is created automatically when a new channel is created.
 *
 * <h3>How an event flows in a pipeline</h3>
 *
 * The following diagram describes how I/O events are processed by {@link ChannelHandler}s in a {@link ChannelPipeline}
 * typically. An I/O event is handled by either a {@link ChannelInboundHandler} or a {@link ChannelOutboundHandler}
 * and be forwarded to its closest handler by calling the event propagation methods defined in
 * {@link ChannelHandlerContext}, such as {@link ChannelHandlerContext#fireChannelRead(Object)} and
 * {@link ChannelHandlerContext#write(Object)}.
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link Channel} or
 *                                        {@link ChannelHandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                           ChannelPipeline         |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ Socket.read() ]                    [ Socket.write() ]     |
 *  |                                                                   |
 *  |  Netty Internal I/O Threads (Transport Implementation)            |
 *  +-------------------------------------------------------------------+
 * </pre>
 * An inbound event is handled by the inbound handlers in the bottom-up direction as shown on the left side of the
 * diagram.  An inbound handler usually handles the inbound data generated by the I/O thread on the bottom of the
 * diagram.  The inbound data is often read from a remote peer via the actual input operation such as
 * {@link SocketChannel#read(ByteBuffer)}.  If an inbound event goes beyond the top inbound handler, it is discarded
 * silently, or logged if it needs your attention.
 * <p>
 * An outbound event is handled by the outbound handler in the top-down direction as shown on the right side of the
 * diagram.  An outbound handler usually generates or transforms the outbound traffic such as write requests.
 * If an outbound event goes beyond the bottom outbound handler, it is handled by an I/O thread associated with the
 * {@link Channel}. The I/O thread often performs the actual output operation such as
 * {@link SocketChannel#write(ByteBuffer)}.
 * <p>
 * For example, let us assume that we created the following pipeline:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Inbound} means it is an inbound handler.
 * The class whose name starts with {@code Outbound} means it is a outbound handler.
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3, 4, 5 when an event goes inbound.
 * When an event goes outbound, the order is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 * <ul>
 * <li>3 and 4 don't implement {@link ChannelInboundHandler}, and therefore the actual evaluation order of an inbound
 *     event will be: 1, 2, and 5.</li>
 * <li>1 and 2 don't implement {@link ChannelOutboundHandler}, and therefore the actual evaluation order of a
 *     outbound event will be: 5, 4, and 3.</li>
 * <li>If 5 implements both {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}, the evaluation order of
 *     an inbound and a outbound event could be 125 and 543 respectively.</li>
 * </ul>
 *
 * <h3>Forwarding an event to the next handler</h3>
 *
 * As you might noticed in the diagram shows, a handler has to invoke the event propagation methods in
 * {@link ChannelHandlerContext} to forward an event to its next handler.  Those methods include:
 * <ul>
 * <li>Inbound event propagation methods:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#fireChannelRegistered()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelActive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelRead(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelReadComplete()}</li>
 *     <li>{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}</li>
 *     <li>{@link ChannelHandlerContext#fireUserEventTriggered(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelWritabilityChanged()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelInactive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelUnregistered()}</li>
 *     </ul>
 * </li>
 * <li>Outbound event propagation methods:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#bind(SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#write(Object, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#flush()}</li>
 *     <li>{@link ChannelHandlerContext#read()}</li>
 *     <li>{@link ChannelHandlerContext#disconnect(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#close(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#deregister(ChannelPromise)}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * and the following example shows how the event propagation is usually done:
 *
 * <pre>
 * public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
 *
 * public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
 *     {@code @Override}
 *     public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
 *         System.out.println("Closing ..");
 *         ctx.close(promise);
 *     }
 * }
 * </pre>
 *
 * <h3>Building a pipeline</h3>
 * <p>
 * A user is supposed to have one or more {@link ChannelHandler}s in a pipeline to receive I/O events (e.g. read) and
 * to request I/O operations (e.g. write and close).  For example, a typical server will have the following handlers
 * in each channel's pipeline, but your mileage may vary depending on the complexity and characteristics of the
 * protocol and business logic:
 *
 * <ol>
 * <li>Protocol Decoder - translates binary data (e.g. {@link ByteBuf}) into a Java object.</li>
 * <li>Protocol Encoder - translates a Java object into binary data.</li>
 * <li>Business Logic Handler - performs the actual business logic (e.g. database access).</li>
 * </ol>
 *
 * and it could be represented as shown in the following example:
 *
 * <pre>
 * static final {@link EventExecutorGroup} group = new {@link DefaultEventExecutorGroup}(16);
 * ...
 *
 * {@link ChannelPipeline} pipeline = ch.pipeline();
 *
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * // Tell the pipeline to run MyBusinessLogicHandler's event handler methods
 * // in a different thread than an I/O thread so that the I/O thread is not blocked by
 * // a time-consuming task.
 * // If your business logic is fully asynchronous or finished very quickly, you don't
 * // need to specify a group.
 * pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a {@link ChannelPipeline} is thread safe.
 * For example, you can insert an encryption handler when sensitive information is about to be exchanged, and remove it
 * after the exchange.
 *
 *
 * 概述：
 *      ChannelPipeline的作用就是合理安排ChannelHandler去工作
 *
 *      在ChannelPipeline中，每一个handler都要负责将Netty事件传递给下一个handler，
 *      为此，Netty提供了*Adapter抽象类来提供这些传递功能，这样我们在使用的时候就可以只实现我们感兴趣的方法（事件）。
 *      除此之外，*Adapter还提供了其他功能，例如：对于消息的编码和解码。
 *      Netty中提供的几个Adapter：
            ChannelHandlerAdapter
 *          ChannelInboundHandlerAdapter
 *          ChannelOutboundHandlerAdapter
 *          ChannelDuplexHandlerAdapter
 *
 *      当EventLoop的selector监听到某Channel产生了就绪的IO事件，并调用socket API对就绪的IO事件进行操作后，需要将操作
 *      产生的“IO数据”或“操作结果”告知用户进行相应的业务处理。
 *      netty将因外部IO事件导致的Channel状态变更（Channel被注册到EventLoop中，Channel状态变为可用，Channel读取到IO
 *      数据...）或Channel内部逻辑操作（添加ChannelHandler...）抽象为不同的回调事件，并定义了pipeline对Channel的回
 *      调事件进行流式的响应处理。
 *      用户可在pipeline中添加多个事件处理器(ChannelHandler)，并通过实现ChannelHandler中定义的方法，对回调事件进行
 *      定制化的业务处理。ChannelHandler也可以调用自身方法对Channel本身进行操作。
 *      netty会保证“回调事件在ChannelHandler之间的流转”及“Channel内部IO操作”由EventLoop线程串行执行，用户也可以在
 *      ChannelHandler中使用自行构建的业务线程进行业务处理。
 *
 *      ChannelPipeline接口继承了ChannelInboundInvoker，其实现类DefaultChannelPipeline因此具有发起inbound事件的功能。
 *      实际通过调用AbstractChannelHandlerContext的静态invokeXXX()方法，产生并从pipeline的head节点开始接收事件。
 *
 *
 * 2. Pipeline介绍：
 *    2.1 管道模型，是一种“链式模型”，用来串接不同的程序或者不同的组件，让它们组成一条直线的工作流。这样给定一个完整的输入，经过各个组件的先后协同处理，得到唯一的最终输出。
      2.2 Pipeline模式应用场景
          简单的说，管道模型的典型应用场景，可以用一个形象的比方，有点类似像富士康那么的工厂生产线。
          管道模型包含两个部分：pipeline 管道、valve 阀门。
          pipeline 管道，可以比作车间生产线，在这里可认为是容器的逻辑处理总线。
          valve 阀门，可以比作生产线上的工人，负责完成各自的部分工作。 阀门也可以叫做Handler 处理者。
      2.3 Tomcat中的Pipeline模式
          在我们非常熟悉的Web容器Tomcat中，一个请求，首先被Connector接受到。然后，会将请求交给Container，Container处理完了之后将结果返回给Connector 。
          Tomcat中，Container包含了Engine、Host、Context、Wrapper几个内部的子容器元素。
          这几个子容器元素的功能，赘述如下：
              Engine：代表一个完整的 Servlet 引擎，可以包含多个Host。它接收来自Connector的请求，并决定传给哪个Host来处理，得到Host处理完的结果后，返回给Connector。
              Host：代表一个虚拟主机，一个Host能运行多个应用，它负责安装和展开这些应用，每个Host对应的一个域名。
              Context：一个Context代表一个运行在Host上的Web应用
              Wrapper: 一个Wrapper 代表一个 Servlet，它负责管理一个 Servlet，包括的 Servlet 的装载、初始化、执行以及资源回收。
          在一个用户请求过来后，Tomcat中的每一级子容器，都对应于一个阀门Valve（注意：这个单词不是value，有一个字母的差别）。Tomcat接受请求之后，请求从被接受，被分发，被处理，到最后转变成http响应，会通过如下的阀门序列。
          这些阀门（Valve）通过invoke（next）方法彼此串联起来，最终构成的执行顺序，构成一个管道。
          Pipeline模式，在设计模式中，属于责任链模式的一种。在Tomcat的Pipeline模式中。从Engine到Host再到Context一直到Wrapper，都是通过同一个责任链，来传递请求。
      2.4 Netty中的Pipeline模式
          一个Channel，拥有一个ChannelPipeline，作为ChannelHandler的容器。但是一个ChannelHandler，不能直接放进Pipeline中，必须包裹一个AbstractChannelHandlerContext 的上下文环境。
          在初始化Netty的Channel时，需要将Handler加载到Pipeline中。
          Pipeline中不直接加入Handler，而是需要进行包裹。例如Decoder、Business、Encoder三个Hander，分别创建三个默认的上下文包裹器(DefaultContext )。DefaultContext 的具体实现类，在Netty中，是DefaultChannelHandlerContext。
           除此之外，Pipeline的头尾，各有一个特别的上下文Context 。这两个Hander Context ，不是默认的DefaultContext 。分别有自己的类型。

          Context的类型
          在Pipeline中头尾，分别各有一个特别的HandlerContext——简称Head和Tail。Head的类型是HeadContext。Tail的类型是TailContext。
          这两种类型，和DefaultChannelHandlerContext类型，都是AbstractChannelHandlerContext的子类。
               Head的作用是什么呢？
                    Head上下文包裹器的主要作用： 主要是作为入站处理的起点。数据从Channel读入之后，一个入站数据包从Channel的事件发送出来，首先从Head开始，被后面的所有的入站处理器，逐个进行入站处理。
                    注意，TailContext，其实也是一个入站处理器。先按下不表，待会详细阐述。

               Tail的作用是什么呢？
                    Tail上下文包裹器的主要作用： 主要是作为出站处理的起点。当所有的入站处理器，都处理完成后，开始出站流程。需要出站的数据包，首先从Tail开始，被所有的出站处理器上下文Context中的Hander逐个进行处理。然后将处理结果，写入Channel中。
                    注意，HeadContext，其实也是一个出站处理器。先按下不表，待会详细阐述

          Tail和Head内部，没有包裹其他的内部Handler成员。这一点，是与默认的上下文包裹器DefaultChannelHandlerContext不同的地方。
          TailContext本身实现了ChannelInboundHandler 接口的方式，可以完成入站处理的操作，作为一个入站处理器使用。
          HeadContext本身，实现了ChannelOutboundHandler 接口的方式，可以完成出站处理的操作，完成最终的出站处理操作。

          Pipeline模式的优点：
          总结一下Pipeline模式的优点，如下：
          1、降低耦合度。它将请求的发送者和接收者解耦。
          2、简化了Handler处理器。使得处理器不需要不需要知道链的结构。也就是Handler处理器可以是无状态的。与责任链（流水线）相关的状态，交给了Context去维护。
          3、增强给对象指派职责的灵活性。通过改变链内的成员或者调动它们的次序，允许动态地新增或者删除责任。
          4、增加新的请求处理器很方便。
 *
 *
 * 1.一个Channel在数量上，肯定不止拥有一个Handler。 如何将杂乱无章的Handler，有序的组织起来呢？来了一个Handler的装配器——Pipeline。
 *  1.1Netty中， 使用一个双向链表，将属于一个Channel的所有Handler组织起来，并且给这个双向链表封装在一个类中，
 *     再给这个类取了一个非常牛逼的名字，叫做ChannelPipeline。
 *  1.2实际上这里用了Java中一种非常重要的设计模式，Pipeline设计模式。后面将用专门的文章，来介绍这种牛逼模式
 *  1.3一个Channel，仅仅一个ChannelPipeline。该pipeline在Channel被创建的时候创建。ChannelPipeline相当于是ChannelHandler的容器，
 *    它包含了一个ChannelHander形成的列表，且所有ChannelHandler都会注册到ChannelPipeline中。
 *
 * 2.Handler的注册
 *  Handler是如何注册到Pipeline中的呢？
 *  2.1 第一步：包裹
 *  加入到Pipeline之前，在Pipeline的基类DefaultChannelPipeline中，首先对Handler进行包裹。
 *  // 使用 AbstractChannelHandlerContext 包裹 ChannelHandler
 *
 * private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
 *     return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
 * }
 *
 * 2.2 加入链表并注册完成回调事件
 *    1. 构建了 AbstractChannelHandlerContext 节点，并加入到了链表尾部。
 *    2. 如果 channel 尚未注册到 EventLoop，就添加一个任务到 PendingHandlerCallback 上，后续channel 注册完毕，再调用 ChannelHandler.handlerAdded。
 *    3. 如果已经注册，马上调用 callHandlerAdded0 方法来执行 ChannelHandler.handlerAdded 注册完成的回调函数。
 * 2.3回调添加完成事件
 * 添加完成后，执行回调方法如下：
 * private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) {
 *         try {
 * ctx.handler().handlerAdded(ctx);
 *             ctx.setAddComplete();
 *         } catch (Throwable t) {
 *            …….
 *     }
 *
 *会执行handler的handlerAdded 方法，这是一个回调方法。添加完成后的回调代码，基本上写在这里。
 *
 *
 * 3.Pipeline的入站流程
 *  在讲解入站处理流程前，先脑补和铺垫一下两个知识点：
 * （1）如何向Pipeline添加一个Handler节点
 * （2）Handler的出站和入站的区分方式
 *
 *  3.1HandlerContext节点的添加
 *  在Pipeline实例创建的同时，Netty为Pipeline创建了一个Head和一个Tail，并且建立好了链接关系。
 *  代码如下：
 *   protected DefaultChannelPipeline(Channel channel) {
 *     this.channel = ObjectUtil.checkNotNull(channel, "channel");
 *     tail = new TailContext(this);
 *     head = new HeadContext(this);
 *     head.next = tail;
 *     tail.prev = head;
 *  }
 *  也就是说，在加入业务Handler之前，Pipeline的内部双向链表不是一个空链表。而新加入的Handler，加入的位置是，插入在链表的倒数第二个位置，在Tail的前面。
 *  加入Handler的代码，在DefaultChannelPipeline类中。
 * 具体的代码如下：
 * @Override
 * public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
 *     final AbstractChannelHandlerContext newCtx;
 *     synchronized (this) {
 *
 *      //检查重复
 *         checkMultiplicity(handler);
 *         //创建上下文
 *         newCtx = newContext(group, filterName(name, handler), handler);
 *          //加入双向链表
 *         addLast0(newCtx);
 *         //…
 *     }
 *     callHandlerAdded0(newCtx);
 *     return this;
 * }
 * 加入之前，首先进行Handler的重复性检查。非共享类型的Handler，只能被添加一次。如果当前要添加的Handler是非共享的，并且已经添加过，那就抛出异常，否则，标识该handler已经添加。
 * 什么是共享类型，什么是非共享类型呢？先聚焦一下主题，后面会详细解答。
 * 检查完成后，给Handler创建包裹上下文Context，然后将Context加入到双向列表的尾部Tail前面。
 * 代码如下：
 * private void addLast0(AbstractChannelHandlerContext newCtx) {
 *     AbstractChannelHandlerContext prev = tail.prev;
 *     newCtx.prev = prev;
 *     newCtx.next = tail;
 *     prev.next = newCtx;
 *     tail.prev = newCtx;
 * }
 * 这里主要是通过调整双向链接的指针，完成节点的插入。如果对双向链表不熟悉，可以自己画画指向变化的草图，就明白了。
 *
 * 3.2 Context的出站和入站的类型
 * 对于入站和出站，Pipeline中两种不同类型的Handler处理器，出站Handler和入站Handler。
 * 入站（inBound）事件Handler的基类是 ChannelInboundHandler，出站（outBound）事件Handler的基类是 ChannelOutboundHandler。
 * 处理入站（inBound）事件，最典型的就是处理Channel读就绪事件，还有就是业务处理Handler。处理出站outBound操作，最为典型的处理，是写数据到Channel。
 * 对应于两种Handler处理器的Context 包裹器，更加需要区分入站和出站。对Context的区分方式，又是什么呢？
 * 首先，需要在Context加了一组boolean类型判断属性，判断出站和入站的类型。这组属性就是——inbound、outbound。
 * 这组属性，定义在上下文包裹器的基类中——ContextAbstractChannelHandlerContext 定义。它们在构造函数中进行初始化。
 *
 *
 * 3.3. 入站操作的全流程
 *  *   入站事件前面已经讲过，流向是从Java 底层IO到ChannelHandler。入站事件的类型包括连接建立和断开、读就绪、写就绪等。
 *  *   基本上，，在处理流程上，大部分的入站事件的处理过程，是一致的。
 *  *   通用的入站Inbound事件处理过程，大致如下（使用IN_EVT符号代替一个通用事件）:
 *  *   （1）pipeline.fireIN_EVT
 *  *   （2）AbstractChannelHandlerContext.invokeIN_EVT(head, msg);
 *  *   （3）context.invokeIN_EVT(msg);
 *  *   （4）handler.IN_EVT
 *  *   （5）context.fireIN_EVT(msg);
 *  *   （6）Connect.findContextInbound()
 *  *   （7）context.invokeIN_EVT(msg);
 *  *
 *  * 3.4.入站源头的Java底层 NIO封装
 *  *   入站事件处理的源头，在Channel的底层Java NIO 就绪事件。
 *  *   Netty对底层Java NIO的操作类，进行了封装，封装成了Unsafe系列的类。比方说，AbstractNioByteChannel 中，就有一个NioByteUnsafe 类，封装了底层的Java NIO的底层Byte字节的读取操作。
 *  *   为什么叫Unsafe呢？
 *  *   很简单，就是在外部使用，是不安全的。Unsafe就是只能在Channel内部使用的，在Netty 外部的应用开发中，不建议使用。Unsafe包装了底层的数据读取工作，包装在Channel中，不需要应用程序关心。应用程序只需要从缓存中，取出缓存数据，完成业务处理即可。
 *  *   Channel 读取数据到缓存后，下一步就是调用Pipeline的fireChannelRead（）方法，从这个点开始，正式开始了Handler的入站处理流程。
 *  *
 *  * 3.5. Head是入站流程的起点
 *  *    前面分析到，Pipeline中，入站事件处理流程的处理到的第一个Context是Head。
 *  *    这一点，从DefaultChannelPipeline 源码可以得到验证，如下所示：
 *  *    public class DefaultChannelPipeline implements ChannelPipeline
 *  * {
 *  * …
 *  * @Override
 *  * public final ChannelPipeline fireChannelRead(Object msg) {
 *  * AbstractChannelHandlerContext.invokeChannelRead(head, msg);
 *  *     return this;
 *  * }
 *  * …
 *  * }
 *  *
 *  *    Pipeline将内部链表的head头作为参数，传入了invokeChannelRead的静态方法中。
 *  *   就像开动了流水线的开关，开启了整个的流水线的循环处理。
 *  *
 *  *  3.6.小迭代的五个动作
 *  *    一个Pipeline上有多个InBound Handler，每一个InBound Handler的处理，可以算做一次迭代，也可以说成小迭代。
 *  *    每一个迭代，有四个动作。这个invokeIN_EVT方法，是整个四个动作的小迭代的起点。
 *  *   四个动作，分别如下：
 *  * （1）invokeChannelRead(next, msg)
 *  * （2）context.invokeIN_EVT(msg);
 *  * （3）handler.IN_EVT
 *  * （4）context.fireIN_EVT(msg);
 *  * （5）Connect.findContextInbound()
 *  *
 *  * 整个五个动作中，只有第三步在Handler中，其他的四步都在Context中完成。
 *  *
 *  *   3.6.1流水线小迭代的第一步
 *  *   invokeChannelRead（next，msg） 静态方法，非常关键，其重要作为是：作为流水线迭代处理的每一轮循环小迭代的第一步。在Context的抽象基类中，源码如下：
 *  *   abstract class AbstractChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext
 *  * {
 *  * //...
 *  * static void invokeChannelRead(final AbstractChannelHandlerContext next, final Object msg) {
 *  *   ……
 *  * next.invokeChannelRead(msg);
 *  *   ……
 *  * }
 *  * //...
 *  * }
 *  *
 *  * 首先，这个是一个静态方法。
 *  * 其次，这个方法没有啥特别。只是做了一个二转。将处理传递给context实例，调用context实例的invokeChannelRead方法。强调一下，使用了同一个名称哈。但是后边的invokeChannelRead，是一个实例方法，而且只有一个参数。
 *  *
 *  *   3.6.2context.invokeIN_EVT实例方法
 *  *   流水线小迭代第二步，触发当前的Context实例的IN_EVT操作。
 *  * 对于IN_EVT为ChannelRead的时候，第二步方法为invokeChannelRead，其源码如下：
 *  * abstract class AbstractChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext
 *  * {
 *  *
 *  * private void invokeChannelRead(Object msg) {
 *  *     ……
 *  *        ((ChannelInboundHandler) handler()).channelRead(this, msg);
 *  * ……
 *  * }
 *  *
 *  * }
 *  * 这一步很简单，就是将context和msg（byteBuf）作为参数，传递给Handler实例，完成业务处理。
 *  * 在Handler中，可以获取到以上两个参数实例，作为业务处理的输入。在业务Handler中的IN_EVT方法中，可以写自己的业务处理逻辑。
 *  *
 *  *
 *  *   3.6.3默认的handler.IN_EVT 入站处理操作
 *  *   流水线小迭代第三步，完后Context实例中Handler的IN_EVT业务操作。
 *  * 如果Handler中的IN_EVT方法中没有写业务逻辑，则Netty提供了默认的实现。默认源码在ChannelInboundHandlerAdapter 适配器类中。
 *  * 当IN_EVT为ChannelRead的时候，第三步的默认实现源码如下：
 *  * public class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler
 *  * {
 *  *
 *  * //默认的通道读操作
 *  * @Override
 *  * public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
 *  *     ctx.fireChannelRead(msg);
 *  * }
 *  *
 *  * //...
 *  * }
 *  *
 *  * 读完源码发现，这份默认源码，都没有做什么实际的处理。
 *  * 唯一的干的活，就是调用ctx.fireChannelRead(msg)，将msg通过context再一次发射出去。
 *  * 进入第四步
 *  *
 *  *  3.6.4context.fireIN_EVT再发射消息
 *  *  流水线小迭代第四步，寻找下家，触发下一家的入站处理。
 *  * 整个是流水线的流动的关键一步，实现了向下一个HandlerContext的流动。
 *  * 源码如下：
 *  * abstract class AbstractChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext
 *  * {
 *  *
 *  * private final boolean inbound;
 *  * private final boolean outbound;
 *  *
 *  * //...
 *  *
 *  * @Override
 *  * public ChannelHandlerContext fireChannelRead(final Object msg) {
 *  *     invokeChannelRead(findContextInbound(), msg);
 *  *     return this;
 *  * }
 *  * //..
 *  * }
 *  *
 *  * 第四步还是在ChannelInboundHandlerAdapter 适配器中定义。首先通过第五步，找到下一个Context，然后回到小迭代的第一步，完成了小迭代的一个闭环。
 *  * 这一步，对于业务Handler而言，很重要。
 *  * 在用户Handler中，如果当前 Handler 需要将此事件继续传播下去，则调用contxt.fireIN_EVT方法。如果不这样做, 那么此事件的流水线传播会提前终止。
 *  *
 *  *   3.6.5findContextInbound()找下家
 *  *   第五步是查找下家。
 *  * 代码如下：
 *  * public class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler
 *  * {
 *  * //...
 *  *
 *  * private AbstractChannelHandlerContext findContextInbound() {
 *  *     AbstractChannelHandlerContext ctx = this;
 *  *     do {
 *  *         ctx = ctx.next;
 *  *     } while (!ctx.inbound);
 *  *     return ctx;
 *  * }
 *  * }
 *  * 这个是一个标准的链表查询操作。this表示当前的context，this.next表示下一个context。通过while循环，一直往流水线的下边找，知道查找到下一个入站Context为止。
 *  *
 *  * 3.6.6是在第四步调用的
 *  * 找到之后，第四步通过 invokeChannelRead(findContextInbound(), msg)这个静态方法的调用，由回到小迭代的第一步，开始下一轮小的运行
 *  *
 *  * 3.6.7最后一轮Context处理
 *  * 我们在前面讲到，在Netty中，Tail是最后一个IN boundContext。
 *  * final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {
 *  * @Override
 *  * public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
 *  *     onUnhandledInboundMessage(msg);
 *  * }
 *  * protected void onUnhandledInboundMessage(Object msg) {
 *  *   //…
 *  *         //释放msg的引用计数
 *  *         ReferenceCountUtil.release(msg);
 *  *   //..
 *  * }
 *  * }
 *  * 在最后的一轮入站处理中。Tail没有做任何的业务逻辑，仅仅是对msg 释放一次引用计数。
 *  * 这个msg ，是从channel 入站源头的过来的byteBuf。有可能是引用计数类型（ReferenceCounted）类型的缓存，则需要释放其引用。如果不是ReferenceCounted，则什么也不做。
 *  * 关于缓存的引用计数，后续再开文章做专题介绍。
 *  *
 *  * 3.7.小结
 *  * 对入站（Inbound ）事件的处理流程，做一下小节：
 *  * Inbound 事件是通知事件，当某件事情已经就绪后，从Java IO 通知上层Netty  Channel。
 *  * Inbound 事件源头是 Channel内部的UNSafe；
 *  * Inbound 事件启动者是 Channel，通过Pipeline. fireIN_EVT启动。
 *  * Inbound 事件在 Pipeline 中传输方向是从 head 到 tail。
 *  * Inbound 事件最后一个的处理者是 TailContext, 并且其处理方法是空实现。如果没有其他的处理者，则对Inbound ，TailContext是唯一的处理者。
 *  * Inbound 事件的向后传递方法是contxt.fireIN_EVT方法。在用户Handler中，如果当前 Handler 需要将此事件继续传播下去，则调用contxt.fireIN_EVT方法。
 *  * 如果不这样做, 那么此事件的流水线传播会提前终止。
 *  *
 *
 *
 *  4.Pipeline outbound流程
 *    4.1出站的定义
 *    简单回顾一下。
     * 出站（outbound） 操作，通常是处于上层的Netty channel，去操作底层Java  NIO channel/OIO Channel。
     * 主要出站（outbound）操作如下：
     * 1. 端口绑定 bind
     * 2. 连接服务端 connect
     * 3. write写通道
     * 4.  flush刷新通道
     * 5.  read读通道
     * 6. 主动断开连接 disconnect
     * 7. 主动关闭通道 close
     * 最为常见，也是最容易理解的出站操作，是第3个操作 —— write写通道。
     * 一个Netty Channel的write 出站操作 实例：
     * // server向 Channel写登录响应
     * ctx.channel().write(“恭喜，登录成功”);
     * //....
 *
 *   4.2. 出站处理器Handler
 *   对于出站操作，有相应的出站Handler处理器。
 *   有四个比较重要的出站Handler类。
 *
 *   在抽象的ChannelOutboundHandler 接口中，定义了所有的出站操作的方法声明。
 *   在ChannelOutboundHandlerAdapter 出站适配器中，提供了出站操作的默认实现。如果要实现定制的出站业务逻辑，继承ChannelOutboundHandlerAdapter 适配器即可。ChannelOutboundHandlerAdapter 面向的是通用的出站处理场景。
 *   有一个特殊场景的出站处理器——HeadContext。先按下不表，稍候重点介绍。
 *
 *   4.3出站的上下文包裹器Context
 *   虽然有专门的Handler，但是，并没有专门的出站Context上下文包裹器。
 *   强调一下：
 *    没有单独的出站上下文Context基类。出站和入站，复用了同一个上下文Context基类。它就是AbstractChannelHandlerContext。
 *    在这个AbstractChannelHandlerContext基类中，定义了每一个出站操作的默认实现。
 *   基本的出站方法如下:
 *   AbstractChannelHandlerContext.bind(SocketAddress, ChannelPromise)
 * AbstractChannelHandlerContext.connect(SocketAddress,SocketAddress, hannelPromise)
 * AbstractChannelHandlerContext.write(Object, ChannelPromise)
 * AbstractChannelHandlerContext.flush()
 * AbstractChannelHandlerContext.read()
 * AbstractChannelHandlerContext.disconnect(ChannelPromise)
 * AbstractChannelHandlerContext.close(ChannelPromise)
 *
 *  赘述一遍：
 *  Context类型的接口是ChannelHandlerContext，抽象的基类是AbstractChannelHandlerContext。
 *  出站和入站的区分，通过基类AbstractChannelHandlerContext的两个属性来完成——outbound、intbound。
 *  出站Context的两个属性的值是：
 *  （1）AbstractChannelHandlerContext基类对象的属性outbound的值为false
 *  （2）AbstractChannelHandlerContext基类对象的属性intbound值为true
 *
 *  Pipeline 的第一个节点HeadContext，outbound属性值为true，所以一个典型的出站上下文。
 *
 *   4.4流逼哄哄的HeadContext
 *   为什么说流逼哄哄呢？
 *   因为：HeadContext不光是一个出站类型的上下文Context， 而且它完成整个出站流程的最后一棒。
 *   不信，看源码：
 *   final class HeadContext extends AbstractChannelHandlerContext
 *         implements ChannelOutboundHandler, ChannelInboundHandler
 * {
 *  private final Unsafe unsafe;
 *  HeadContext(DefaultChannelPipeline pipeline)
 * {
 *   //父类构造器
 *   super(pipeline, null, HEAD_NAME, false, true);
 *   //...
 * }
 * }
 * 在HeadContext的构造器中，调用super 方法去初始化基类AbstractChannelHandlerContext实例。
 * 注意，第四个、第五个参数非常重要。
 * 第四个参数false，此参数对应是是基类inbound的值，表示Head不是入站Context。
 * 第五个参数true，此参数对应是是基类outbound的值，表示Head是一个出站Context。
 * 所以，在作为上下文Context角色的时候，HeadContext是黑白分明的、没有含糊的。它就是一个出站上下文。
 * 但是，顺便提及一下，HeadContext还承担了另外的两个角色：
 * （1）入站处理器
 * （2）出站处理器
 * 所以，总计一下，其实HeadContext 承担了3个角色。
 * HeadContext作为Handler处理器的角色使用的时候，HeadContext整个Handler是一个两面派，承担了两个Handler角色：
 * （1）HeadContext是一个入站Handler。HeadContext 是入站流水线处理处理的起点。是入站Handler队列的排头兵。
 * （2）HeadContext是一个出站Handler。HeadContext 是出站流水线处理的终点，完成了出站的最后棒—— 执行最终的Java IO Channel底层的出站方法。
 *   整个出站处理的流水线，是如何一步一步，流转到最后一棒的呢？
 *
 *   4.5 TailContext出站流程的起点
 *   出站处理，起点在TailContext。
 *   这一点，和入站处理的流程刚好反过来。
 *   在Netty 的Pipeline流水线上，出站流程的起点是TailContext，终点是HeadContext，流水线执行的方向是从尾到头。
 *
 *   强调再强调：
 *   出站流程，只有outbound 类型的 Context 参与。inbound 类型的上下文Context不参与（TailContext另说）。
 *
 *   在Pipeline创建的时候，加入Handler之前，Pipeline就是有两个默认的Context——HeadContext，和TailContext。
 *   TailContext是出站起点，HeadContext是出站的终点。也就是说，Pipeline 从创建开始，就具已经备了Channel出站操作的能力的。
 *   关键的问题是：作为出站的起点，为什么TailContext不是橙色呢？
 *   首先，TailContext不是outbound类型，反而，是inbound入站类型的上下文包裹器。
 *   其次，TailContext 在出站流水线上，仅仅是承担了一个启动工作，寻找出第一个真正的出站Context，并且，将出站的第一棒交给他。
 *   总之，在出站流程上，TailContext作用，只是一把钥匙，仅此而已。
 *
 *
 *   4.6Tail是出站操作的起点
 *   TailContext 类的定义中，并没有实现 write写出站的方法。这个write(Object msg) 方法，定义在TailContext的基类——AbstractChannelHandlerContext 中。
 *   abstract class AbstractChannelHandlerContext
 * extends DefaultAttributeMap implements ChannelHandlerContext
 * {
 * //……
 * @Override
 * public ChannelFuture write(Object msg) {
 * //….
 * return write(msg, newPromise());
 * }
 * @Override
 * public ChannelFuture write(final Object msg, final ChannelPromise promise) {
 * //…
 * write(msg, false, promise);
 * return promise;
 * }
 * //……
 * @Override
 * public ChannelPromise newPromise() {
 * return new DefaultChannelPromise(channel(), executor());
 * }
 * //第三个重载的write
 *  private void write(Object msg, boolean flush, ChannelPromise promise)  {
 *        //...
 *    //找出下一棒 next
 *      AbstractChannelHandlerContext next = findContextOutbound();
 *        //....
 *     //执行下一棒 next.invoke
 *        next.invokeWrite(msg, promise);
 *        //...
 *  }
 * }
 * 有三个版本的write重载方法：
 * ChannelFuture write(Object msg)
 * ChannelFuture write(Object msg，ChannelPromise promise)
 * ChannelFuture write(Object msg， boolean flush，ChannelPromise promise)
 * 调用的次序是：
 * 第一个调用第二个，第二个调用第三个。
 * 第一个write 创建了一个ChannelPromise 对象，这个对象实例非常重要。因为Netty的write出站操作，并不一定是一调用write就立即执行，更多的时候是异步执行的。write返回的这个ChannelPromise 对象，是专门提供给业务程序，用来干预异步操作的过程。
 * 可以通过ChannelPromise 实例，监听异步处理的是否结束，完成write出站正真执行后的一些业务处理，比如，统计出站操作执行的时间等等。
 * ChannelPromise 接口，继承了 Netty 的Future的接口，使用了Future/Promise 模式。这个是一种异步处理干预的经典的模式。疯狂创客圈另外开了一篇文章，专门讲述Future/Promise 模式。
 * 第二个write，最为单薄。简单的直接调用第三个write，调用前设置flush 参数的值为false。flush 参数，表示是否要将缓冲区ByteBuf中的数据，立即写入Java IO Channel底层套接字，发送出去。一般情况下，第二个write设置了false，表示不立即发出，尽量减少底层的发送，提升性能。
 * 第三个write，最为重要，也最为复杂。分成两步，第一步是找出下一棒 next，下一棒next也是一个出站Context。第二步是，执行下一棒的invoke方法，也即是next.invokeWrite(msg, promise);
 * 完成以上的三步操作，TailContext 终于将write出站的实际工作，交到了第一棒outbound Context的手中
 * 至此，TailContext终于完成的启动write流程的使命。
 *
 *
 * 4.7出站流程小迭代的五个动作
 * 一般来说，Pipeline上会有多个OutBound Context（包裹着Handler），每一个OutBound Context 的处理，可以看成是大的流水处理中的一次小迭代。
 * 每一个小迭代，有五个动作。
 * 五个动作，具体如下：
 * （1）context.write(msg，promise)
 * （2）context.write(msg，flush，promise)
 * （3）context.findContextOutbound();
 * （4）next.invokeWrite(msg,promise)
 * （5）handler.write(this,msg,promise)
 * 整个五个动作中，只有第五步在Handler中定义。其他的四步，都在Context中定义。
 * 第一步、第二步的 write 方法在前面已经详细介绍过了。这两步主要完成promise实例的 创建，flush 参数的设置。
 * 现在到了比较关键的步骤：第三步。这一步是寻找出站的下一棒。
 *
 * 4.8findContextOutbound找出下一棒
 * 出站流程的寻找下一棒的工作，和入站处理的寻找下一棒的方向，刚好反过来。
 * 出站流程，查找的方向是从尾到头。这就用到的双向链表的指针是prev——向前的指针。具体来说，从当前的context开始，不断的使用prev指针，进行循环迭代查找。一直找到终点HeadContext，结束。
 * Netty源码如下：
 * abstract class AbstractChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext
 * {
 * //…
 * private AbstractChannelHandlerContext findContextOutbound() {
 *     AbstractChannelHandlerContext ctx = this;
 *     do {
 *         ctx = ctx.prev;
 *     } while (!ctx.outbound);
 *     return ctx;
 * }
 * //…
 * }
 * 这个是一个标准的链表的前向查询。
 * 每一次的查找，this表示当前的查询所在的context 节点，this.prev表示前一个context节点。
 * 查找时，用到的双向链表的指针是prev——向前的指针。通过while循环，一直往Pipeline的前面查找，如果前面的context不是outbound出站上下文，则一直向前。直到，直到，一直到查找出下一个出站Context上下文为止。
 * 最初的查找，从TailContext开始，this就是TailContext。后续的每一次查找，都是从当前的Context上下文开始的。
 *
 * 4.9默认的write出站实现
 *  默认的write 出站方法的实现，定义在ChannelOutboundHandlerAdapter 中。write方法的源码如下：
 *  Handler的write出站操作，已经到了一轮出站小迭代的最后一步。这个默认的write方法，简单的调用context. write方法，回到了小迭代的第一步。
 * 换句话说，默认的ChannelOutboundHandlerAdapter 中的handler方法，是流水线的迭代一个一个环节前后连接起来，的关键的一小步，保证了流水线不被中断掉。
 * 反复进行小迭代，迭代处理完中间的业务handler之后，就会走到流水线的HeadContext。
 *
 * 4.10HeadContext出站的最后一棒
 * 在出站迭代处理pipeline的最后一步， 会来到HeadContext。
 * HeadContext是如何完成最后一棒的呢？
 *
 * HeadContext 包含了一个unsafe 成员。 这个unsafe 成员，是一个只供在netty内部使用的类型。
 * unsafe 主要功能，就是完成对 Java NIO 底层Channel的写入 。
 * 由此可见，在Netty中的三大上下文包裹器HeadContext、TailContext 、 DefaultChannelHandlerContext中，HeadContext是离 Java NIO 底层Channel最近的地方。三大包裹器，除了HeadContext，也没有谁包含Unsafe。对完成出站的最终操作职能来说，没有谁比HeadContext 更加直接。所以，这个出站处理的最后一棒，只能是HeadContext 了，呵呵。
 * 至此为止，write出站的整个流水线流程，已经全部讲完。
 * 从具体到抽象，我们再回到出站处理的通用流程。
 *
 * 4.11出站操作的全流程
 * 基本上，在流程上，所有的出站事件的处理过程，是一致的。
 * 为了方便说明，使用OUT_EVT符号代替一个通用出站操作。
 * 通用的出站Outbound操作处理过程，大致如下:
 * （1）channel.OUT_EVT(msg);
 * （2）pipeline.OUT_EVT(msg);
 * （3）context.OUT_EVT(msg);
 * （4）context.OUT_EVT(msg,promise);
 * （5）context.OUT_EVT(msg,flush,promise);
 * （6）context.findContextOutbound();
 * （7）next.invoke(msg,flush,promise);
 * （8）handle.OUT_EVT(context,msg,promise);
 * （9）context.OUT_EVT(msg,promise);
 * 上面的流程，如果短时间内看不懂，可以在回头看看write出站的实例。
 *
 */
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     *
     * @param  handler          the {@link ChannelHandler} to remove
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this pipeline.
     *
     * @param  name             the name under which the {@link ChannelHandler} was stored.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this pipeline.
     *
     * @param <T>           the type of the handler
     * @param handlerType   the type of the handler
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in this pipeline.
     *
     * @param  oldHandler    the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return itself

     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new handler in this pipeline.
     *
     * @param  oldName       the name of the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new handler in this pipeline.
     *
     * @param  oldHandlerType   the type of the handler to be removed
     * @param  newName          the name under which the replacement should be added
     * @param  newHandler       the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * Returns the context of the first {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext firstContext();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * Returns the context of the last {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext lastContext();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * Returns the {@link List} of the handler names.
     */
    List<String> names();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

     @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    @Override
    ChannelPipeline fireChannelRead(Object msg);

    @Override
    ChannelPipeline fireChannelReadComplete();

    @Override
    ChannelPipeline fireChannelWritabilityChanged();

    @Override
    ChannelPipeline flush();
}