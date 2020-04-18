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
package io.netty.buffer;

import io.netty.util.ByteProcessor;
import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * A random and sequential accessible sequence of zero or more bytes (octets).
 * This interface provides an abstract view for one or more primitive byte
 * arrays ({@code byte[]}) and {@linkplain ByteBuffer NIO buffers}.
 *
 * <h3>Creation of a buffer</h3>
 *
 * It is recommended to create a new buffer using the helper methods in
 * {@link Unpooled} rather than calling an individual implementation's
 * constructor.
 *
 * <h3>Random Access Indexing</h3>
 *
 * Just like an ordinary primitive byte array, {@link ByteBuf} uses
 * <a href="http://en.wikipedia.org/wiki/Zero-based_numbering">zero-based indexing</a>.
 * It means the index of the first byte is always {@code 0} and the index of the last byte is
 * always {@link #capacity() capacity - 1}.  For example, to iterate all bytes of a buffer, you
 * can do the following, regardless of its internal implementation:
 *
 * <pre>
 * {@link ByteBuf} buffer = ...;
 * for (int i = 0; i &lt; buffer.capacity(); i ++) {
 *     byte b = buffer.getByte(i);
 *     System.out.println((char) b);
 * }
 * </pre>
 *
 * <h3>Sequential Access Indexing</h3>
 *
 * {@link ByteBuf} provides two pointer variables to support sequential
 * read and write operations - {@link #readerIndex() readerIndex} for a read
 * operation and {@link #writerIndex() writerIndex} for a write operation
 * respectively.  The following diagram shows how a buffer is segmented into
 * three areas by the two pointers:
 *
 * <pre>
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      |                   |     (CONTENT)    |                  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 * </pre>
 *
 * <h4>Readable bytes (the actual content)</h4>
 *
 * This segment is where the actual data is stored.  Any operation whose name
 * starts with {@code read} or {@code skip} will get or skip the data at the
 * current {@link #readerIndex() readerIndex} and increase it by the number of
 * read bytes.  If the argument of the read operation is also a
 * {@link ByteBuf} and no destination index is specified, the specified
 * buffer's {@link #writerIndex() writerIndex} is increased together.
 * <p>
 * If there's not enough content left, {@link IndexOutOfBoundsException} is
 * raised.  The default value of newly allocated, wrapped or copied buffer's
 * {@link #readerIndex() readerIndex} is {@code 0}.
 *
 * <pre>
 * // Iterates the readable bytes of a buffer.
 * {@link ByteBuf} buffer = ...;
 * while (buffer.isReadable()) {
 *     System.out.println(buffer.readByte());
 * }
 * </pre>
 *
 * <h4>Writable bytes</h4>
 *
 * This segment is a undefined space which needs to be filled.  Any operation
 * whose name starts with {@code write} will write the data at the current
 * {@link #writerIndex() writerIndex} and increase it by the number of written
 * bytes.  If the argument of the write operation is also a {@link ByteBuf},
 * and no source index is specified, the specified buffer's
 * {@link #readerIndex() readerIndex} is increased together.
 * <p>
 * If there's not enough writable bytes left, {@link IndexOutOfBoundsException}
 * is raised.  The default value of newly allocated buffer's
 * {@link #writerIndex() writerIndex} is {@code 0}.  The default value of
 * wrapped or copied buffer's {@link #writerIndex() writerIndex} is the
 * {@link #capacity() capacity} of the buffer.
 *
 * <pre>
 * // Fills the writable bytes of a buffer with random integers.
 * {@link ByteBuf} buffer = ...;
 * while (buffer.maxWritableBytes() >= 4) {
 *     buffer.writeInt(random.nextInt());
 * }
 * </pre>
 *
 * <h4>Discardable bytes</h4>
 *
 * This segment contains the bytes which were read already by a read operation.
 * Initially, the size of this segment is {@code 0}, but its size increases up
 * to the {@link #writerIndex() writerIndex} as read operations are executed.
 * The read bytes can be discarded by calling {@link #discardReadBytes()} to
 * reclaim unused area as depicted by the following diagram:
 *
 * <pre>
 *  BEFORE discardReadBytes()
 *
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER discardReadBytes()
 *
 *      +------------------+--------------------------------------+
 *      |  readable bytes  |    writable bytes (got more space)   |
 *      +------------------+--------------------------------------+
 *      |                  |                                      |
 * readerIndex (0) <= writerIndex (decreased)        <=        capacity
 * </pre>
 *
 * Please note that there is no guarantee about the content of writable bytes
 * after calling {@link #discardReadBytes()}.  The writable bytes will not be
 * moved in most cases and could even be filled with completely different data
 * depending on the underlying buffer implementation.
 *
 * <h4>Clearing the buffer indexes</h4>
 *
 * You can set both {@link #readerIndex() readerIndex} and
 * {@link #writerIndex() writerIndex} to {@code 0} by calling {@link #clear()}.
 * It does not clear the buffer content (e.g. filling with {@code 0}) but just
 * clears the two pointers.  Please also note that the semantic of this
 * operation is different from {@link ByteBuffer#clear()}.
 *
 * <pre>
 *  BEFORE clear()
 *
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER clear()
 *
 *      +---------------------------------------------------------+
 *      |             writable bytes (got more space)             |
 *      +---------------------------------------------------------+
 *      |                                                         |
 *      0 = readerIndex = writerIndex            <=            capacity
 * </pre>
 *
 * <h3>Search operations</h3>
 *
 * For simple single-byte searches, use {@link #indexOf(int, int, byte)} and {@link #bytesBefore(int, int, byte)}.
 * {@link #bytesBefore(byte)} is especially useful when you deal with a {@code NUL}-terminated string.
 * For complicated searches, use {@link #forEachByte(int, int, ByteProcessor)} with a {@link ByteProcessor}
 * implementation.
 *
 * <h3>Mark and reset</h3>
 *
 * There are two marker indexes in every buffer. One is for storing
 * {@link #readerIndex() readerIndex} and the other is for storing
 * {@link #writerIndex() writerIndex}.  You can always reposition one of the
 * two indexes by calling a reset method.  It works in a similar fashion to
 * the mark and reset methods in {@link InputStream} except that there's no
 * {@code readlimit}.
 *
 * <h3>Derived buffers</h3>
 *
 * You can create a view of an existing buffer by calling one of the following methods:
 * <ul>
 *   <li>{@link #duplicate()}</li>
 *   <li>{@link #slice()}</li>
 *   <li>{@link #slice(int, int)}</li>
 *   <li>{@link #readSlice(int)}</li>
 *   <li>{@link #retainedDuplicate()}</li>
 *   <li>{@link #retainedSlice()}</li>
 *   <li>{@link #retainedSlice(int, int)}</li>
 *   <li>{@link #readRetainedSlice(int)}</li>
 * </ul>
 * A derived buffer will have an independent {@link #readerIndex() readerIndex},
 * {@link #writerIndex() writerIndex} and marker indexes, while it shares
 * other internal data representation, just like a NIO buffer does.
 * <p>
 * In case a completely fresh copy of an existing buffer is required, please
 * call {@link #copy()} method instead.
 *
 * <h4>Non-retained and retained derived buffers</h4>
 *
 * Note that the {@link #duplicate()}, {@link #slice()}, {@link #slice(int, int)} and {@link #readSlice(int)} does NOT
 * call {@link #retain()} on the returned derived buffer, and thus its reference count will NOT be increased. If you
 * need to create a derived buffer with increased reference count, consider using {@link #retainedDuplicate()},
 * {@link #retainedSlice()}, {@link #retainedSlice(int, int)} and {@link #readRetainedSlice(int)} which may return
 * a buffer implementation that produces less garbage.
 *
 * <h3>Conversion to existing JDK types</h3>
 *
 * <h4>Byte array</h4>
 *
 * If a {@link ByteBuf} is backed by a byte array (i.e. {@code byte[]}),
 * you can access it directly via the {@link #array()} method.  To determine
 * if a buffer is backed by a byte array, {@link #hasArray()} should be used.
 *
 * <h4>NIO Buffers</h4>
 *
 * If a {@link ByteBuf} can be converted into an NIO {@link ByteBuffer} which shares its
 * content (i.e. view buffer), you can get it via the {@link #nioBuffer()} method.  To determine
 * if a buffer can be converted into an NIO buffer, use {@link #nioBufferCount()}.
 *
 * <h4>Strings</h4>
 *
 * Various {@link #toString(Charset)} methods convert a {@link ByteBuf}
 * into a {@link String}.  Please note that {@link #toString()} is not a
 * conversion method.
 *
 * <h4>I/O Streams</h4>
 *
 * Please refer to {@link ByteBufInputStream} and
 * {@link ByteBufOutputStream}.
 */
@SuppressWarnings("ClassMayBeInterface")

/**
 * 概述：
 * Netty提供的ByteBuf与JDK的ByteBuffer相比，前者具有卓越的功能性和灵活性。
 *
 *
 *
 * 1.ByteBuf 是一个字节容器，内部是一个字节数组。
 * 从逻辑上来分，字节容器内部，可以分为四个部分：
 * 第一个部分是已经丢弃的字节，这部分数据是无效的；
 * 第二部分是可读字节，这部分数据是 ByteBuf 的主体数据， 从 ByteBuf 里面读取的数据都来自这一部分;
 * 第三部分的数据是可写字节，所有写到 ByteBuf 的数据都会写到这一段。
 * 第四部分的字节，表示的是该 ByteBuf 最多还能扩容的大小。
 *
 * 2.ByteBuf 的三个指针
 * ByteBuf 通过三个整型的指针（index），有效地区分可读数据和可写数据，使得读写之间相互没有冲突。
 * 这个三个指针，分别是：
 *    readerIndex（读指针）: 指示读取的起始位置。每读取一个字节，readerIndex 自增1 。一旦 readerIndex 与 writerIndex 相等，ByteBuf 不可读 。
 *    writerIndex（写指针）:指示写入的起始位置。每写一个字节，writerIndex 自增1。一旦增加到 writerIndex 与 capacity（） 容量相等，表示 ByteBuf 已经不可写了 。
 *                         capacity（）容量不是一个成员属性，是一个成员方法。表示 ByteBuf 内部的总容量。 注意，这个不是最大容量。
 *    maxCapacity（最大容量）: 这三个指针，是三个int 型的成员属性，定义在 AbstractByteBuf 抽象基类中。
 *                            指示可以 ByteBuf 扩容的最大容量。当向 ByteBuf 写数据的时候，如果容量不足，可以进行扩容。扩容的最大限度，
 *                            直到 capacity（） 扩容到 maxCapacity为止，超过 maxCapacity 就会报错。capacity（）扩容的操作，是底层自动进行的。
 * 3.Netty ByteBuf 优势（https://blog.csdn.net/crazymakercircle/article/details/84198042）
 * Netty 提供了ByteBuf，来替代Java NIO的 ByteBuffer 缓，来操纵内存缓冲区。
 * 与Java NIO的 ByteBuffer 相比，ByteBuf的优势如下：
 *   1>Pooling (池化，这点减少了内存复制和GC，提升效率)
 *   2>可以自定义缓冲类型
 *   3>通过一个内置的复合缓冲类型实现零拷贝
 *   4>扩展性好，比如 StringBuffer
 *   5>不需要调用 flip()来切换读/写模式
 *   6>读取和写入索引分开
 *   7>方法链
 *   8>引用计数
 *
 * 手动获取与释放ByteBuf
 *    Netty环境下，业务处理的代码，基本上都在Handler处理器中的各个入站和出站方法中。
 *    一般情况下，采用如下方法获取一个Java 堆中的缓冲区：ByteBuf heapBuffer = ctx.alloc().heapBuffer();
 *    使用完成后，通过如下的方法，释放缓冲区：ReferenceCountUtil.release(heapBuffer );
 *    上面的代码很简单，通过release方法减去 heapBuffer 的使用计数，Netty 会自动回收 heapBuffer 。
 *    缓冲区内存的回收、二次分配等管理工作，是 Netty 自动完成的。
 *
 *
 * 自动获取和释放 ByteBuf
 * 方式一：TailHandler 自动释放
 * ​ Netty默认会在ChannelPipline的最后添加的那个 TailHandler 帮你完成 ByteBuf的release。
 * ​ 先看看，自动创建的ByteBuf实例是如何登场的？
 * ​ Netty自动创建 ByteBuf实例
 *   Netty 的 Reactor 线程会在 AbstractNioByteChannel.NioByteUnsafe.read() 处调用 ByteBufAllocator创建ByteBuf实例，将TCP缓冲区的数据读取到 Bytebuf 实例中，并调用 pipeline.fireChannelRead(byteBuf) 进入pipeline 入站处理流水线。
 *
 *   默认情况下，TailHandler自动释放掉ByteBuf实例
 *    Netty的ChannelPipleline的流水线的末端是TailHandler，默认情况下如果每个入站处理器Handler都把消息往下传，TailHandler会释放掉ReferenceCounted类型的消息。
 *
 * 方式二：SimpleChannelInboundHandler 自动释放
 *​ 如果业务Handler需要将 ChannelPipleline的流水线的默认处理流程截断，不进行后边的inbound入站处理操作,这时候末端 TailHandler自动释放缓冲区的工作，自然就失效了。
 * ​ 这种场景下，业务Handler 有两种选择：
 *        1>手动释放 ByteBuf 实例
 *        2>继承 SimpleChannelInboundHandler，利用它的自动释放功能。
 *
 * 方式三：HeadHandler 自动释放
 *  出站处理流程中，申请分配到的 ByteBuf，通过 HeadHandler 完成自动释放。
 *  出站处理用到的 Bytebuf 缓冲区，一般是要发送的消息，通常由应用所申请。在出站流程开始的时候，通过调用 ctx.writeAndFlush(msg)，Bytebuf
 *  缓冲区开始进入出站处理的 pipeline 流水线 。在每一个出站Handler中的处理完成后，最后消息会来到出站的最后一棒 HeadHandler，再经过一轮复杂的调用，
 *  在flush完成后终将被release掉。(强调一下，HeadContext （HeadHandler）是出站处理流程的最后一棒。)
 *
 * 如何避免内存泄露
 *   基本上，在 Netty的开发中，通过 ChannelHandlerContext 或 Channel 获取的缓冲区ByteBuf 默认都是Pooled，所以需要再合适的时机对其进行释放，避免造成内存泄漏。
 *
 * 自动释放的注意事项
 *   我们已经知道了三种自动释放方法：
 *       1>通过 TailHandler 自动释放入站 ByteBuf
 *       2>继承 SimpleChannelInboundHandler 的完成 入站ByteBuf 自动释放
 *       3>通过HeadHandler自动释放出站 ByteBuf
 *
 * 自动释放，注意事项如下：
 *       1>入站处理流程中，如果对原消息不做处理，默认会调用 ctx.fireChannelRead(msg) 把原消息往下传，由流水线最后一棒 TailHandler 完成自动释放。
 *       2>如果截断了入站处理流水线，则可以继承 SimpleChannelInboundHandler ，完成入站ByteBuf 自动释放。
 *       3>出站处理过程中，申请分配到的 ByteBuf，通过 HeadHandler 完成自动释放。
 *       4>出站处理用到的 Bytebuf 缓冲区，一般是要发送的消息，通常由应用所申请。在出站流程开始的时候，通过调用 ctx.writeAndFlush(msg)，Bytebuf 缓冲区开始进入出站处理的 pipeline 流水线 。在每一个出站Handler中的处理完成后，最后消息会来到出站的最后一棒 HeadHandler，再经过一轮复杂的调用，在flush完成后终将被release掉。
 *
 * 手动释放的注意事项(手动释放是自动释放的重要补充和辅助。)
 *  手动释放操作，大致有如下注意事项：
 *      1>入站处理中，如果将原消息转化为新的消息并调用 ctx.fireChannelRead(newMsg)往下传，那必须把原消息release掉;
 *      2>入站处理中，如果已经不再调用 ctx.fireChannelRead(msg) 传递任何消息，也没有继承SimpleChannelInboundHandler 完成自动释放，那更要把原消息release掉;
 *      3>多层的异常处理机制，有些异常处理的地方不一定准确知道ByteBuf之前释放了没有，可以在释放前加上引用计数大于0的判断避免异常； 有时候不清楚ByteBuf被引用了多少次，但又必须在此进行彻底的释放，可以循环调用reelase()直到返回true。
 *
 *  特别需要强调的，是上边的第一种情况。如果在入站处理的 handlers 传递过程中，传递了新的ByteBuf 值，老ByteBuf 值需要自己手动释放。老的ByteBuf 值，就是从pipeline流水线入口传递过来的 ByteBuf 实例。
 *  总之，只要是在传递过程中，没有传递下去的ByteBuf就需要手动释放，避免不必要的内存泄露。
 *
 *
 * 缓冲区 Allocator 分配器
 *    Netty通过 ByteBufAllocator分配缓冲区。Netty提供了ByteBufAllocator的两种实现：PoolByteBufAllocator和UnpooledByteBufAllocator。
 *    前者将ByteBuf实例放入池中，提高了性能，将内存碎片减少到最小。这个实现采用了一种内存分配的高效策略，称为 jemalloc。它已经被好几种现代操
 *    作系统所采用。后者则没有把ByteBuf放入池中，每次被调用时，返回一个新的ByteBuf实例。
 *
 * 分配器 Allocator的类型
 *    PooledByteBufAllocator：可以重复利用之前分配的内存空间。
 *          为了减少内存的分配回收以及产生的内存碎片，Netty提供了PooledByteBufAllocator 用来分配可回收的ByteBuf，可以把PooledByteBufAllocator 看做一个池子，需要的时候从里面获取ByteBuf，用完了放回去，以此提高性能。
 *
 *    UnpooledByteBufAllocator：不可重复利用，由JVM GC负责回收。
 *          顾名思义Unpooled就是不会放到池子里，所以根据该分配器分配的ByteBuf，不需要放回池子，由JVM自己GC回收。
 *
 *    这两个类，都是AbstractByteBufAllocator的子类，AbstractByteBufAllocator实现了一个接口，叫做ByteBufAllocator。
 *
 *
 *    可以做一个对比试验：
 *           使用UnpooledByteBufAllocator的方式创建ByteBuf的时候，单台24核CPU的服务器，16G内存，刚启动时候，10000个长连接，每秒所有的连接发一条消息，短时间内，可以看到内存占到10G多点，但随着系统的运行，内存不断增长，直到整个系统内存溢出挂掉。
 *           把UnpooledByteBufAllocator换成PooledByteBufAllocator，通过试验，内存使用量机器能维持在一个连接占用1M左右，内存在10G左右，经常长期的运行测试，发现都能维持在这个数量，系统内存不会崩溃。
 *
 *默认的分配器
 *    默认的分配器 ByteBufAllocator.DEFAULT ，可以通过 Java 系统参数（SystemProperty ）选项 io.netty.allocator.type 去配置，使用字符串值：“unpooled”，“pooled”。
 *    是4.0版本的源码，默认为UnpooledByteBufAllocator。而4.1 版本，默认为 PooledByteBufAllocator。因此，4.1版本的代码，是和上面的代码稍微有些不同的。
 *
 *设置通道Channel的分配器
 *    在4.x版本中，UnpooledByteBufAllocator是默认的allocator，尽管其存在某些限制。
 *    现在PooledByteBufAllocator已经广泛使用一段时间，并且我们有了增强的缓冲区泄漏追踪机制，所以是时候让PooledByteBufAllocator成为默认了。
 *     ServerBootstrap b = new ServerBootstrap()
 *         .group(bossGroup, workerGroup)
 *         .channel(NioServerSocketChannel.class)
 *         .localAddress(port)
 *         .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
 *         .childHandler(new ChannelInitializer<SocketChannel>() {
 *             @Override
 *             public void initChannel(SocketChannel ch) throws Exception {
 *                 ch.pipeline().addLast(...);
 *             }
 *         });
 *
 *     使用Netty带来的又一个好处就是内存管理。只需一行简单的配置，就能获得到内存池带来的好处。在底层，Netty实现了一个Java版的Jemalloc内存管理库，为我们做完了所有“脏活累活”！
 *
 *缓冲区内存的类型(说完了分配器的类型，再来说下缓冲区的类型)
 *     依据内存的管理方不同，分为堆缓存和直接缓存。也就是Heap ByteBuf 和 Direct ByteBuf。另外，为了方便缓冲区进行组合，提供了一种组合缓存区。
 *
 *三种缓冲区的介绍如下：
 *    堆缓冲区:
 *            概述：数据存存储在JVM的堆空间中，又称为支撑数组，通过 hasArray 来判断是不是在堆缓冲区中
 *            优点:没使用池化情况下能提供快速的分配和释放
 *            劣势:发送之前都会拷贝到直接缓冲区
 *    直接缓冲区：
 *            概述：存储在物理内存中
 *            优点：能获取超过jvm堆限制大小的空间；写入channel比堆缓冲区更快
 *            劣势：释放和分配空间昂贵(使用系统的方法) ；操作时需要复制一次到堆上
 *    复合缓冲：
 *            概述：单个缓冲区合并多个缓冲区表示
 *            优点：操作多个更方便
 *
 *    上面三种缓冲区的类型，无论哪一种，都可以通过池化、非池化的方式，去获取。
 *
 *Unpooled 非池化缓冲区的使用方法
 *    Unpooled也是用来创建缓冲区的工具类，Unpooled 的使用也很容易。
 *    //创建复合缓冲区
 *    CompositeByteBuf compBuf = Unpooled.compositeBuffer();
 *    //创建堆缓冲区
 *    ByteBuf heapBuf = Unpooled.buffer(8);
 *    //创建直接缓冲区
 *    ByteBuf directBuf = Unpooled.directBuffer(16);
 *
 *    Unpooled类的应用场景：Unpooled类让ByteBuf也同样适用于不需要其他的Netty组件的、无网络操作的项目，这些项目可以从这个高性能的、可扩展的buffer API中获益。
 *
 *
 * Netty 的 ByteBuf 的内存回收工作，是通过引用计数的方式管理的。
 *   大致的引用计数的规则如下：
     * 默认情况下，当创建完一个 ByteBuf 时，它的引用为1。
     * 每次调用 retain()方法， 它的引用就加 1 ；
     * 每次调用 release() 方法，是将引用计数减 1。
     * 如果引用为0，再次访问这个 ByteBuf 对象，将会抛出异常。
     *
     * 如果引用为0，表示这个 ByteBuf 没有地方被引用到，需要回收内存。
     * Netty的内存回收分为两种情况：
     * Pooled 池化的内存，放入可以重新分配的 ByteBuf 池子，等待下一次分配。
     * Unpooled 未池化的 ByteBuf 内存，确保GC 可达，确保 能被 JVM 的 GC 回收器回收到。
     *
 *
 *
 * ByteBuf 的浅层复制
 *    ByteBuf 的浅层复制分为两种，有切片slice 浅层复制，和duplicate 浅层复制。
 *
 *    slice 切片浅层复制:
 *          首先说明一下，这是一种非常重要的操作。可以很大程度的避免内存拷贝。这一点，对于大规模消息通讯来说，是非常重要的。
 *          slice 操作可以获取到一个 ByteBuf 的一个切片。一个ByteBuf，可以进行多次的切片操作，多个切片可以共享一个存储区域的 ByteBuf 对象。
 *
 *          slice 操作方法有两个重载版本：
 *                public ByteBuf slice():不带参数的 slice 方法，等同于 buf.slice(buf.readerIndex(), buf.readableBytes()) 调用, 即返回 ByteBuf 实例中可读部分的切片。
 *                public ByteBuf slice(int index, int length):而带参数 slice(int index, int length) 方法，可以通过灵活的设置不同的参数，来获取到 buf 的不同区域的切片。
 *                两个版本有非常紧密的联系。
 *                调用slice（）方法后，返回的 ByteBuf 的切片
 *
 *          调用slice（）方法后，返回的ByteBuf 切片的属性，大致如下：
 *                     slice 的 readerIndex（读指针）的值为 0
 *                     slice 的 writerIndex（写指针） 的 值为源Bytebuf的 readableBytes() 可读字节数。
 *                     slice 的 maxCapacity（最大容量） 的值为源Bytebuf的 readableBytes() 可读字节数。maxCapacity 与 writerIndex 值相同，切片不可以写。
 *                     切片的可读字节数，为自己的 writerIndex - readerIndex。所有，切片和源Bytebuf的 readableBytes() 可读字节数相同。
 *                     也就是说，切片可读，不可写。
 *         slice（）切片和原ByteBuf的联系：
 *                     切片不会拷贝原ByteBuf底层数据，底层数组和原ByteBuf的底层数组是同一个
 *                     切片不会改变原 ByteBuf 的引用计数。
 *
 *          根本上，调用slice（）方法生成的切片，是 源Bytebuf 可读部分的浅层复制。
 *
 *    duplicate() 浅层复制
 *           duplicate() 返回的是源ByteBuf 的整个对象的一个浅层复制，包括如下内容：
 *                       duplicate() 会创建自己的读写指针，但是值与源ByteBuf 的读写指针相同；
 *                       duplicate() 不会改变源 ByteBuf 的引用计数
 *                       duplicate() 不会拷贝 源ByteBuf 的底层数据
 *
 *    duplicate() 和slice() 方法，都是浅层复制。不同的是，slice() 方法是切取一段的浅层复制，duplicate() 是整个的浅层复制。
 *
 *    浅层复制的问题
 *       浅层复制方法不会拷贝数据，也不会改变 ByteBuf 的引用计数，这就会导致一个问题。
 * ​     在源 ByteBuf 调用 release() 之后，引用计数为零，变得不能访问。这个时候，源 ByteBuf 的浅层复制实例，也不能进行读写。如果再对浅层复制实例进行读写，就会报错。
 * ​     因此，在调用浅层复制实例时，可以通过调用一次 retain() 方法 来增加引用，表示它们对应的底层的内存多了一次引用，引用计数为2，在浅层复制实例用完后，需要调用两次 release() 方法，将引用计数减一，不影响源ByteBuf的内存释放。
 *
 * */
public abstract class ByteBuf implements ReferenceCounted, Comparable<ByteBuf> {

    /**
     * Returns the number of bytes (octets) this buffer can contain.
     * capacity（）容量不是一个成员属性，是一个成员方法。表示 ByteBuf 内部的总容量。 注意，这个不是最大容量。
     * capacity（）扩容的操作，是底层自动进行的。
     *
     * 表示 ByteBuf 的容量，包括丢弃的字节数、可读字节数、可写字节数。
     */
    public abstract int capacity(); // 容量

    /**
     * Adjusts the capacity of this buffer.  If the {@code newCapacity} is less than the current
     * capacity, the content of this buffer is truncated.  If the {@code newCapacity} is greater
     * than the current capacity, the buffer is appended with unspecified data whose length is
     * {@code (newCapacity - currentCapacity)}.
     */
    public abstract ByteBuf capacity(int newCapacity);

    /**
     * Returns the maximum allowed capacity of this buffer.  If a user attempts to increase the
     * capacity of this buffer beyond the maximum capacity using {@link #capacity(int)} or
     * {@link #ensureWritable(int)}, those methods will raise an
     * {@link IllegalArgumentException}.
     *
     * 指示可以 ByteBuf 扩容的最大容量。
     * 当向 ByteBuf 写数据的时候，如果容量不足，可以进行扩容。
     * 扩容的最大限度，直到 capacity（） 扩容到 maxCapacity为止，超过 maxCapacity 就会报错。
     *
     *
     * 表示 ByteBuf 底层最大能够占用的最大字节数。当向 ByteBuf 中写数据的时候，如果发现容量不足，则进行扩容，直到扩容到 maxCapacity。
     */
    public abstract int maxCapacity(); // 最大容量

    /**
     * Returns the {@link ByteBufAllocator} which created this buffer.
     */
    public abstract ByteBufAllocator alloc(); // 分配器，用于创建 ByteBuf 对象。

    /**
     * Returns the <a href="http://en.wikipedia.org/wiki/Endianness">endianness</a>
     * of this buffer.
     *
     * @deprecated use the Little Endian accessors, e.g. {@code getShortLE}, {@code getIntLE}
     * instead of creating a buffer with swapped {@code endianness}.
     */
    @Deprecated
    public abstract ByteOrder order(); // 字节序，即大小端。推荐阅读 http://www.ruanyifeng.com/blog/2016/11/byte-order.html

    /**
     * Returns a buffer with the specified {@code endianness} which shares the whole region,
     * indexes, and marks of this buffer.  Modifying the content, the indexes, or the marks of the
     * returned buffer or this buffer affects each other's content, indexes, and marks.  If the
     * specified {@code endianness} is identical to this buffer's byte order, this method can
     * return {@code this}.  This method does not modify {@code readerIndex} or {@code writerIndex}
     * of this buffer.
     *
     * @deprecated use the Little Endian accessors, e.g. {@code getShortLE}, {@code getIntLE}
     * instead of creating a buffer with swapped {@code endianness}.
     */
    @Deprecated
    public abstract ByteBuf order(ByteOrder endianness);

    /**
     * Return the underlying buffer instance if this buffer is a wrapper of another buffer.
     *
     * @return {@code null} if this buffer is not a wrapper
     */
    public abstract ByteBuf unwrap(); // 获得被包装( wrap )的 ByteBuf 对象。

    /**
     * Returns {@code true} if and only if this buffer is backed by an
     * NIO direct buffer.
     */
    public abstract boolean isDirect(); // 是否 NIO Direct Buffer

    /**
     * Returns {@code true} if and only if this buffer is read-only.
     */
    public abstract boolean isReadOnly(); // 是否为只读 Buffer

    /**
     * Returns a read-only version of this buffer.
     */
    public abstract ByteBuf asReadOnly();

    /**
     * Returns the {@code readerIndex} of this buffer.
     * 指示读取的起始位置。
     * 每读取一个字节，readerIndex 自增1 。一旦 readerIndex 与 writerIndex 相等，ByteBuf 不可读 。
     */
    public abstract int readerIndex(); // 读取位置（读指针）

    /**
     * Sets the {@code readerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerIndex} is
     *            less than {@code 0} or
     *            greater than {@code this.writerIndex}
     *
     *
     */
    public abstract ByteBuf readerIndex(int readerIndex);//读指针

    /**
     * Returns the {@code writerIndex} of this buffer.
     * 指示写入的起始位置。
     * 每写一个字节，writerIndex 自增1。一旦增加到 writerIndex 与 capacity（） 容量相等，表示 ByteBuf 已经不可写了 。
     *
     */
    public abstract int writerIndex(); // 写入位置 （写指针）

    /**
     * Sets the {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code writerIndex} is
     *            less than {@code this.readerIndex} or
     *            greater than {@code this.capacity}
     */
    public abstract ByteBuf writerIndex(int writerIndex);

    /**
     * Sets the {@code readerIndex} and {@code writerIndex} of this buffer
     * in one shot.  This method is useful when you have to worry about the
     * invocation order of {@link #readerIndex(int)} and {@link #writerIndex(int)}
     * methods.  For example, the following code will fail:
     *
     * <pre>
     * // Create a buffer whose readerIndex, writerIndex and capacity are
     * // 0, 0 and 8 respectively.
     * {@link ByteBuf} buf = {@link Unpooled}.buffer(8);
     *
     * // IndexOutOfBoundsException is thrown because the specified
     * // readerIndex (2) cannot be greater than the current writerIndex (0).
     * buf.readerIndex(2);
     * buf.writerIndex(4);
     * </pre>
     *
     * The following code will also fail:
     *
     * <pre>
     * // Create a buffer whose readerIndex, writerIndex and capacity are
     * // 0, 8 and 8 respectively.
     * {@link ByteBuf} buf = {@link Unpooled}.wrappedBuffer(new byte[8]);
     *
     * // readerIndex becomes 8.
     * buf.readLong();
     *
     * // IndexOutOfBoundsException is thrown because the specified
     * // writerIndex (4) cannot be less than the current readerIndex (8).
     * buf.writerIndex(4);
     * buf.readerIndex(2);
     * </pre>
     *
     * By contrast, this method guarantees that it never
     * throws an {@link IndexOutOfBoundsException} as long as the specified
     * indexes meet basic constraints, regardless what the current index
     * values of the buffer are:
     *
     * <pre>
     * // No matter what the current state of the buffer is, the following
     * // call always succeeds as long as the capacity of the buffer is not
     * // less than 4.
     * buf.setIndex(2, 4);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerIndex} is less than 0,
     *         if the specified {@code writerIndex} is less than the specified
     *         {@code readerIndex} or if the specified {@code writerIndex} is
     *         greater than {@code this.capacity}
     */
    public abstract ByteBuf setIndex(int readerIndex, int writerIndex); // 设置读取和写入位置

    /**
     * Returns the number of readable bytes which is equal to
     * {@code (this.writerIndex - this.readerIndex)}.
     *
     * 返回表示 ByteBuf 当前可读取的字节数，它的值等于 writerIndex - readerIndex 。
     */
    public abstract int readableBytes(); // 剩余可读字节数

    /**
     * Returns the number of writable bytes which is equal to
     * {@code (this.capacity - this.writerIndex)}.
     *
     * 返回表示 ByteBuf 当前可写入的字节数，它的值等于 capacity（）- writerIndex。
     */
    public abstract int writableBytes(); // 剩余可写字节数

    /**
     * Returns the maximum possible number of writable bytes, which is equal to
     * {@code (this.maxCapacity - this.writerIndex)}.
     * 返回可写的最大字节数，它的值等于 maxCapacity-writerIndex 。
     */
    public abstract int maxWritableBytes();

    /**
     * Returns {@code true}
     * if and only if {@code (this.writerIndex - this.readerIndex)} is greater
     * than {@code 0}.
     *
     * 表示 ByteBuf 是否可读。如果 writerIndex 指针的值大于 readerIndex 指针的值 ，则表示可读。否则为不可写。
     */
    public abstract boolean isReadable();

    /**
     * Returns {@code true} if and only if this buffer contains equal to or more than the specified number of elements.
     */
    public abstract boolean isReadable(int size);

    /**
     * Returns {@code true}
     * if and only if {@code (this.capacity - this.writerIndex)} is greater
     * than {@code 0}.
     *
     * 表示 ByteBuf 是否可写。如果 capacity（） 容量大于 writerIndex 指针的位置 ，则表示可写。否则为不可写。
     * 注意：如果 isWritable() 返回 false，并不代表不能往 ByteBuf 中写数据了。 如果Netty发现往 ByteBuf 中写数据写不进去的话，会自动扩容 ByteBuf。
     */
    public abstract boolean isWritable();

    /**
     * Returns {@code true} if and only if this buffer has enough room to allow writing the specified number of
     * elements.
     */
    public abstract boolean isWritable(int size);

    /**
     * Sets the {@code readerIndex} and {@code writerIndex} of this buffer to
     * {@code 0}.
     * This method is identical to {@link #setIndex(int, int) setIndex(0, 0)}.
     * <p>
     * Please note that the behavior of this method is different
     * from that of NIO buffer, which sets the {@code limit} to
     * the {@code capacity} of the buffer.
     */
    public abstract ByteBuf clear(); // 清空字节空间。实际是修改 readerIndex=writerIndex=0，标记清空。

    /**
     * Marks the current {@code readerIndex} in this buffer.  You can
     * reposition the current {@code readerIndex} to the marked
     * {@code readerIndex} by calling {@link #resetReaderIndex()}.
     * The initial value of the marked {@code readerIndex} is {@code 0}.
     *
     * 表示把当前的写指针writerIndex 保存在 markedWriterIndex 属性中；
     */
    public abstract ByteBuf markReaderIndex(); // 标记读取位置

    /**
     * Repositions the current {@code readerIndex} to the marked
     * {@code readerIndex} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the current {@code writerIndex} is less than the marked
     *         {@code readerIndex}
     *
     * 表示把当前的写指针 writerIndex 恢复到之前保存的 markedWriterIndex 值 。
     */
    public abstract ByteBuf resetReaderIndex();

    /**
     * Marks the current {@code writerIndex} in this buffer.  You can
     * reposition the current {@code writerIndex} to the marked
     * {@code writerIndex} by calling {@link #resetWriterIndex()}.
     * The initial value of the marked {@code writerIndex} is {@code 0}.
     */
    public abstract ByteBuf markWriterIndex(); // 标记写入位置

    /**
     * Repositions the current {@code writerIndex} to the marked
     * {@code writerIndex} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the current {@code readerIndex} is greater than the marked
     *         {@code writerIndex}
     */
    public abstract ByteBuf resetWriterIndex();

    /**
     * Discards the bytes between the 0th index and {@code readerIndex}.
     * It moves the bytes between {@code readerIndex} and {@code writerIndex}
     * to the 0th index, and sets {@code readerIndex} and {@code writerIndex}
     * to {@code 0} and {@code oldWriterIndex - oldReaderIndex} respectively.
     * <p>
     * Please refer to the class documentation for more detailed explanation.
     */
    public abstract ByteBuf discardReadBytes(); // 释放已读的字节空间

    /**
     * Similar to {@link ByteBuf#discardReadBytes()} except that this method might discard
     * some, all, or none of read bytes depending on its internal implementation to reduce
     * overall memory bandwidth consumption at the cost of potentially additional memory
     * consumption.
     */
    public abstract ByteBuf discardSomeReadBytes(); // 释放部分已读的字节空间

    /**
     * Makes sure the number of {@linkplain #writableBytes() the writable bytes}
     * is equal to or greater than the specified value.  If there is enough
     * writable bytes in this buffer, this method returns with no side effect.
     * Otherwise, it raises an {@link IllegalArgumentException}.
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @throws IndexOutOfBoundsException
     *         if {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}
     */
    public abstract ByteBuf ensureWritable(int minWritableBytes);

    /**
     * Tries to make sure the number of {@linkplain #writableBytes() the writable bytes}
     * is equal to or greater than the specified value.  Unlike {@link #ensureWritable(int)},
     * this method does not raise an exception but returns a code.
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @param force
     *        When {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}:
     *        <ul>
     *        <li>{@code true} - the capacity of the buffer is expanded to {@link #maxCapacity()}</li>
     *        <li>{@code false} - the capacity of the buffer is unchanged</li>
     *        </ul>
     * @return {@code 0} if the buffer has enough writable bytes, and its capacity is unchanged.
     *         {@code 1} if the buffer does not have enough bytes, and its capacity is unchanged.
     *         {@code 2} if the buffer has enough writable bytes, and its capacity has been increased.
     *         {@code 3} if the buffer does not have enough bytes, but its capacity has been
     *                   increased to its maximum.
     */
    public abstract int ensureWritable(int minWritableBytes, boolean force);

    /**
     * Gets a boolean at the specified absolute (@code index) in this buffer.
     * This method does not modify the {@code readerIndex} or {@code writerIndex}
     * of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract boolean getBoolean(int index); // Boolean

    /**
     * Gets a byte at the specified absolute {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     *
     *  getTYPE(TYPE value）基础类型读取，不改变指针值
     * 基础数据类型的读取，可以读取 8大基础类型。
     * 具体如下：getByte()、 getBoolean()、getChar()、getShort()、getInt()、getLong()、getFloat()、getDouble() ，从 ByteBuf读取对应的基础类型的数据。
     *
     * getType 系列与readTYPE系列的不同：
     * getType 系列 不会 改变读指针 readerIndex ；
     * readTYPE系列 会 改变读指针 readerIndex 的值。
     */
    public abstract byte  getByte(int index);

    /**
     * Gets an unsigned byte at the specified absolute {@code index} in this
     * buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract short getUnsignedByte(int index);

    /**
     * Gets a 16-bit short integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract short getShort(int index);

    /**
     * Gets a 16-bit short integer at the specified absolute {@code index} in
     * this buffer in Little Endian Byte Order. This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract short getShortLE(int index);

    /**
     * Gets an unsigned 16-bit short integer at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract int getUnsignedShort(int index);

    /**
     * Gets an unsigned 16-bit short integer at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract int getUnsignedShortLE(int index);

    /**
     * Gets a 24-bit medium integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int   getMedium(int index);

    /**
     * Gets a 24-bit medium integer at the specified absolute {@code index} in
     * this buffer in the Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int getMediumLE(int index);

    /**
     * Gets an unsigned 24-bit medium integer at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int   getUnsignedMedium(int index);

    /**
     * Gets an unsigned 24-bit medium integer at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int   getUnsignedMediumLE(int index);

    /**
     * Gets a 32-bit integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract int   getInt(int index);

    /**
     * Gets a 32-bit integer at the specified absolute {@code index} in
     * this buffer with Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract int   getIntLE(int index);

    /**
     * Gets an unsigned 32-bit integer at the specified absolute {@code index}
     * in this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract long  getUnsignedInt(int index);

    /**
     * Gets an unsigned 32-bit integer at the specified absolute {@code index}
     * in this buffer in Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract long  getUnsignedIntLE(int index);

    /**
     * Gets a 64-bit long integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract long  getLong(int index);

    /**
     * Gets a 64-bit long integer at the specified absolute {@code index} in
     * this buffer in Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract long  getLongLE(int index);

    /**
     * Gets a 2-byte UTF-16 character at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract char  getChar(int index);

    /**
     * Gets a 32-bit floating point number at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract float getFloat(int index);

    /**
     * Gets a 32-bit floating point number at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public float getFloatLE(int index) {
        return Float.intBitsToFloat(getIntLE(index));
    }

    /**
     * Gets a 64-bit floating point number at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract double getDouble(int index);

    /**
     * Gets a 64-bit floating point number at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public double getDoubleLE(int index) {
        return Double.longBitsToDouble(getLongLE(index));
    }

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index} until the destination becomes
     * non-writable.  This method is basically same with
     * {@link #getBytes(int, ByteBuf, int, int)}, except that this
     * method increases the {@code writerIndex} of the destination by the
     * number of the transferred bytes while
     * {@link #getBytes(int, ByteBuf, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.writableBytes} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.  This method is basically same
     * with {@link #getBytes(int, ByteBuf, int, int)}, except that this
     * method increases the {@code writerIndex} of the destination by the
     * number of the transferred bytes while
     * {@link #getBytes(int, ByteBuf, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code length} is greater than {@code dst.writableBytes}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex}
     * of both the source (i.e. {@code this}) and the destination.
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.length} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, byte[] dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex}
     * of this buffer.
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.length}
     */
    public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index} until the destination's position
     * reaches its limit.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer while the destination's {@code position} will be increased.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.remaining()} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuffer dst);

    /**
     * Transfers this buffer's data to the specified stream starting at the
     * specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than
     *            {@code this.capacity}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract ByteBuf getBytes(int index, OutputStream out, int length) throws IOException;

    /**
     * Transfers this buffer's data to the specified channel starting at the
     * specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than
     *            {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int getBytes(int index, GatheringByteChannel out, int length) throws IOException;

    /**
     * Transfers this buffer's data starting at the specified absolute {@code index}
     * to the specified channel starting at the given file position.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer. This method does not modify the channel's position.
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than
     *            {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int getBytes(int index, FileChannel out, long position, int length) throws IOException;

    /**
     * Gets a {@link CharSequence} with the given length at the given index.
     *
     * @param length the length to read
     * @param charset that should be used
     * @return the sequence
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract CharSequence getCharSequence(int index, int length, Charset charset);

    /**
     * Sets the specified boolean at the specified absolute {@code index} in this
     * buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setBoolean(int index, boolean value);

    /**
     * Sets the specified byte at the specified absolute {@code index} in this
     * buffer.  The 24 high-order bits of the specified value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     * setTYPE(TYPE value）基础类型写入，不改变指针值
     * 基础数据类型的写入，包含了 8大基础类型的写入。
     * 具体如下：setByte()、 setBoolean()、setChar()、setShort()、setInt()、setLong()、setFloat()、setDouble() ，向 ByteBuf 写入基础类型的数据。
     * setType 系列与writeTYPE系列的不同：
     * setType 系列 不会 改变写指针 writerIndex ；
     * writeTYPE系列 会 改变写指针 writerIndex 的值。
     *
     *
     */
    public abstract ByteBuf setByte(int index, int value);

    /**
     * Sets the specified 16-bit short integer at the specified absolute
     * {@code index} in this buffer.  The 16 high-order bits of the specified
     * value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setShort(int index, int value);

    /**
     * Sets the specified 16-bit short integer at the specified absolute
     * {@code index} in this buffer with the Little Endian Byte Order.
     * The 16 high-order bits of the specified value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setShortLE(int index, int value);

    /**
     * Sets the specified 24-bit medium integer at the specified absolute
     * {@code index} in this buffer.  Please note that the most significant
     * byte is ignored in the specified value.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setMedium(int index, int value);

    /**
     * Sets the specified 24-bit medium integer at the specified absolute
     * {@code index} in this buffer in the Little Endian Byte Order.
     * Please note that the most significant byte is ignored in the
     * specified value.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setMediumLE(int index, int value);

    /**
     * Sets the specified 32-bit integer at the specified absolute
     * {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setInt(int index, int value);

    /**
     * Sets the specified 32-bit integer at the specified absolute
     * {@code index} in this buffer with Little Endian byte order
     * .
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setIntLE(int index, int value);

    /**
     * Sets the specified 64-bit long integer at the specified absolute
     * {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setLong(int index, long value);

    /**
     * Sets the specified 64-bit long integer at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setLongLE(int index, long value);

    /**
     * Sets the specified 2-byte UTF-16 character at the specified absolute
     * {@code index} in this buffer.
     * The 16 high-order bits of the specified value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setChar(int index, int value);

    /**
     * Sets the specified 32-bit floating-point number at the specified
     * absolute {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setFloat(int index, float value);

    /**
     * Sets the specified 32-bit floating-point number at the specified
     * absolute {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public ByteBuf setFloatLE(int index, float value) {
        return setIntLE(index, Float.floatToRawIntBits(value));
    }

    /**
     * Sets the specified 64-bit floating-point number at the specified
     * absolute {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setDouble(int index, double value);

    /**
     * Sets the specified 64-bit floating-point number at the specified
     * absolute {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public ByteBuf setDoubleLE(int index, double value) {
        return setLongLE(index, Double.doubleToRawLongBits(value));
    }

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index} until the source buffer becomes
     * unreadable.  This method is basically same with
     * {@link #setBytes(int, ByteBuf, int, int)}, except that this
     * method increases the {@code readerIndex} of the source buffer by
     * the number of the transferred bytes while
     * {@link #setBytes(int, ByteBuf, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.readableBytes} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index}.  This method is basically same
     * with {@link #setBytes(int, ByteBuf, int, int)}, except that this
     * method increases the {@code readerIndex} of the source buffer by
     * the number of the transferred bytes while
     * {@link #setBytes(int, ByteBuf, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code length} is greater than {@code src.readableBytes}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex}
     * of both the source (i.e. {@code this}) and the destination.
     *
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code srcIndex + length} is greater than
     *            {@code src.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.length} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, byte[] src);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code srcIndex + length} is greater than {@code src.length}
     */
    public abstract ByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index} until the source buffer's position
     * reaches its limit.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.remaining()} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuffer src);

    /**
     * Transfers the content of the specified source stream to this buffer
     * starting at the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param length the number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel.
     *         {@code -1} if the specified channel is closed.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract int setBytes(int index, InputStream in, int length) throws IOException;

    /**
     * Transfers the content of the specified source channel to this buffer
     * starting at the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel.
     *         {@code -1} if the specified channel is closed.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int setBytes(int index, ScatteringByteChannel in, int length) throws IOException;

    /**
     * Transfers the content of the specified source channel starting at the given file position
     * to this buffer starting at the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer. This method does not modify the channel's position.
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel.
     *         {@code -1} if the specified channel is closed.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int setBytes(int index, FileChannel in, long position, int length) throws IOException;

    /**
     * Fills this buffer with <tt>NUL (0x00)</tt> starting at the specified
     * absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param length the number of <tt>NUL</tt>s to write to the buffer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setZero(int index, int length);

    /**
     * Writes the specified {@link CharSequence} at the current {@code writerIndex} and increases
     * the {@code writerIndex} by the written bytes.
     *
     * @param index on which the sequence should be written
     * @param sequence to write
     * @param charset that should be used.
     * @return the written number of bytes.
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is not large enough to write the whole sequence
     */
    public abstract int setCharSequence(int index, CharSequence sequence, Charset charset);

    /**
     * Gets a boolean at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    public abstract boolean readBoolean();

    /**
     * Gets a byte at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     *
     * readType(） 基础类型读取
     * 基础数据类型的读取，可以读取 8大基础类型。
     * 具体如下：readByte()、readBoolean()、readChar()、readShort()、readInt()、readLong()、readFloat()、readDouble() ，从 ByteBuf读取对应的基础类型的数据。
     *
     */
    public abstract byte  readByte();

    /**
     * Gets an unsigned byte at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    public abstract short readUnsignedByte();

    /**
     * Gets a 16-bit short integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 2} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract short readShort();

    /**
     * Gets a 16-bit short integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 2} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract short readShortLE();

    /**
     * Gets an unsigned 16-bit short integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 2} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     *
     *
     * 返回当前索引的 (无符号) Short 型，读索引加2
     * 
     */
    public abstract int   readUnsignedShort();

    /**
     * Gets an unsigned 16-bit short integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 2} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract int   readUnsignedShortLE();

    /**
     * Gets a 24-bit medium integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 3} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     *
     * 返回当前索引的 (无符号) 24-bit 中间值，读索引加3
     */
    public abstract int   readMedium();

    /**
     * Gets a 24-bit medium integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the
     * {@code readerIndex} by {@code 3} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readMediumLE();

    /**
     * Gets an unsigned 24-bit medium integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 3} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readUnsignedMedium();

    /**
     * Gets an unsigned 24-bit medium integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 3} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readUnsignedMediumLE();

    /**
     * Gets a 32-bit integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract int   readInt();

    /**
     * Gets a 32-bit integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract int   readIntLE();

    /**
     * Gets an unsigned 32-bit integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     *
     *   返回当前索引的(无符号) 整型，读索引加4
     */
    public abstract long  readUnsignedInt();

    /**
     * Gets an unsigned 32-bit integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract long  readUnsignedIntLE();

    /**
     * Gets a 64-bit integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 8} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public abstract long  readLong();

    /**
     * Gets a 64-bit integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 8} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public abstract long  readLongLE();

    /**
     * Gets a 2-byte UTF-16 character at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 2} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract char  readChar();

    /**
     * Gets a 32-bit floating point number at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract float readFloat();

    /**
     * Gets a 32-bit floating point number at the current {@code readerIndex}
     * in Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public float readFloatLE() {
        return Float.intBitsToFloat(readIntLE());
    }

    /**
     * Gets a 64-bit floating point number at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 8} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public abstract double readDouble();

    /**
     * Gets a 64-bit floating point number at the current {@code readerIndex}
     * in Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 8} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public double readDoubleLE() {
        return Double.longBitsToDouble(readLongLE());
    }

    /**
     * Transfers this buffer's data to a newly created buffer starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     * The returned buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and {@code length} respectively.
     *
     * @param length the number of bytes to transfer
     *
     * @return the newly created buffer which contains the transferred bytes
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(int length);

    /**
     * Returns a new slice of this buffer's sub-region starting at the current
     * {@code readerIndex} and increases the {@code readerIndex} by the size
     * of the new slice (= {@code length}).
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     *
     * @param length the size of the new slice
     *
     * @return the newly created slice
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readSlice(int length);

    /**
     * Returns a new retained slice of this buffer's sub-region starting at the current
     * {@code readerIndex} and increases the {@code readerIndex} by the size
     * of the new slice (= {@code length}).
     * <p>
     * Note that this method returns a {@linkplain #retain() retained} buffer unlike {@link #readSlice(int)}.
     * This method behaves similarly to {@code readSlice(...).retain()} except that this method may return
     * a buffer implementation that produces less garbage.
     *
     * @param length the size of the new slice
     *
     * @return the newly created slice
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readRetainedSlice(int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} until the destination becomes
     * non-writable, and increases the {@code readerIndex} by the number of the
     * transferred bytes.  This method is basically same with
     * {@link #readBytes(ByteBuf, int, int)}, except that this method
     * increases the {@code writerIndex} of the destination by the number of
     * the transferred bytes while {@link #readBytes(ByteBuf, int, int)}
     * does not.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code dst.writableBytes} is greater than
     *            {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).  This method
     * is basically same with {@link #readBytes(ByteBuf, int, int)},
     * except that this method increases the {@code writerIndex} of the
     * destination by the number of the transferred bytes (= {@code length})
     * while {@link #readBytes(ByteBuf, int, int)} does not.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes} or
     *         if {@code length} is greater than {@code dst.writableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code length} is greater than {@code this.readableBytes}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.capacity}
     */
    public abstract ByteBuf readBytes(ByteBuf dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code dst.length}).
     *
     * @throws IndexOutOfBoundsException
     *         if {@code dst.length} is greater than {@code this.readableBytes}
     *
     * 把 ByteBuf 里面的数据全部读取到 dst 字节数组中，这里 dst 字节数组的大小通常等于 readableBytes() 。
     * 这个方法，也是最为常用的一个方法。
     */
    public abstract ByteBuf readBytes(byte[] dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code length} is greater than {@code this.readableBytes}, or
     *         if {@code dstIndex + length} is greater than {@code dst.length}
     */
    public abstract ByteBuf readBytes(byte[] dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} until the destination's position
     * reaches its limit, and increases the {@code readerIndex} by the
     * number of the transferred bytes.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code dst.remaining()} is greater than
     *            {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuffer dst);

    /**
     * Transfers this buffer's data to the specified stream starting at the
     * current {@code readerIndex}.
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract ByteBuf readBytes(OutputStream out, int length) throws IOException;

    /**
     * Transfers this buffer's data to the specified stream starting at the
     * current {@code readerIndex}.
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int readBytes(GatheringByteChannel out, int length) throws IOException;

    /**
     * Gets a {@link CharSequence} with the given length at the current {@code readerIndex}
     * and increases the {@code readerIndex} by the given length.
     *
     * @param length the length to read
     * @param charset that should be used
     * @return the sequence
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract CharSequence readCharSequence(int length, Charset charset);

    /**
     * Transfers this buffer's data starting at the current {@code readerIndex}
     * to the specified channel starting at the given file position.
     * This method does not modify the channel's position.
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int readBytes(FileChannel out, long position, int length) throws IOException;

    /**
     * Increases the current {@code readerIndex} by the specified
     * {@code length} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf skipBytes(int length);

    /**
     * Sets the specified boolean at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 1} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 1}
     */
    public abstract ByteBuf writeBoolean(boolean value);

    /**
     * Sets the specified byte at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 1} in this buffer.
     * The 24 high-order bits of the specified value are ignored.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 1}
     * 方法五：writeTYPE(TYPE value） 基础类型写入方法
     * 基础数据类型的写入，包含了 8大基础类型的写入。
     * 具体如下：writeByte()、 writeBoolean()、writeChar()、writeShort()、writeInt()、writeLong()、writeFloat()、writeDouble() ，向 ByteBuf写入基础类型的数据。
     */
    public abstract ByteBuf writeByte(int value);

    /**
     * Sets the specified 16-bit short integer at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 2}
     * in this buffer.  The 16 high-order bits of the specified value are ignored.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 2}
     */
    public abstract ByteBuf writeShort(int value);

    /**
     * Sets the specified 16-bit short integer in the Little Endian Byte
     * Order at the current {@code writerIndex} and increases the
     * {@code writerIndex} by {@code 2} in this buffer.
     * The 16 high-order bits of the specified value are ignored.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 2}
     */
    public abstract ByteBuf writeShortLE(int value);

    /**
     * Sets the specified 24-bit medium integer at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 3}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 3}
     */
    public abstract ByteBuf writeMedium(int value);

    /**
     * Sets the specified 24-bit medium integer at the current
     * {@code writerIndex} in the Little Endian Byte Order and
     * increases the {@code writerIndex} by {@code 3} in this
     * buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 3}
     */
    public abstract ByteBuf writeMediumLE(int value);

    /**
     * Sets the specified 32-bit integer at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 4}
     */
    public abstract ByteBuf writeInt(int value);

    /**
     * Sets the specified 32-bit integer at the current {@code writerIndex}
     * in the Little Endian Byte Order and increases the {@code writerIndex}
     * by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 4}
     */
    public abstract ByteBuf writeIntLE(int value);

    /**
     * Sets the specified 64-bit long integer at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 8}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 8}
     */
    public abstract ByteBuf writeLong(long value);

    /**
     * Sets the specified 64-bit long integer at the current
     * {@code writerIndex} in the Little Endian Byte Order and
     * increases the {@code writerIndex} by {@code 8}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 8}
     */
    public abstract ByteBuf writeLongLE(long value);

    /**
     * Sets the specified 2-byte UTF-16 character at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 2}
     * in this buffer.  The 16 high-order bits of the specified value are ignored.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 2}
     */
    public abstract ByteBuf writeChar(int value);

    /**
     * Sets the specified 32-bit floating point number at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 4}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 4}
     */
    public abstract ByteBuf writeFloat(float value);

    /**
     * Sets the specified 32-bit floating point number at the current
     * {@code writerIndex} in Little Endian Byte Order and increases
     * the {@code writerIndex} by {@code 4} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 4}
     */
    public ByteBuf writeFloatLE(float value) {
        return writeIntLE(Float.floatToRawIntBits(value));
    }

    /**
     * Sets the specified 64-bit floating point number at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 8}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 8}
     */
    public abstract ByteBuf writeDouble(double value);

    /**
     * Sets the specified 64-bit floating point number at the current
     * {@code writerIndex} in Little Endian Byte Order and increases
     * the {@code writerIndex} by {@code 8} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is less than {@code 8}
     */
    public ByteBuf writeDoubleLE(double value) {
        return writeLongLE(Double.doubleToRawLongBits(value));
    }

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} until the source buffer becomes
     * unreadable, and increases the {@code writerIndex} by the number of
     * the transferred bytes.  This method is basically same with
     * {@link #writeBytes(ByteBuf, int, int)}, except that this method
     * increases the {@code readerIndex} of the source buffer by the number of
     * the transferred bytes while {@link #writeBytes(ByteBuf, int, int)}
     * does not.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code src.readableBytes} is greater than
     *            {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).  This method
     * is basically same with {@link #writeBytes(ByteBuf, int, int)},
     * except that this method increases the {@code readerIndex} of the source
     * buffer by the number of the transferred bytes (= {@code length}) while
     * {@link #writeBytes(ByteBuf, int, int)} does not.
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.writableBytes} or
     *         if {@code length} is greater then {@code src.readableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code srcIndex + length} is greater than
     *            {@code src.capacity}, or
     *         if {@code length} is greater than {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src, int srcIndex, int length);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code src.length}).
     *
     * @throws IndexOutOfBoundsException
     *         if {@code src.length} is greater than {@code this.writableBytes}
     *
     * 把字节数组 src 里面的数据全部写到 ByteBuf。这个是最为常用的一个方法。
     */
    public abstract ByteBuf writeBytes(byte[] src);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code srcIndex + length} is greater than
     *            {@code src.length}, or
     *         if {@code length} is greater than {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(byte[] src, int srcIndex, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} until the source buffer's position
     * reaches its limit, and increases the {@code writerIndex} by the
     * number of the transferred bytes.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code src.remaining()} is greater than
     *            {@code this.writableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuffer src);

    /**
     * Transfers the content of the specified stream to this buffer
     * starting at the current {@code writerIndex} and increases the
     * {@code writerIndex} by the number of the transferred bytes.
     *
     * @param length the number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified stream
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.writableBytes}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract int  writeBytes(InputStream in, int length) throws IOException;

    /**
     * Transfers the content of the specified channel to this buffer
     * starting at the current {@code writerIndex} and increases the
     * {@code writerIndex} by the number of the transferred bytes.
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.writableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int writeBytes(ScatteringByteChannel in, int length) throws IOException;

    /**
     * Transfers the content of the specified channel starting at the given file position
     * to this buffer starting at the current {@code writerIndex} and increases the
     * {@code writerIndex} by the number of the transferred bytes.
     * This method does not modify the channel's position.
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.writableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int writeBytes(FileChannel in, long position, int length) throws IOException;

    /**
     * Fills this buffer with <tt>NUL (0x00)</tt> starting at the current
     * {@code writerIndex} and increases the {@code writerIndex} by the
     * specified {@code length}.
     *
     * @param length the number of <tt>NUL</tt>s to write to the buffer
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.writableBytes}
     */
    public abstract ByteBuf writeZero(int length);

    /**
     * Writes the specified {@link CharSequence} at the current {@code writerIndex} and increases
     * the {@code writerIndex} by the written bytes.
     * in this buffer.
     *
     * @param sequence to write
     * @param charset that should be used
     * @return the written number of bytes
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is not large enough to write the whole sequence
     */
    public abstract int writeCharSequence(CharSequence sequence, Charset charset);

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search takes place from the specified {@code fromIndex}
     * (inclusive)  to the specified {@code toIndex} (exclusive).
     * <p>
     * If {@code fromIndex} is greater than {@code toIndex}, the search is
     * performed in a reversed order.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return the absolute index of the first occurrence if found.
     *         {@code -1} otherwise.
     */
    public abstract int indexOf(int fromIndex, int toIndex, byte value); // 指定值( value ) 在 ByteBuf 中的位置

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search takes place from the current {@code readerIndex}
     * (inclusive) to the current {@code writerIndex} (exclusive).
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return the number of bytes between the current {@code readerIndex}
     *         and the first occurrence if found. {@code -1} otherwise.
     */
    public abstract int bytesBefore(byte value);

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search starts from the current {@code readerIndex}
     * (inclusive) and lasts for the specified {@code length}.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return the number of bytes between the current {@code readerIndex}
     *         and the first occurrence if found. {@code -1} otherwise.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract int bytesBefore(int length, byte value);

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search starts from the specified {@code index} (inclusive)
     * and lasts for the specified {@code length}.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return the number of bytes between the specified {@code index}
     *         and the first occurrence if found. {@code -1} otherwise.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code index + length} is greater than {@code this.capacity}
     */
    public abstract int bytesBefore(int index, int length, byte value);

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in ascending order.
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     *
     *
     * 查找byte,返回byte的索引
     */
    public abstract int forEachByte(ByteProcessor processor); // 遍历 ByteBuf ，进行自定义处理

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in ascending order.
     * (i.e. {@code index}, {@code (index + 1)},  .. {@code (index + length - 1)})
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByte(int index, int length, ByteProcessor processor);

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in descending order.
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByteDesc(ByteProcessor processor);

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in descending order.
     * (i.e. {@code (index + length - 1)}, {@code (index + length - 2)}, ... {@code index})
     *
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByteDesc(int index, int length, ByteProcessor processor);

    /**
     * Returns a copy of this buffer's readable bytes.  Modifying the content
     * of the returned buffer or this buffer does not affect each other at all.
     * This method is identical to {@code buf.copy(buf.readerIndex(), buf.readableBytes())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     *
     * 返回的 ByteBuf 有数据的独立副本。
     */
    public abstract ByteBuf copy(); // 拷贝可读部分的字节数组。独立，互相不影响。

    /**
     * Returns a copy of this buffer's sub-region.  Modifying the content of
     * the returned buffer or this buffer does not affect each other at all.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     */
    public abstract ByteBuf copy(int index, int length);

    /**
     * Returns a slice of this buffer's readable bytes. Modifying the content
     * of the returned buffer or this buffer affects each other's content
     * while they maintain separate indexes and marks.  This method is
     * identical to {@code buf.slice(buf.readerIndex(), buf.readableBytes())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     */
    public abstract ByteBuf slice(); // 拷贝可读部分的字节数组。共享，相互影响。

    /**
     * Returns a retained slice of this buffer's readable bytes. Modifying the content
     * of the returned buffer or this buffer affects each other's content
     * while they maintain separate indexes and marks.  This method is
     * identical to {@code buf.slice(buf.readerIndex(), buf.readableBytes())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     * <p>
     * Note that this method returns a {@linkplain #retain() retained} buffer unlike {@link #slice()}.
     * This method behaves similarly to {@code slice().retain()} except that this method may return
     * a buffer implementation that produces less garbage.
     */
    public abstract ByteBuf retainedSlice();

    /**
     * Returns a slice of this buffer's sub-region. Modifying the content of
     * the returned buffer or this buffer affects each other's content while
     * they maintain separate indexes and marks.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     * <p>
     * Also be aware that this method will NOT call {@link #retain()} and so the
     * reference count will NOT be increased.
     */
    public abstract ByteBuf slice(int index, int length);

    /**
     * Returns a retained slice of this buffer's sub-region. Modifying the content of
     * the returned buffer or this buffer affects each other's content while
     * they maintain separate indexes and marks.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     * <p>
     * Note that this method returns a {@linkplain #retain() retained} buffer unlike {@link #slice(int, int)}.
     * This method behaves similarly to {@code slice(...).retain()} except that this method may return
     * a buffer implementation that produces less garbage.
     */
    public abstract ByteBuf retainedSlice(int index, int length);

    /**
     * Returns a buffer which shares the whole region of this buffer.
     * Modifying the content of the returned buffer or this buffer affects
     * each other's content while they maintain separate indexes and marks.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     * <p>
     * The reader and writer marks will not be duplicated. Also be aware that this method will
     * NOT call {@link #retain()} and so the reference count will NOT be increased.
     * @return A buffer whose readable content is equivalent to the buffer returned by {@link #slice()}.
     * However this buffer will share the capacity of the underlying buffer, and therefore allows access to all of the
     * underlying content if necessary.
     */
    public abstract ByteBuf duplicate(); // 拷贝整个的字节数组。共享，相互影响

    /**
     * Returns a retained buffer which shares the whole region of this buffer.
     * Modifying the content of the returned buffer or this buffer affects
     * each other's content while they maintain separate indexes and marks.
     * This method is identical to {@code buf.slice(0, buf.capacity())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     * <p>
     * Note that this method returns a {@linkplain #retain() retained} buffer unlike {@link #slice(int, int)}.
     * This method behaves similarly to {@code duplicate().retain()} except that this method may return
     * a buffer implementation that produces less garbage.
     */
    public abstract ByteBuf retainedDuplicate();

    /**
     * Returns the maximum number of NIO {@link ByteBuffer}s that consist this buffer.  Note that {@link #nioBuffers()}
     * or {@link #nioBuffers(int, int)} might return a less number of {@link ByteBuffer}s.
     *
     * @return {@code -1} if this buffer has no underlying {@link ByteBuffer}.
     *         the number of the underlying {@link ByteBuffer}s if this buffer has at least one underlying
     *         {@link ByteBuffer}.  Note that this method does not return {@code 0} to avoid confusion.
     *
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract int nioBufferCount();

    /**
     * Exposes this buffer's readable bytes as an NIO {@link ByteBuffer}. The returned buffer
     * either share or contains the copied content of this buffer, while changing the position
     * and limit of the returned NIO buffer does not affect the indexes and marks of this buffer.
     * This method is identical to {@code buf.nioBuffer(buf.readerIndex(), buf.readableBytes())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
     * Please note that the returned NIO buffer will not see the changes of this buffer if this buffer
     * is a dynamic buffer and it adjusted its capacity.
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    // ByteBuf 包含 ByteBuffer 数量。
    // 如果返回 = 1 ，则调用 `#nioBuffer()` 方法，获得 ByteBuf 包含的 ByteBuffer 对象。
    // 如果返回 > 1 ，则调用 `#nioBuffers()` 方法，获得 ByteBuf 包含的 ByteBuffer 数组。
    public abstract ByteBuffer nioBuffer();

    /**
     * Exposes this buffer's sub-region as an NIO {@link ByteBuffer}. The returned buffer
     * either share or contains the copied content of this buffer, while changing the position
     * and limit of the returned NIO buffer does not affect the indexes and marks of this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
     * Please note that the returned NIO buffer will not see the changes of this buffer if this buffer
     * is a dynamic buffer and it adjusted its capacity.
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract ByteBuffer nioBuffer(int index, int length);

    /**
     * Internal use only: Exposes the internal NIO buffer.
     */
    public abstract ByteBuffer internalNioBuffer(int index, int length);

    /**
     * Exposes this buffer's readable bytes as an NIO {@link ByteBuffer}'s. The returned buffer
     * either share or contains the copied content of this buffer, while changing the position
     * and limit of the returned NIO buffer does not affect the indexes and marks of this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
     * Please note that the returned NIO buffer will not see the changes of this buffer if this buffer
     * is a dynamic buffer and it adjusted its capacity.
     *
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     */
    public abstract ByteBuffer[] nioBuffers();

    /**
     * Exposes this buffer's bytes as an NIO {@link ByteBuffer}'s for the specified index and length
     * The returned buffer either share or contains the copied content of this buffer, while changing
     * the position and limit of the returned NIO buffer does not affect the indexes and marks of this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer. Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     */
    public abstract ByteBuffer[] nioBuffers(int index, int length);

    /**
     * Returns {@code true} if and only if this buffer has a backing byte array.
     * If this method returns true, you can safely call {@link #array()} and
     * {@link #arrayOffset()}.
     *
     * 如果byteBuf可以直接返回一个数组就返回true
     * (heap buf才会为true)
     */
    public abstract boolean hasArray();

    /**
     * Returns the backing byte array of this buffer.
     *
     * @throws UnsupportedOperationException
     *         if there no accessible backing byte array
     *
     * hasArray返回true,该方法就会返回一个数组
     */
    public abstract byte[] array();

    /**
     * Returns the offset of the first byte within the backing byte array of
     * this buffer.
     *
     * @throws UnsupportedOperationException
     *         if there no accessible backing byte array
     */
    public abstract int arrayOffset();

    /**
     * Returns {@code true} if and only if this buffer has a reference to the low-level memory address that points
     * to the backing data.
     */
    public abstract boolean hasMemoryAddress();

    /**
     * Returns the low-level memory address that point to the first byte of ths backing data.
     *
     * @throws UnsupportedOperationException
     *         if this buffer does not support accessing the low-level memory address
     */
    public abstract long memoryAddress();

    /**
     * Decodes this buffer's readable bytes into a string with the specified
     * character set name.  This method is identical to
     * {@code buf.toString(buf.readerIndex(), buf.readableBytes(), charsetName)}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @throws UnsupportedCharsetException
     *         if the specified character set name is not supported by the
     *         current VM
     */
    public abstract String toString(Charset charset);

    /**
     * Decodes this buffer's sub-region into a string with the specified
     * character set.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     */
    public abstract String toString(int index, int length, Charset charset);

    /**
     * Returns a hash code which was calculated from the content of this
     * buffer.  If there's a byte array which is
     * {@linkplain #equals(Object) equal to} this array, both arrays should
     * return the same value.
     */
    @Override
    public abstract int hashCode();

    /**
     * Determines if the content of the specified buffer is identical to the
     * content of this array.  'Identical' here means:
     * <ul>
     * <li>the size of the contents of the two buffers are same and</li>
     * <li>every single byte of the content of the two buffers are same.</li>
     * </ul>
     * Please note that it does not compare {@link #readerIndex()} nor
     * {@link #writerIndex()}.  This method also returns {@code false} for
     * {@code null} and an object which is not an instance of
     * {@link ByteBuf} type.
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Compares the content of the specified buffer to the content of this
     * buffer. Comparison is performed in the same manner with the string
     * comparison functions of various languages such as {@code strcmp},
     * {@code memcmp} and {@link String#compareTo(String)}.
     */
    @Override
    public abstract int compareTo(ByteBuf buffer);

    /**
     * Returns the string representation of this buffer.  This method does not
     * necessarily return the whole content of the buffer but returns
     * the values of the key properties such as {@link #readerIndex()},
     * {@link #writerIndex()} and {@link #capacity()}.
     */
    @Override
    public abstract String toString();

    @Override
    public abstract ByteBuf retain(int increment);

    @Override
    public abstract ByteBuf retain();

    @Override
    public abstract ByteBuf touch();

    @Override
    public abstract ByteBuf touch(Object hint);
}
