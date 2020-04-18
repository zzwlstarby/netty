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

package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NameResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 一. Netty做了什么？
 * 1.Netty实现了对Java NIO的封装，提供了更方便使用的接口；
 * 2.Netty利用责任链模式实现了ChannelPipeline这一概念，基于ChannelPipeline，我们可以 优雅的实现网络消息的处理（可插拔，解耦）；
 * 3.Netty的Reactor线程模型，利用无锁化提高了系统的性能；
 * 4.Netty实现了ByteBuf用于对字节进行缓存和操作，相比JDK的ByteBuffer，它更易用，同时还提供了Buffer池的功能，对于UnpooledDirectByteBuf和PooledByteBuf，Netty还对其内存使用进行了跟踪，发现内存泄漏时会给出报警；
 * 链接：https://www.jianshu.com/p/b3e74973641e
 *
 * 二.Netty对JDK NIO的封装
 * JDK NIO有ServerSocketChannel、SocketChannel、Selector、SelectionKey几个核心概念。
 * Netty提供了一个Channel接口统一了对网络的IO操作，其底层的IO通信是交给Unsafe接口实现，而Channel主要负责更高层次的read、write、flush、和ChannelPipeline、Eventloop等组件的交互，以及一些状态的展示；做到了职责的清晰划分，对使用者是很友好的，规避了JDK NIO中一些比较繁琐复杂的概念和流程。
 * Channel、Unsafe继承UML图
 * Channel和Unsafe是分多级别实现的，不同级别的Channel和Unsafe对应了不同级别的实现，也是“职责单一原则”的体现。
 * 链接：https://www.jianshu.com/p/b3e74973641e
 *
 * 三.ChannelPipeline责任链模型
 * 借用网上的一张图表示Channel、ChannelPipeline、ChannelHandlerContext和ChannelHandler之间的关系。
 * 每个Channel都持有一个ChannelPipeline，通过Channel的读写等IO操作实际上是交由ChannelPipeline处理的，
 * 而ChannelPipeline会持有一个ChannelHandlerContext链表，每个Context内部又包含一个ChannelHandler，
 * 使用者在Pipeline上添加handler负责逻辑处理，ChannelHandlerContext负责事件的传递流转。
 * 每个ChannelPipeline都会持有2个特殊的ChannelHandlerContext——head和tail，他们分别是Context链表的头和尾。
 *
 * ChannelPipeline上的事件，分为Inbound事件和Outbound事件2种，Inbound事件从headContext读入，在Context链上的InboundHandler
 * 上正向依次流动；Outbound事件从Channel（即ChannelPipeline）上触发，则从tailContext上出发，在Context链上的OutboundHandler
 * 上反向依次流动，若从某一个Context上触发，则从这个Context之后的下一个OutboundContext开始执行。headContext利用Unsafe完成实际的IO操作。
 *
 * 我们在使用Netty的时候，业务逻辑其实基本都存在于ChannelHandler；Netty也为我们提供了很多通用的Handler，如一些常用的编解码Handler，
 * 常见应用层协议的Handler，整流、心跳、日志等常用功能的Handler，合理使用这些Handler能迅速提高我们开发的效率。
 *
 * 四.Reactor线程模型
 * Reactor模型是一种常见的并发编程模型，关于React模型可以参考这篇文章Reactor模型，React模型改变了Thread Per Connection的模式，
 * 它将一个网络IO操作分为2部分：连接的建立，网络通信及消息处理；这两部分分别用单独的线程池去处理（一般情况下，连接的建立用单独的一个线程就足够了），
 * 这样做的好处如下：功能解耦、利于维护、利于组件化复用、方便细粒度的并发控制，另外可以通过减少线程数，避免大量的线程切换。其模型图如下：
 *
 * 单的说，一个Reactor线程（池）负责接收所有的连接请求，然后将连接产生的Channel赋给Work线程池中的线程，接下来的通信操作都交给Work线程执行。
 * Netty结合NIO的特点合理的使用了Reactor模型，具体地说，Netty的Reactor线程接收到一个连接请求后，会创建一个Channel，并为这个Channel分配一个EventLoop，
 * 每个EventLoop对应一个线程，Channel上的IO操作将在EventLoop上执行，一个Channel仅绑定在一个EventLoop上，一个EventLoop可以对应多个Channel，这样就避免了同步，也提高了线程的使用效率。
 * 实际上，EventLoop中的线程除了执行IO操作，还会执行ChannelPipeline上的handler的责任链方法，这样做是为了避免频换切换线程带来的损耗，
 * 所以handler中一般不可以放置耗时的任务，如果有耗时的任务，可以将任务放入自定义的线程池中执行。
 *
 * 链接：https://www.jianshu.com/p/b3e74973641e
 *
 * 五.ByteBuf
 * Java的NIO给我们提供了缓冲区的实现ByteBuffer，但是它的易用性较差（读写模式需要切换等问题），所以，Netty自己重新实现了缓冲区ByteBuf，
 * ByteBuf的API更易用、并且提供了内存池的功能，对于池化的ByteBuf和直接内存的ByteBuf，Netty还提供了对内存泄漏的监控（并且设置了各种性能级别），
 * 另外ByteBuf还提供了对ByteBuffer的兼容。
 *
 * 链接：https://www.jianshu.com/p/b3e74973641e
 *
 *
 *
 * 概述：
 *      1.Bootstrap的主要作用就是处理程序启动时候的逻辑，通过它你可以配置你的应用。
 *      你可以使用Bootstrap连接到某个客户端的ip和port，或者将服务端绑定到某个端口上。
 *      Bootstrap是为客户端准备的，ServerBootstrap是为服务端准备的。
 *      我们在使用netty4的时候，总是从使用它的两个启动器开始的，它们是我们最熟悉的netty的API，
 *      也是netty程序的入口，因此我们从启动器来开启研究netty4源码是非常明智的选择，从我们最熟悉的内容开始，
 *      让我们一起来探究它背后是怎么实现的。
 *
 *      2.使用Bootstrap还是ServerBootstrap与你使用的协议没有任何关系，只与你的目的有关：你是想创建一个客户端还是服务端。
 *      两者除了使用场景不同之外，还有一个很关键的不同:客户端Bootstrap使用一个EventLoopGroup，而服务端的使用的是两个。
 *      服务端可以看成是包含两组Channel，一组只包含一个ServerChannel，它表示服务端自己绑定到本地端口的套接字；
 *      另一组则包含了很多Channel，它们代表服务端所收到的所有的连接。
 *
 *      3.Boostrape往往是用于启动一个netty客户端或者UDP的一端。它通常使用connet()方法连接到远程的主机和端口，当然也
 *      可以通过bind()绑定本地的一个端口，它仅仅需要使用一个EventLoopGroup。
 *
 *
 *      4.服务端两个EventLoopGroup的协作流程：GroupA的主要目的是：接收外部连接请求，然后将连接转移给GroupB，
 *      而GroupB负责这些请求建立的通道的处理。
 *
 *      5.Bootstrap和ServerBootstrape这2个类都继承了AbstractBootstrap，因此它们有很多相同的方法和职责。它们都是启动器，
 *      能够帮助netty使用者更加方便地组装和配置netty，也可以更方便地启动netty应用程序，比使用者自己从头去将netty的各部
 *      分组装起来要方便地多，降低了使用者的学习和使用成本，它是我们使用netty的入口和最重要的API，可以通过它来连接到一个
 *      主机和端口上，也可以通过它来绑定到一个本地的端口上，它们两者之间相同之处要大于不同。它们和其它类之间的关系是它将
 *      netty的其它各类进行组装和配置，它会组合和直接或间接依赖其它的类。
 *
 *
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 * <p>The {@link #bind()} methods are useful in combination with connectionless transports such as datagram (UDP).
 * For regular TCP connections, please use the provided {@link #connect()} methods.</p>
 */
public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    /**
     * 默认地址解析器对象
     */
    private static final AddressResolverGroup<?> DEFAULT_RESOLVER = DefaultAddressResolverGroup.INSTANCE;

    /**
     * 启动类配置对象
     */
    private final BootstrapConfig config = new BootstrapConfig(this);
    /**
     * 地址解析器对象
     */
    @SuppressWarnings("unchecked")
    private volatile AddressResolverGroup<SocketAddress> resolver = (AddressResolverGroup<SocketAddress>) DEFAULT_RESOLVER;
    /**
     * 连接地址
     */
    private volatile SocketAddress remoteAddress;

    public Bootstrap() { }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        resolver = bootstrap.resolver;
        remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * Sets the {@link NameResolver} which will resolve the address of the unresolved named address.
     *
     * @param resolver the {@link NameResolver} for this {@code Bootstrap}; may be {@code null}, in which case a default
     *                 resolver will be used
     *
     * @see io.netty.resolver.DefaultAddressResolverGroup
     */
    @SuppressWarnings("unchecked")
    public Bootstrap resolver(AddressResolverGroup<?> resolver) {
        this.resolver = (AddressResolverGroup<SocketAddress>) (resolver == null ? DEFAULT_RESOLVER : resolver);
        return this;
    }

    /**
     * The {@link SocketAddress} to connect to once the {@link #connect()} method
     * is called.
     */
    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    /**
     * @see #remoteAddress(SocketAddress)
     */
    public Bootstrap remoteAddress(String inetHost, int inetPort) {
        remoteAddress = InetSocketAddress.createUnresolved(inetHost, inetPort);
        return this;
    }

    /**
     * @see #remoteAddress(SocketAddress)
     */
    public Bootstrap remoteAddress(InetAddress inetHost, int inetPort) {
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
        return this;
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect() {
        // 校验必要参数
        validate();
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }
        // 解析远程地址，并进行连接
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(String inetHost, int inetPort) {
        return connect(InetSocketAddress.createUnresolved(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(InetAddress inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        // 校验必要参数
        validate();
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        // 解析远程地址，并进行连接
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        // 校验必要参数
        validate();
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        // 解析远程地址，并进行连接
        return doResolveAndConnect(remoteAddress, localAddress);
    }

    /**
     * @see #connect()
     */
    private ChannelFuture doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        // 初始化并注册一个 Channel 对象，因为注册是异步的过程，所以返回一个 ChannelFuture 对象。
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();

        if (regFuture.isDone()) {
            // 若执行失败，直接进行返回。
            if (!regFuture.isSuccess()) {
                return regFuture;
            }
            // 解析远程地址，并进行连接
            return doResolveAndConnect0(channel, remoteAddress, localAddress, channel.newPromise());
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    // Directly obtain the cause and do a null check so we only need one volatile read in case of a
                    // failure.
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, doResolveAndConnect0so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        // 解析远程地址，并进行连接
                        doResolveAndConnect0(channel, remoteAddress, localAddress, promise);
                    }
                }

            });
            return promise;
        }
    }

    private ChannelFuture doResolveAndConnect0(final Channel channel, SocketAddress remoteAddress,
                                               final SocketAddress localAddress, final ChannelPromise promise) {
        try {
            final EventLoop eventLoop = channel.eventLoop();
            final AddressResolver<SocketAddress> resolver = this.resolver.getResolver(eventLoop);

            if (!resolver.isSupported(remoteAddress) || resolver.isResolved(remoteAddress)) {
                // Resolver has no idea about what to do with the specified remote address or it's resolved already.
                doConnect(remoteAddress, localAddress, promise);
                return promise;
            }

            // 解析远程地址
            final Future<SocketAddress> resolveFuture = resolver.resolve(remoteAddress);

            if (resolveFuture.isDone()) {
                // 解析远程地址失败，关闭 Channel ，并回调通知 promise 异常
                final Throwable resolveFailureCause = resolveFuture.cause();
                if (resolveFailureCause != null) {
                    // Failed to resolve immediately
                    channel.close();
                    promise.setFailure(resolveFailureCause);
                } else {
                    // Succeeded to resolve immediately; cached? (or did a blocking lookup)
                    // 连接远程地址
                    doConnect(resolveFuture.getNow(), localAddress, promise);
                }
                return promise;
            }

            // Wait until the name resolution is finished.
            resolveFuture.addListener(new FutureListener<SocketAddress>() {
                @Override
                public void operationComplete(Future<SocketAddress> future) throws Exception {
                    // 解析远程地址失败，关闭 Channel ，并回调通知 promise 异常
                    if (future.cause() != null) {
                        channel.close();
                        promise.setFailure(future.cause());
                    // 解析远程地址成功，连接远程地址
                    } else {
                        doConnect(future.getNow(), localAddress, promise);
                    }
                }
            });
        } catch (Throwable cause) {
            // 发生异常，并回调通知 promise 异常
            promise.tryFailure(cause);
        }
        return promise;
    }

    private static void doConnect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise connectPromise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        final Channel channel = connectPromise.channel();
        channel.eventLoop().execute(new Runnable() {

            @Override
            public void run() {
                if (localAddress == null) {
                    channel.connect(remoteAddress, connectPromise);
                } else {
                    channel.connect(remoteAddress, localAddress, connectPromise);
                }
                connectPromise.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }

        });
    }

    @Override
    @SuppressWarnings("unchecked")
    void init(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();

        // 添加处理器到 pipeline 中
        p.addLast(config.handler());

        // 初始化 Channel 的可选项集合
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        // 初始化 Channel 的属性集合
        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
        }
    }

    @Override
    public Bootstrap validate() {
        // 父类校验
        super.validate();
        // handler 非空
        if (config.handler() == null) {
            throw new IllegalStateException("handler not set");
        }
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Bootstrap clone() {
        return new Bootstrap(this);
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration except that it uses
     * the given {@link EventLoopGroup}. This method is useful when making multiple {@link Channel}s with similar
     * settings.
     */
    public Bootstrap clone(EventLoopGroup group) {
        Bootstrap bs = new Bootstrap(this);
        bs.group = group;
        return bs;
    }

    @Override
    public final BootstrapConfig config() {
        return config;
    }

    final SocketAddress remoteAddress() {
        return remoteAddress;
    }

    final AddressResolverGroup<?> resolver() {
        return resolver;
    }
}
