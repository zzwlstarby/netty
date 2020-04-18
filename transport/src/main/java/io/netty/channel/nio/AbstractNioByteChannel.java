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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.internal.ChannelUtils;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import static io.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 */
public abstract class AbstractNioByteChannel extends AbstractNioChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(FileRegion.class) + ')';

    private final Runnable flushTask = new Runnable() {
        @Override
        public void run() {
            // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
            // meantime.
            ((AbstractNioUnsafe) unsafe()).flush0();
        }
    };

    /**
     * 通道关闭读取，又错误读取的错误的标识
     *
     * 详细见 https://github.com/netty/netty/commit/ed0668384b393c3502c2136e3cc412a5c8c9056e 提交
     */
    private boolean inputClosedSeenErrorOnRead;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    /**
     * Shutdown the input side of the channel.
     */
    protected abstract ChannelFuture shutdownInput();

    protected boolean isInputShutdown0() {
        return false;
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    final boolean shouldBreakReadReady(ChannelConfig config) {
        return isInputShutdown0() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    private static boolean isAllowHalfClosure(ChannelConfig config) {
        return config instanceof SocketChannelConfig &&
                ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    protected class NioByteUnsafe extends AbstractNioUnsafe {

        private void closeOnRead(ChannelPipeline pipeline) {
            if (!isInputShutdown0()) {
                // 开启连接半关闭
                if (isAllowHalfClosure(config())) {
                    // 关闭 Channel 数据的读取
                    shutdownInput();
                    // 触发 ChannelInputShutdownEvent.INSTANCE 事件到 pipeline 中
                    pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            } else {
                // 标记 inputClosedSeenErrorOnRead 为 true
                inputClosedSeenErrorOnRead = true;
                // 触发 ChannelInputShutdownEvent.INSTANCE 事件到 pipeline 中
                pipeline.fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
        }

        @SuppressWarnings("Duplicates")
        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close, RecvByteBufAllocator.Handle allocHandle) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    // TODO 芋艿 细节
                    readPending = false;
                    // 触发 Channel read 事件到 pipeline 中。
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    // 释放 ByteBuf 对象
                    byteBuf.release();
                }
            }
            // 读取完成
            allocHandle.readComplete();
            // 触发 Channel readComplete 事件到 pipeline 中。
            pipeline.fireChannelReadComplete();
            // 触发 exceptionCaught 事件到 pipeline 中。
            pipeline.fireExceptionCaught(cause);
            // // TODO 芋艿 细节
            if (close || cause instanceof IOException) {
                closeOnRead(pipeline);
            }
        }

        /**
         * 在AbstractNioByteChannel 中，可以找到 unsafe.read( ) 调用的实现代码。 unsafe.read( )负责的是 Channel 的底层数据的
         * IO 读取，并且将读取的结果，dispatch（分派）给最终的Handler。
         */
        @Override
        @SuppressWarnings("Duplicates")
        public final void read() {
            final ChannelConfig config = config();
            // 若 inputClosedSeenErrorOnRead = true ，移除对 SelectionKey.OP_READ 事件的感兴趣。
            if (shouldBreakReadReady(config)) {
                clearReadPending();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            // 获得 RecvByteBufAllocator.Handle 对象
            final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
            // 重置 RecvByteBufAllocator.Handle 对象
            allocHandle.reset(config);

            ByteBuf byteBuf = null;
            boolean close = false; // 是否关闭连接
            try {
                do {
                    // 申请 ByteBuf 对象
                    byteBuf = allocHandle.allocate(allocator);
                    // 读取数据
                    // 设置最后读取字节数
                    allocHandle.lastBytesRead(doReadBytes(byteBuf));
                    // <1> 未读取到数据
                    if (allocHandle.lastBytesRead() <= 0) {
                        // 释放 ByteBuf 对象
                        // nothing was read. release the buffer.
                        byteBuf.release();
                        // 置空 ByteBuf 对象
                        byteBuf = null;
                        // 如果最后读取的字节为小于 0 ，说明对端已经关闭
                        close = allocHandle.lastBytesRead() < 0;
                        // TODO
                        if (close) {
                            // There is nothing left to read as we received an EOF.
                            readPending = false;
                        }
                        // 结束循环
                        break;
                    }

                    // <2> 读取到数据

                    // 读取消息数量 + localRead
                    allocHandle.incMessagesRead(1);
                    // TODO 芋艿 readPending
                    readPending = false;
                    // 触发 Channel read 事件到 pipeline 中。 TODO
                    pipeline.fireChannelRead(byteBuf);
                    // 置空 ByteBuf 对象
                    byteBuf = null;
                } while (allocHandle.continueReading()); // 循环判断是否继续读取

                // 读取完成
                allocHandle.readComplete();
                // 触发 Channel readComplete 事件到 pipeline 中。
                pipeline.fireChannelReadComplete();

                // TODO 芋艿 细节
                if (close) {
                    closeOnRead(pipeline);
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close, allocHandle);
            } finally {
                // TODO 芋艿 readPending
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!readPending && !config.isAutoRead()) {
                    removeReadOp();
                }
            }
        }
    }

    /**
     * Write objects to the OS.
     * @param in the collection which contains objects to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     * @throws Exception if an I/O exception occurs during write.
     */
    protected final int doWrite0(ChannelOutboundBuffer in) throws Exception {
        Object msg = in.current();
        if (msg == null) {
            // Directly return here so incompleteWrite(...) is not called.
            return 0;
        }
        return doWriteInternal(in, in.current());
    }

    private int doWriteInternal(ChannelOutboundBuffer in, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.isReadable()) {
                in.remove();
                return 0;
            }

            final int localFlushedAmount = doWriteBytes(buf);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (!buf.isReadable()) {
                    in.remove();
                }
                return 1;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;
            if (region.transferred() >= region.count()) {
                in.remove();
                return 0;
            }

            long localFlushedAmount = doWriteFileRegion(region);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (region.transferred() >= region.count()) {
                    in.remove();
                }
                return 1;
            }
        } else {
            // Should not reach here.
            throw new Error();
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = config().getWriteSpinCount();
        do {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }
            writeSpinCount -= doWriteInternal(in, msg);
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        // ByteBuf 的情况
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            // 已经是 Direct ByteBuf
            if (buf.isDirect()) {
                return msg;
            }

            // 非 Direct ByteBuf ，需要进行创建封装
            return newDirectBuffer(buf);
        }

        // FileRegion 的情况
        if (msg instanceof FileRegion) {
            return msg;
        }

        // 不支持其他类型
        throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        // true ，注册对 SelectionKey.OP_WRITE 事件感兴趣
        if (setOpWrite) {
            setOpWrite();
        // false ，取消对 SelectionKey.OP_WRITE 事件感兴趣
        } else {
            // It is possible that we have set the write OP, woken up by NIO because the socket is writable, and then
            // use our write quantum. In this case we no longer want to set the write OP because the socket is still
            // writable (as far as we know). We will find out next time we attempt to write if the socket is writable
            // and set the write OP if necessary.
            clearOpWrite();

            // Schedule flush again later so other tasks can be picked up in the meantime
            // 立即发起下一次 flush 任务
            eventLoop().execute(flushTask);
        }
    }

    /**
     * Write a {@link FileRegion}
     *
     * @param region        the {@link FileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract long doWriteFileRegion(FileRegion region) throws Exception;

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract int doWriteBytes(ByteBuf buf) throws Exception;

    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) { // 合法
            return;
        }
        final int interestOps = key.interestOps();
        // 注册 SelectionKey.OP_WRITE 事件的感兴趣
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) { // 合法
            return;
        }
        final int interestOps = key.interestOps();
        // 若注册了 SelectionKey.OP_WRITE ，则进行取消
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }

}
