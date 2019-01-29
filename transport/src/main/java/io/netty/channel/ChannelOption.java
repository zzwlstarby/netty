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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractConstant;
import io.netty.util.ConstantPool;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * A {@link ChannelOption} allows to configure a {@link ChannelConfig} in a type-safe
 * way. Which {@link ChannelOption} is supported depends on the actual implementation
 * of {@link ChannelConfig} and may depend on the nature of the transport it belongs
 * to.
 * {@link ChannelOption}允许以类型安全的方式配置{@link ChannelConfig}。
 * 支持哪个{@link ChannelOption}取决于{@link ChannelConfig}的实际实现，并且可能取决于它所属的传输的性质*。
 *
 * @param <T>   the type of the value which is valid for the {@link ChannelOption}
 */

/**
 * 概述：
 *      ChannelOption 主要是用于配置netty中一些Channel相关的参数，
 *      这些参数的key已经在ChannelOption中以静态变量的方式设置好了，可以直接拿来使用，
 *      并且配置相关的value，如果ChannelOption设置了一个不存在的key，就会以日志的形式提示错误信息，但是不会抛出异常。
 *
 *      netty 中在创建ServerBootstrap 时，里面会维护一个生成好的LinkedHashMap, 来保存所有的ChannelOption及对应的值
 *      在ServerBootstrap 中放option时，会将这个option对象，及value存放到这个LinkedHashMap当中。
 *      在serverBootstrap 绑定到具体的端口时，init()方法当中，会去将之前的options的信息，绑定到具体channel中
 *      ChannelOption主要代表channel相关的一些常量
 *
 *      ChannelOption是一种以一种安全的方式配置ChannelConfig，ChannelOption支持的类型个依赖于ChannelConfig的实际类型
 *      和他所属的传输层的本质。
 *
 *      ChannelOption的主要作用是用来存在TCP之类的传输层的一些协议的参数。
 *
 *      ChannelOption不存储值，只存储值得类型.为什么ChannelOption是线程安全的，原因就在于此,
 *      并且ChannelOption不存储值，只是存储值得类型。
 *
 *      ChannelOption是用来配置ChannelConfig的
 *
 * @param <T>
 */
