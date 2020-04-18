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

/**
 * {@link ChannelHandler} which adds callbacks for state changes. This allows the user
 * to hook in to state changes easily.
 */

/**
 *1. ChannelInboundHandler入站处理器
 * 当Java NIO事件进站到Channel时，产生一的一系列事件将由ChannelHandler所对应的API处理。
 * 当查询到Java NIO底层Channel的就绪事件时，通过一系列的ChannelInboundHandler处理器，完成底层就绪事件的处理。
 * 比方说底层连接建立事件、底层连接断开事件、从底层读写就绪事件等等。
 *
 *2.入站（inbound）处理通常由底层Java NIO channel触发，主要事件如下：
 * 1. 注册事件 fireChannelRegistered。
 * 2. 连接建立事件 fireChannelActive。
 * 3. 读事件和读完成事件 fireChannelRead、fireChannelReadComplete。
 * 4. 异常通知事件 fireExceptionCaught。
 * 5. 用户自定义事件 fireUserEventTriggered。
 * 6. Channel 可写状态变化事件 fireChannelWritabilityChanged。
 * 7. 连接关闭事件 fireChannelInactive。
 *
 */
public interface ChannelInboundHandler extends ChannelHandler {

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered with its {@link EventLoop}
     */
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was unregistered from its {@link EventLoop}
     *
     *
     */
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} is now active
     */
    void channelActive(ChannelHandlerContext ctx) throws Exception;

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered is now inactive and reached its
     * end of lifetime.
     */
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    /**
     * Invoked when the current {@link Channel} has read a message from the peer
     *
     * 对于每个传入的消息都要调用；.
     */
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * Invoked when the last message read by the current read operation has been consumed by
     * {@link #channelRead(ChannelHandlerContext, Object)}.  If {@link ChannelOption#AUTO_READ} is off, no further
     * attempt to read an inbound data from the current {@link Channel} will be made until
     * {@link ChannelHandlerContext#read()} is called.
     * 通知ChannelInboundHandler最后一次对channelRead()的调用是当前批量读取中的最后一条消息
     *
     *
     */
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if an user event was triggered.
     */
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    /**
     * Gets called once the writable state of a {@link Channel} changed. You can check the state with
     * {@link Channel#isWritable()}.
     */
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     *
     * 在读取操作期间， 有异常抛出时会调用。
     */
    @Override
    @SuppressWarnings("deprecation")
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
