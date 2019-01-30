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
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the {@linkplain ChannelConfig configuration parameters} of the channel (e.g. receive buffer size),</li>
 * <li>the I/O operations that the channel supports (e.g. read, write, connect, and bind), and</li>
 * <li>the {@link ChannelPipeline} which handles all I/O events and requests
 *     associated with the channel.</li>
 * </ul>
 *
 * <h3>All I/O operations are asynchronous.</h3>
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which will notify you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * <h3>Channels are hierarchical</h3>
 * <p>
 * A {@link Channel} can have a {@linkplain #parent() parent} depending on
 * how it was created.  For instance, a {@link SocketChannel}, that was accepted
 * by {@link ServerSocketChannel}, will return the {@link ServerSocketChannel}
 * as its parent on {@link #parent()}.
 * <p>
 * The semantics of the hierarchical structure depends on the transport
 * implementation where the {@link Channel} belongs to.  For example, you could
 * write a new {@link Channel} implementation that creates the sub-channels that
 * share one socket connection, as <a href="http://beepcore.org/">BEEP</a> and
 * <a href="http://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 *
 * <h3>Downcast to access transport-specific operations</h3>
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *
 * <h3>Release resources</h3>
 * <p>
 * It is important to call {@link #close()} or {@link #close(ChannelPromise)} to release all
 * resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 *
 * 概述：
 *      首先强调一点:NIO的Channel与Netty的Channel不是一个东西!
 *      Netty重新设计了Channel接口,并且给予了很多不同的实现。Channel时Netty的网络抽象类,除了NIO中Channel所包含的网络I/O操作,
 *      主动建立/关闭连接,获取双方网络地址的功能外,还包含了Netty框架的功能,例如:获取Channel的EventLoop\Pipeline等。
 *
 *      Channel接口是能与一个网络套接字(或组件)进行I/0操作(读取\写入\连接\绑定)的纽带.
 *      通过Channel可以获取连接的状态(是否连接/是否打开),配置通道的参数(设置缓冲区大小等),进行I/O操作
 *
 *      Channel的基本方法
 *      id():返回此通道的全局唯一标识符.
 *      isActive():如果通道处于活动状态并连接,则返回true.
 *      isOpen():如果通道打开并且可能稍后激活,则返回true.
 *      isRegistered():如果通道注册了EventLoop，则返回true。
 *      config():返回关于此通道的配置.
 *      localAddress():返回此通道绑定的本地地址.
 *      pipeline():返回分派的ChannelPipeline.
 *      remoteAddress():返回此通道连接到的远程地址.
 *      flush():请求通过ChannelOutboundInvoker将所有挂起的消息输出.
 *
 *      关于Channel的释放
 *       当Channel完成工作后,需要调用ChannelOutboundInvoker.close()或ChannelOutboundInvoker.close(ChannelPromise)释放所有资源.
 *       这样做是为了确保所有资源(文件句柄)都能够得到释放
 *
 *       Channel是由netty抽象出来的网络I/O操作的接口，作为Netty传输的核心，负责处理所有的I/O操作。Channel提供了一组用于传输的API，
 *       主要包括网络的读/写，客户端主动发起连接、关闭连接，服务端绑定端口，获取通讯双方的网络地址等；同时，还提供了与netty框架相关
 *       的操作，如获取channel相关联的EventLoop、pipeline等。
 *
 *       一个Channel可以拥有父Channel，服务端Channel的parent为空，对客户端Channel来说，它的parent就是创建它的ServerSocketChannel。
 *       当一个Channel相关的网络操作完成后，请务必调用ChannelOutboundInvoker.close（）或ChannelOutboundInvoker.close（ChannelPromise）
 *       来释放所有资源，如文件句柄。
 *
 *       每个Channel都会被分配一个ChannelPipeline和ChannelConfig。ChannelConfig主要负责Channel的所有配置，并且支持热更新。此外，每个Channel
 *       都会绑定一个EventLoop，该通道整个生命周期内的事件都将由这个特定EventLoop负责处理。
 *
 *       Channel是独一无二的，所以为了保证顺序将Channel声明为Comparable的一个子接口。如果两个Channel实例返回了相同的hashcode，
 *       那么AbstractChannel中compareTo()方法的实现将会抛出一个Error。
 *
 *
 *       在netty中，所有的I/O操作都是异步的，这些操作被调用将立即返回一个ChannelFuture实例，用户通过ChannelFuture实例获取操作的结果。
 *
 *
 *       Channel的子类非常多，按协议分类来看，则有基于TCP、UDP、SCTP的Channel，如基于UDP协议的DatagramChannel；按底层I/O模型来看，
 *       分别有基于传统阻塞型I/O模型的Channel，基于java NIO selector模型的Channel，基于FreeBSD I/O复用模型KQueue的Channel，基于
 *       Linux Epoll边缘触发模型实现的Channel；按功能来看，主要分为客户端Channel和服务端Channel，常用的有客户端NioSocketChannel和
 *       服务端NioServerSocketChannel，两者分别封装了java.nio中包含的 ServerSocketChannel和SocketChannel的功能
 *
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * Returns the globally unique identifier of this {@link Channel}.
     *
     * Channel 的编号
     */
    ChannelId id();

    /**
     * Return the {@link EventLoop} this {@link Channel} was registered to.
     *
     * Channel 注册到的 EventLoop
     */
    EventLoop eventLoop();

    /**
     * Returns the parent of this channel.
     *
     * 父 Channel 对象
     *
     * @return the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     */
    Channel parent();

    /**
     * Returns the configuration of this channel.
     *
     * Channel 配置参数
     */
    ChannelConfig config();

    /**
     * Returns {@code true} if the {@link Channel} is open and may get active later
     *
     * Channel 是否打开。
     *
     * true 表示 Channel 可用
     * false 表示 Channel 已关闭，不可用
     */
    boolean isOpen();

    /**
     * Returns {@code true} if the {@link Channel} is registered with an {@link EventLoop}.
     *
     * Channel 是否注册
     *
     * true 表示 Channel 已注册到 EventLoop 上
     * false 表示 Channel 未注册到 EventLoop 上
     */
    boolean isRegistered();

    /**
     * Return {@code true} if the {@link Channel} is active and so connected.
     *
     * Channel 是否激活
     *
     * 对于服务端 ServerSocketChannel ，true 表示 Channel 已经绑定到端口上，可提供服务
     * 对于客户端 SocketChannel ，true 表示 Channel 连接到远程服务器
     */
    boolean isActive();

    /**
     * Return the {@link ChannelMetadata} of the {@link Channel} which describe the nature of the {@link Channel}.
     *
     * Channel 元数据
     */
    ChannelMetadata metadata();

    /**
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * 本地地址
     *
     * @return the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress localAddress();

    /**
     * Returns the remote address where this channel is connected to.  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * 远端地址
     *
     * @return the remote address of this channel.
     *         {@code null} if this channel is not connected.
     *         If this channel is not connected but it can receive messages
     *         from arbitrary remote addresses (e.g. {@link DatagramChannel},
     *         use {@link DatagramPacket#recipient()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     */
    SocketAddress remoteAddress();

    /**
     * 主动关闭当前连接，close操作会触发链路关闭事件，该事件会级联触发ChannelPipeline中ChannelOutboundHandler.close（ChannelHandlerContext，ChannelPromise）
     * 方法被调用；区别在于方法2提供了ChannelPromise实例，用于设置close操作的结果，无论成功与否。
     *
     *
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     *
     * Channel 关闭的 Future 对象
     */
    ChannelFuture closeFuture();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     *
     * Channel 是否可写
     *
     * 当 Channel 的写缓存区 outbound 非 null 且可写时，返回 true
     */
    boolean isWritable();

    /**
     * 获得距离不可写还有多少字节数
     *
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     */
    long bytesBeforeUnwritable();

    /**
     * 获得距离可写还要多少字节数
     *
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     */
    long bytesBeforeWritable();

    /**
     * Returns an <em>internal-use-only</em> object that provides unsafe operations.
     *
     * Unsafe 对象
     */
    Unsafe unsafe();

    /**
     * Return the assigned {@link ChannelPipeline}.
     *
     * ChannelPipeline 对象，用于处理 Inbound 和 Outbound 事件的处理
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     *
     * ByteBuf 分配器
     */
    ByteBufAllocator alloc();

    /**
     * 从当前Channel中读取数据到第一个inbound缓冲区中，如果读取数据，触发ChannelInboundHandler.channelRead（ChannelHandlerContext，Object）
     * 事件；当读取完成后，触发channelReadComplete事件，这样业务的ChannelHandler可以决定是否需要继续读取数据。同时，该操作会触发outbound事件，
     * 该事件会级联触发ChannelPipeline中ChannelHandler.read（ChannelHandlerContext）方法被调用。
     * @return
     */
    @Override
    Channel read();

    /**
     * 将write操作写入环形数组中的消息全部写入到Channel中，发送给通信对方。该操作会触发outbound事件，该事件会级联触发ChannelPipeline中
     * ChannelHandler.flush（ChannelHandlerContext）方法被调用。
     * @return
     */
    @Override
    Channel flush();

    /**
     * 概述：
     *      Unsafe接口是Channel的辅助接口，用于执行实际的I/O操作，且必须在I/O线程中执行。该接口不应该被用户代码直接使用，
     *      仅在netty内部使用。Unsafe定义在Channel内部。
     *
     *      由于Unsafe是Channel的内部接口，因此其大多数子类实现也都定义在Channel子类的内部，提供与Channel相关的方法实现。
     *
     *
     *      Unsafe是Channel的内部类，一个Channel对应一个Unsafe。
     *
     *      Unsafe用于处理Channel对应网络IO的底层操作。ChannelHandler处理回调事件时产生的相关网络IO操作最终也会委托给
     *      Unsafe执行。
     *
     *      Unsafe接口中定义了socket相关操作，包括SocketAddress获取、selector注册、网卡端口绑定、socket建连与断连、
     *      socket写数据。这些操作都和jdk底层socket相关。
     *
     * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
     * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
     * following methods:
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(EventLoop, ChannelPromise)}</li>
     *   <li>{@link #deregister(ChannelPromise)}</li>
     *   <li>{@link #voidPromise()}</li>
     * </ul>
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         *
         * ByteBuf 分配器的处理器
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

        /**
         * 返回绑定到本地的SocketAddress，没有则返回null
         *
         * Return the {@link SocketAddress} to which is bound local or
         * {@code null} if none.
         *
         * 本地地址
         */
        SocketAddress localAddress();

        /**
         * 返回绑定到远程的SocketAddress，如果还没有绑定，则返回null。
         *
         * Return the {@link SocketAddress} to which is bound remote or
         * {@code null} if none is bound yet.
         *
         * 远端地址
         */
        SocketAddress remoteAddress();

        /**
         * 注册Channel到多路复用器，并在注册完成后通知ChannelFuture。一旦ChannelPromise成功，
         * 就可以在ChannelHandler内向EventLoop提交新任务。 否则，任务可能被拒绝也可能不会被拒绝。
         *
         * Register the {@link Channel} of the {@link ChannelPromise} and notify
         * the {@link ChannelFuture} once the registration was complete.
         */
        void register(EventLoop eventLoop, ChannelPromise promise);

        /**
         * 用于绑定指定的本地Socket地址localAddress，触发outbound事件，
         * 该事件会级联触发ChannelPipeline中ChannelHandler.bind（ChannelHandlerContext，SocketAddress，ChannelPromise）方法被调用。
         *
         *
         * Bind the {@link SocketAddress} to the {@link Channel} of the {@link ChannelPromise} and notify
         * it once its done.
         */
        void bind(SocketAddress localAddress, ChannelPromise promise);

        /**
         *
         * 客户端使用指定的服务器地址remoteAddress发起连接请求，如果连接由于连接超时而失败，则ChannelFuture将会失败，
         * 并出现ConnectTimeoutException。 如果由于连接被拒绝而失败，将使用ConnectException。
         *
         * Connect the {@link Channel} of the given {@link ChannelFuture} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * The {@link ChannelPromise} will get notified once the connect operation was complete.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        /**
         * Disconnect the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void disconnect(ChannelPromise promise);

        /**
         * Close the {@link Channel} of the {@link ChannelPromise} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void close(ChannelPromise promise);

        /**
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * Deregister the {@link Channel} of the {@link ChannelPromise} from {@link EventLoop} and notify the
         * {@link ChannelPromise} once the operation was complete.
         */
        void deregister(ChannelPromise promise);

        /**
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
         * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * Schedules a write operation.
         */
        void write(Object msg, ChannelPromise promise);

        /**
         * Flush out all write operations scheduled via {@link #write(Object, ChannelPromise)}.
         */
        void flush();

        /**
         * Return a special ChannelPromise which can be reused and passed to the operations in {@link Unsafe}.
         * It will never be notified of a success or error and so is only a placeholder for operations
         * that take a {@link ChannelPromise} as argument but for which you not want to get notified.
         */
        ChannelPromise voidPromise();

        /**
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