public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>> {

    /**
     * 维护ChannelOption的常量池。
     */
    private static final ConstantPool<ChannelOption<Object>> pool = new ConstantPool<ChannelOption<Object>>() {
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<Object>(id, name);
        }
    };

    /**
     * Returns the {@link ChannelOption} of the specified name.
     * 通过泛型方法，创建一个指定名称的常量。
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(String name) {
        return (ChannelOption<T>) pool.valueOf(name);
    }

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (ChannelOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * Returns {@code true} if a {@link ChannelOption} exists for the given {@code name}.
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * Creates a new {@link ChannelOption} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link ChannelOption} for the given {@code name} exists.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> newInstance(String name) {
        return (ChannelOption<T>) pool.newInstance(name);
    }

    /**
     * 在4.x版本中，UnpooledByteBufAllocator是默认的allocator，尽管其存在某些限制。现在PooledByteBufAllocator
     * 已经广泛使用一段时间，并且我们有了增强的缓冲区泄漏追踪机制， 所以是时候让PooledByteBufAllocator成为默认了。
     * 总结：Netty4使用对象池，重用缓冲区
     */
    public static final ChannelOption<ByteBufAllocator> ALLOCATOR = valueOf("ALLOCATOR");
    public static final ChannelOption<RecvByteBufAllocator> RCVBUF_ALLOCATOR = valueOf("RCVBUF_ALLOCATOR");
    public static final ChannelOption<MessageSizeEstimator> MESSAGE_SIZE_ESTIMATOR = valueOf("MESSAGE_SIZE_ESTIMATOR");

    /**
     *表示客户端调用服务端接口的超时时间。这个设置可以当做一个默认设置,我们在应用层,服务端接口一定要能支持设置超时时间,
     *因为不同的业务服务接口,针对不同场景,超时时间可能是不同的。
     *
     * Netty参数，连接超时毫秒数，默认值30000毫秒即30秒。
     */
    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS = valueOf("CONNECT_TIMEOUT_MILLIS");


    /**
     * @deprecated Use {@link MaxMessagesRecvByteBufAllocator}
     * Netty参数，一次Loop读取的最大消息数，对于ServerChannel或者NioByteChannel，默认值为16，其他Channel默认值为1。
     * 默认值这样设置，是因为：ServerChannel需要接受足够多的连接，保证大吞吐量，NioByteChannel可以减少不必要的系统调
     * 用select。
     */
    @Deprecated
    public static final ChannelOption<Integer> MAX_MESSAGES_PER_READ = valueOf("MAX_MESSAGES_PER_READ");
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT = valueOf("WRITE_SPIN_COUNT");
    /**
     * @deprecated Use {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_HIGH_WATER_MARK = valueOf("WRITE_BUFFER_HIGH_WATER_MARK");
    /**
     * @deprecated Use {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_LOW_WATER_MARK = valueOf("WRITE_BUFFER_LOW_WATER_MARK");
    public static final ChannelOption<WriteBufferWaterMark> WRITE_BUFFER_WATER_MARK =
            valueOf("WRITE_BUFFER_WATER_MARK");

    public static final ChannelOption<Boolean> ALLOW_HALF_CLOSURE = valueOf("ALLOW_HALF_CLOSURE");
    public static final ChannelOption<Boolean> AUTO_READ = valueOf("AUTO_READ");

    /**
     * If {@code true} then the {@link Channel} is closed automatically and immediately on write failure.
     * The default value is {@code true}.
     */
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");

    /**
     *
     *DatagramChannel参数，设置广播模式。
     */
    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");

    /**
     * Channeloption.SO_KEEPALIVE参数对应于套接字选项中的SO_KEEPALIVE，该参数用于设置TCP连接，
     * 当设置该选项以后，连接会测试链接的状态，这个选项用于可能长时间没有数据交流的连接。当设置该
     * 选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文。
     *
     * 当设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文。
     *
     * SO_KEEPALIVE=true,是利用TCP的SO_KEEPALIVE属性,当SO_KEEPALIVE=true的时候,服务端可以探
     * 测客户端的连接是否还存活着,如果客户端因为断电或者网络问题或者客户端挂掉了等,那么服务端的连
     * 接可以关闭掉,释放资源。
     */
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");

    /**
     * DatagramChannel参数
     *
     * ChannelOption.SO_SNDBUF参数对应于套接字选项中的SO_SNDBUF，ChannelOption.SO_RCVBUF参数
     * 对应于套接字选项中的SO_RCVBUF这两个参数用于操作接收缓冲区和发送缓冲区的大小，接收缓冲区用于
     * 保存网络协议站内收到的数据，直到应用程序读取成功，发送缓冲区用于保存发送数据，直到发送成功。
     */
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");

    /**
     *
     * ServerSocketChannel参数：已说明，需要注意的是：当设置值超过64KB时，需要在绑定到本地端口前设置。该值设置的是由
     * ServerSocketChannel使用accept接受的SocketChannel的接收缓冲区。
     */
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");

    /**
     * ServerSocketChannel参数
     *
     * ChanneOption.SO_REUSEADDR对应于套接字选项中的SO_REUSEADDR，这个参数表示允许重复使用本地地址
     * 和端口，比如，某个服务器进程占用了TCP的80端口进行监听，此时再次监听该端口就会返回错误，使用该参
     * 数就可以解决问题，该参数允许共用该端口，这个在服务器程序中比较常使用，比如某个进程非正常退出，该
     * 程序占用的端口可能要被占用一段时间才能允许其他进程使用，而且程序死掉以后，内核一需要一定的时间才
     * 能够释放此端口，不设置SO_REUSEADDR就无法正常使用该端口。
     *
     * 设置SO_REUSEADDR为true,意味着地址可以复用,比如如下场景某个进程占用了80端口,然后重启进程,原来的
     * socket1处于TIME-WAIT状态,进程启动后,使用一个新的socket2,要占用80端口,如果这个时候不设置
     * SO_REUSEADDR=true,那么启动的过程中会报端口已被占用的异常。
     * 注意,这个SO_REUSEADDR是使用serverBootstrap的option方法来设置,而不是使用childOption方法来设置,
     * 要知道具体原因,可以先看李林峰关于netty线程模式。简单来说就是option操作是针对parentGroup的,而childOption是针对childGroup的。
     */
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    /**
     * ChannelOption.SO_LINGER参数对应于套接字选项中的SO_LINGER,Linux内核默认的处理方式是当用户调用
     * close()方法的时候，函数返回，在可能的情况下，尽量发送数据，不一定保证会发生剩余的数据，造成了数据
     * 的不确定性，使用SO_LINGER可以阻塞close()的调用时间，直到数据完全发送
     */
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");

    /**
     * ServerSocketChannel参数
     *
     * ChannelOption.SO_BACKLOG对应的是tcp/ip协议listen函数中的backlog参数，函数listen(int socketfd,int backlog)
     * 用来初始化服务端可连接队列，服务端处理客户端连接请求是顺序处理的，所以同一时间只能处理一个客户端连接，多个客户端来的时候，
     * 服务端将不能处理的客户端连接请求放在队列中等待处理，backlog参数指定了队列的大小
     *
     * Socket参数，服务端接受连接的队列长度，如果队列已满，客户端连接将被拒绝。默认值，Windows为200，其他为128。
     */
    public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");

    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");

    /**
     * 对应IP参数IP_MULTICAST_IF，设置对应地址的网卡为多播模式。
     */
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");

    /**
     *  对应IP参数IP_MULTICAST_IF2，同上但支持IPV6。
     */
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");

    /**
     * IP参数，多播数据报的time-to-live即存活跳数。
     */
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");

    /**
     * 对应IP参数IP_MULTICAST_LOOP，设置本地回环接口的多播功能。由于IP_MULTICAST_LOOP返回True表示关闭，所以
     * Netty加上后缀_DISABLED防止歧义。
     */
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");

    /**
     * ChannelOption.TCP_NODELAY参数对应于套接字选项中的TCP_NODELAY,该参数的使用与Nagle算法有关。Nagle算法是
     * 将小的数据包组装为更大的帧然后进行发送，而不是输入一次发送一次,因此在数据包不足的时候会等待其他数据的到了，
     * 组装成大的数据包进行发送，虽然该方式有效提高网络的有效负载，但是却造成了延时，而该参数的作用就是禁止使用Nagle
     * 算法，使用于小数据即时传输，于TCP_NODELAY相对应的是TCP_CORK，该选项是需要等到发送的数据量最大的时候，一次性
     * 发送数据，适用于文件传输。
     *
     * 如果TCP_NODELAY没有设置为true,那么底层的TCP为了能减少交互次数,会将网络数据积累到一定的数量后,服务器端才发送
     * 出去,会造成一定的延迟。在互联网应用中,通常希望服务是低延迟的,建议将TCP_NODELAY设置为true。
     */
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");

    /**
     *  Netty参数，DatagramChannel注册的EventLoop即表示已激活。
     */
    @Deprecated
    public static final ChannelOption<Boolean> DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION =
            valueOf("DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION");

    public static final ChannelOption<Boolean> SINGLE_EVENTEXECUTOR_PER_GROUP =
            valueOf("SINGLE_EVENTEXECUTOR_PER_GROUP");

    /**
     * Creates a new {@link ChannelOption} with the specified unique {@code name}.
     */
    private ChannelOption(int id, String name) {
        super(id, name);
    }

    @Deprecated
    protected ChannelOption(String name) {
        this(pool.nextId(), name);
    }

    /**
     * Validate the value which is set for the {@link ChannelOption}. Sub-classes
     * may override this for special checks.
     */
    public void validate(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
    }
}
