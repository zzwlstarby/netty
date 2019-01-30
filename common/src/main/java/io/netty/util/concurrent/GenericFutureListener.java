/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import java.util.EventListener;

/**
 * Listens to the result of a {@link Future}.  The result of the asynchronous operation is notified once this listener
 * is added by calling {@link Future#addListener(GenericFutureListener)}.
 *
 * 一个通用的future监听器接口。
 *
 * 监听future的结果。异步操作结果将通过此监听器来通知一次
 *
 * 概述：
 *      虽然可以通过ChannelFuture的get()方法获取异步操作的结果,但完成时间是无法预测的,若不设置超时时间则有可能导致线程长时间被阻塞;
 *      若是不能精确的设置超时时间则可能导致I/O操作中断.因此,Netty建议通过GenericFutureListener接口执行异步操作结束后的回调.
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * Invoked when the operation associated with the {@link Future} has been completed.
     *
     * @param future  the source {@link Future} which called this callback
     */
    void operationComplete(F future) throws Exception;
}
