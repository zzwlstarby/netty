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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.util.internal.SocketUtils;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 *
 * 1.AbstractBootstrap是一个工具类，用来配置和启动Channel的
 *
 * 2.AbstractBootstrap提供了一个ChannelFactory对象用来创建Channel,一个Channel会对应一个EventLoop用于IO的事件处理，
 * 在Channel的整个生命周期中会绑定一个EventLoop,这里可理解给Channel分配一个线程进行IO事件处理，结束后回收该线程，
 * 但是AbstractBootstrap没有提供EventLoop而是提供了一个EventLoopGroup，EventLoop继承EventLoopGroup，
 * 在某些情况下可以把EventLoopGroup当EventLoop来用，EventLoopGroup中包含多个Eventloop，当一个连接到达，Netty会注册一个Channel，
 * 然后EventLoopGroup会分配一个EventLoop绑定到这个channel。不管是服务器还是客户端的Channel都需要绑定一个本地端口这就有了SocketAddress类的对象localAddress，
 * Channel有很多选项所有有了options对象LinkedHashMap<channeloption<?>, Object>。怎么处理Channel的IO事件呢，我们添加一个事件处理器ChannelHandler对象。
 *
 * AbstractBootstrap这个抽象Builder一共需要有6个部分group、channelFactory、localAddress、options、attrs、handler
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {

    /**
     * EventLoopGroup 对象
     */
    volatile EventLoopGroup group;
    /**
     * Channel 工厂，用于创建 Channel 对象。
     */
    @SuppressWarnings("deprecation")
    private volatile ChannelFactory<? extends C> channelFactory;
    /**
     * 本地地址
     */
    private volatile SocketAddress localAddress;
    /**
     * 可选项集合，参数配置集合
     */
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    /**
     * 属性集合
     */
    private final Map<AttributeKey<?>, Object> attrs = new LinkedHashMap<AttributeKey<?>, Object>();
    /**
     * 处理器
     */
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        synchronized (bootstrap.attrs) {
            attrs.putAll(bootstrap.attrs);
        }
    }

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-created
     * {@link Channel}
     */
    public B group(EventLoopGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (this.group != null) { // 不允许重复设置
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    /**
     * 本方法作为一个辅助方法，作为建造者模式（builder模式）返回this的一个辅助类。例如group（）方法就是调用self（）返回当前对象。
     * 这样设计的目的在于可以自由选择设置相关参数而不强求绑定。这样可以根据使用场景进行自由的设置参数。
     * 但是缺点也是明显的，那就是使用者自己要清楚设置哪些参数。
     * 该设计模式对我们的最大启发就是设置参数，我们可以将参数定义在一个类中，然后通过链式调用来设置参数。
     * 我们需要设置哪些参数就调用哪些方法。实际上这个设计模式在我们日常开发中都能使用到
     * @return
     */
    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(io.netty.channel.ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    /**
     * AbstractBootstrap通过ChannelFactory创建Channel实例，channel(channelClass)方法看起来好像是设置了一个Channel，
     * 但实际上只是给指定channel设置了默认的ChannelFactory实现，也就是说根据一个channel在bootstrap中设置一个当前channel的一个channelFactory
     * @param channelClass
     * @return
     */
    public B channel(Class<? extends C> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new ReflectiveChannelFactory<C>(channelClass));
    }

    /**
     * @deprecated Use {@link #channelFactory(io.netty.channel.ChannelFactory)} instead.
     */
    @Deprecated
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (this.channelFactory != null) { // 不允许重复设置
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }

    /**
     * The {@link SocketAddress} which is used to bind the local "end" to.
     *
     * 绑定
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> B option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        if (value == null) { // 空，意味着移除
            synchronized (options) {
                options.remove(option);
            }
        } else { // 非空，进行修改
            synchronized (options) {
                options.put(option, value);
            }
        }
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) { // 空，意味着移除
            synchronized (attrs) {
                attrs.remove(key);
            }
        } else { // 非空，进行修改
            synchronized (attrs) {
                attrs.put(key, value);
            }
        }
        return self();
    }

    /**
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration.  This method is useful when making
     * multiple {@link Channel}s with similar settings.  Please note that this method does not clone the
     * {@link EventLoopGroup} deeply but shallowly, making the group a shared resource.
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        // 校验服务启动需要的必要参数
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        // 绑定本地地址( 包括端口 )
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        // 校验服务启动需要的必要参数
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        // 绑定本地地址( 包括端口 )
        return doBind(localAddress);
    }

    private ChannelFuture doBind(final SocketAddress localAddress) {
        // 初始化并注册一个 Channel 对象，因为注册是异步的过程，所以返回一个 ChannelFuture 对象。
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) { // 若发生异常，直接进行返回。
            return regFuture;
        }

        // 绑定 Channel 的端口，并注册 Channel 到 SelectionKey 中。
        if (regFuture.isDone()) { // 未
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise); // 绑定
            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    System.out.println(Thread.currentThread() + ": PendingRegistrationPromise");
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        doBind0(regFuture, channel, localAddress, promise); // 绑定
                    }
                }
            });
            return promise;
        }
    }


    /**
     * Channel向EventLoop注册的过程，是在启动时进行的。注册的入口代码，在启动类AbstractBootstrap.initAndRegister 方法中。
     * 完整的注册调用流程如下:https://img2018.cnblogs.com/blog/1485398/201810/1485398-20181025005659425-127341228.png
     *注册流程的关键代码关键代码如下，主要在AbstractChannel类中：
     * @return
     */
    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            // 创建 Channel 对象
            channel = channelFactory.newChannel();
            // 初始化 Channel 配置
            init(channel);
        } catch (Throwable t) {
            if (channel != null) { // 已创建 Channel 对象
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly(); // 强制关闭 Channel
                // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        // 注册 Channel 到 EventLoopGroup 中
        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly(); // 强制关闭 Channel
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return regFuture;
    }

    abstract void init(Channel channel) throws Exception;

    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread() + ": bind");
                // 注册成功，绑定端口
                if (regFuture.isSuccess()) {
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                // 注册失败，回调通知 promise 异常
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    public B handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
        return self();
    }

    /**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * Returns the {@link AbstractBootstrapConfig} object that can be used to obtain the current config
     * of the bootstrap.
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    static <K, V> Map<K, V> copiedMap(Map<K, V> map) {
        final Map<K, V> copied;
        synchronized (map) {
            if (map.isEmpty()) {
                return Collections.emptyMap();
            }
            copied = new LinkedHashMap<K, V>(map);
        }
        return Collections.unmodifiableMap(copied);
    }

    final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    @SuppressWarnings("deprecation")
    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final Map<ChannelOption<?>, Object> options() {
        return copiedMap(options);
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return copiedMap(attrs);
    }

    static void setChannelOptions(
            Channel channel, Map<ChannelOption<?>, Object> options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options.entrySet()) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    static void setChannelOptions(
            Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('(').append(config()).append(')');
        return buf.toString();
    }

    static final class PendingRegistrationPromise extends DefaultChannelPromise {

        // Is set to the correct EventExecutor once the registration was successful. Otherwise it will
        // stay null and so the GlobalEventExecutor.INSTANCE will be used for notifications.
        private volatile boolean registered;

        PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        void registered() {
            registered = true;
        }

        @Override
        protected EventExecutor executor() {
            if (registered) {
                // If the registration was a success executor is set.
                //
                // See https://github.com/netty/netty/issues/2586
                return super.executor();
            }
            // The registration failed so we can only use the GlobalEventExecutor as last resort to notify.
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
