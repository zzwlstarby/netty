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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Abstract {@link Future} implementation which does not allow for cancellation.
 *
 * 一个不允许取消的future的抽象实现。言外之意，实现AbstractFuture的future都不允许取消。
 * @param <V>
 */
public abstract class AbstractFuture<V> implements Future<V> {

    /**
     * 必要时等待计算完成，然后检索其结果。
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {

        //调用await（）方法：等待此future完成。如果当前线程被中断了则抛中断异常(InterruptedException)
        await();


        Throwable cause = cause();
        if (cause == null) { //如果成功或此未来尚未完成，则返回null。
            //无阻塞地返回结果。如果future尚未完成，则返回{@code null}。
            return getNow();
        }
        if (cause instanceof CancellationException) {//如果被取消，则抛出取消异常（说明get不允许获取取消的future）
            throw (CancellationException) cause;
        }
        //返回执行异常。
        throw new ExecutionException(cause);
    }

    /**
     * 在指定时间内等待此future完成，如果此future在指定时间内完成了则返回true
     *  如果当前线程被中断，则返回中断异常（InterruptedException）。
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (await(timeout, unit)) {//如果在超时时间内完成则判断是否执行异常。
            Throwable cause = cause();
            if (cause == null) {//如果没有执行异常，则无阻塞地返回结果。如果future尚未完成，则返回{@code null}。
                return getNow();
            }
            if (cause instanceof CancellationException) {
                throw (CancellationException) cause;
            }
            throw new ExecutionException(cause);
        }
        //如果超时，则返回超时异常。
        throw new TimeoutException();
    }
}
