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

import io.netty.bootstrap.Bootstrap;
import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;


/**
 * The result of an asynchronous {@link Channel} I/O operation.
 *
 * 一个异步得IO操作得结果。
 *
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which gives you the information about the
 * result or status of the I/O operation.
 *
 *在netty中所有得操作都是异步得。这意味着任何I / O调用都将立即返回，
 * 并且不保证所请求的I / O操作在调用结束时是完成的 。
 * 但是，将返回一个 {@link ChannelFuture}实例，该实例提供了有关I / O操作的结果或者状态的信息。
 *
 * <p>
 * A {@link ChannelFuture} is either <em>uncompleted</em> or <em>completed</em>.
 * When an I/O operation begins, a new future object is created.  The new future
 * is uncompleted initially - it is neither succeeded, failed, nor cancelled
 * because the I/O operation is not finished yet.  If the I/O operation is
 * finished either successfully, with failure, or by cancellation, the future is
 * marked as completed with more specific information, such as the cause of the
 * failure.  Please note that even failure and cancellation belong to the
 * completed state.
 *
 * {@link ChannelFuture}是无论是否已经完成。 当I / O操作开始时，将创建一个新的future对象。
 * 因为I / O操作还没有完成,所以这个新的future最初是未完成的(它既没有成功，也没有被取消，也没有失败)
 *
 * 如果I / O操作成功完成，失败或取消，则将使用更具体的信息标记为已完成，例如失败的原因。
 *
 * 请注意，即使失败和取消也属于已完成状态。可以理解为完成的状态下才有结果成功或者被取消或者失败。
 *
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add {@link ChannelFutureListener}s so you
 * can get notified when the I/O operation is completed.
 *
 * ChannelFuture提供了各种方法来检查I / O操作是否已完成,等待完成以及查询i/o操作的结果。
 * 当然，也可以通过添加一个ChannelFutureListener，在i/o操作完成后通知你
 *
 *
 * <h3>Prefer {@link #addListener(GenericFutureListener)} to {@link #await()}</h3>
 * 建议通过addListener（GenericFutureListener）方法去等待。
 *
 * It is recommended to prefer {@link #addListener(GenericFutureListener)} to
 * {@link #await()} wherever possible to get notified when an I/O operation is
 * done and to do any follow-up tasks.
 *
 * 建议尽可能选择{@link #addListener（GenericFutureListener）}到* {@link #await（）}，
 * 以便在完成I / O操作时获得通知并执行任何后续任务
 *
 * <p>
 * {@link #addListener(GenericFutureListener)} is non-blocking.  It simply adds
 * the specified {@link ChannelFutureListener} to the {@link ChannelFuture}, and
 * I/O thread will notify the listeners when the I/O operation associated with
 * the future is done.  {@link ChannelFutureListener} yields the best
 * performance and resource utilization because it does not block at all, but
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 *
 *  {@link #addListener(GenericFutureListener)} 是非阻塞的。将指定的
 *  {@link ChannelFutureListener}添加到{@link ChannelFuture}，
 *  I / O线程将在与future相关的I / O操作完成时通知监听器。
 *
 * {@link ChannelFutureListener}产生最佳的性能和资源利用率，因为它根本不会阻塞，
 * 但是如果你不习惯事件驱动的编程，那么实现顺序逻辑可能会很棘手
 *
 * <p>
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until the operation is done.  It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
 * expensive cost of inter-thread notification.  Moreover, there's a chance of
 * dead lock in a particular circumstance, which is described below.
 *
 * 相比之下，{@ link #await（）}是一个阻塞操作。一旦被调用，调用程序线程就会阻塞，直到操作完成。
 * 使用{@link #await（）}实现顺序逻辑更容易，但调用程序线程不必要地阻塞，
 * 直到完成I / O操作并且线程间通知成本相对较高。此外，在特定情况下存在死锁的可能性，如下所述。
 *
 * <h3>Do not call {@link #await()} inside {@link ChannelHandler}</h3>
 * 不要再ChannelHandler调用await()方法。
 * <p>
 * The event handler methods in {@link ChannelHandler} are usually called by
 * an I/O thread.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 * {@link ChannelHandler}中的事件处理程序方法通常由I / O线程调用。
 * 如果由I / O线程调用的事件处理程序方法调用{@link #await（）}，
 * 则它等待的I / O操作可能永远不会完成，因为{@link #await（）}可以阻止它正在等待的I / O *操作，这是一个死锁。
 *
 * <pre>
 * // BAD - NEVER DO THIS
 * //反例，永远不要这么写
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     执行关闭后操作
 *     // ...
 * }
 *
 * // GOOD
 * //正确例子
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             执行关闭后操作
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link BlockingOperationException} will be raised to prevent a dead lock.
 *
 * 尽管存在上述缺点，但肯定存在调用{@link #await（）}更方便的情况。在这种情况下，
 * 请确保您不在I / O线程中调用{@link #await（）}，否则，将引发{@link BlockingOperationException}以防止死锁
 *
 *
 * <h3>Do not confuse I/O timeout and await timeout</h3>
 * 不要混淆I / O超时和等待超时
 *
 * The timeout value you specify with {@link #await(long)},
 * {@link #await(long, TimeUnit)}, {@link #awaitUninterruptibly(long)}, or
 * {@link #awaitUninterruptibly(long, TimeUnit)} are not related with I/O
 * timeout at all.  If an I/O operation times out, the future will be marked as
 * 'completed with failure,' as depicted in the diagram above.  For example,
 * connect timeout should be configured via a transport-specific option:
 * 使用{@link #await（long）}指定的超时值， {@link #await(long, TimeUnit)}, {@link #awaitUninterruptibly(long)}, or
 *  {@link #awaitUninterruptibly(long, TimeUnit)}完全与I / O *超时无关，
 *  如果I / O操作超时，则将来标记为'失败，如上图所示。例如，connect timeout应通过特定于传输的选项进行配置：
 *
 * <pre>
 * // BAD - NEVER DO THIS
 * //反例：
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // GOOD
 * //正例
 * {@link Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * //配置连接超时
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * 现在我们确信future已经完成。
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */

/**
 * Future最早出现于JDK的java.util.concurrent.Future,它用于表示异步操作的结果.由于Netty的Future都是与异步I/O操作相关的,因此命名为ChannelFuture,代表它与Channel操作相关.
 * 由于Netty中的所有I / O操作都是异步的,因此Netty为了解决调用者如何获取异步操作结果的问题而专门设计了ChannelFuture接口.
 * 因此,Channel与ChannelFuture可以说形影不离的.
 *
 * ChannelFuture有两种状态:未完成(uncompleted)和完成(completed).
 * 当令Channel开始一个I/O操作时,会创建一个新的ChannelFuture去异步完成操作.
 * 被创建时的ChannelFuture处于uncompleted状态(非失败,非成功,非取消);一旦ChannelFuture完成I/O操作,ChannelFuture将处于completed状态,结果可能有三种:
 * 1. 操作成功
 * 2. 操作失败
 * 3. 操作被取消(I/O操作被主动终止)
 *
 * 虽然可以通过ChannelFuture的get()方法获取异步操作的结果,但完成时间是无法预测的,若不设置超时时间则有可能导致线程长时间被阻塞;若是不能精确的设置超时时间则可能导致I/O操作中断
 * .因此,Netty建议通过GenericFutureListener接口执行异步操作结束后的回调.
 *
 * 虽然可以通过ChannelFuture的get()方法获取异步操作的结果,但完成时间是无法预测的,若不设置超时时间则有可能导致线程长时间被阻塞;
 * 若是不能精确的设置超时时间则可能导致I/O操作中断.因此,Netty建议通过GenericFutureListener接口执行异步操作结束后的回调.
 *
 * 另外,ChannelFuture允许添加一个或多个(移除一个或多个)GenericFutureListener监听接口,方法名:addListener(), addListeners(), removeListener(), removeListeners().
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     *
     * 返回和当前ChannelFuture相关联的io操作的channel
     */
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * Returns {@code true} if this {@link ChannelFuture} is a void future and so not allow to call any of the
     * following methods:
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     *
     * 如果当前ChannelFuture是个无效的future ，则返回true，所以也不允许调用当前ChannelFuture的以下方法
     *  <li>{@link #addListener(GenericFutureListener)}</li>
     *      *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *      *     <li>{@link #await()}</li>
     *      *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *      *     <li>{@link #await(long)} ()}</li>
     *      *     <li>{@link #awaitUninterruptibly()}</li>
     *      *     <li>{@link #sync()}</li>
     *      *     <li>{@link #syncUninterruptibly()}</li>
     *
     */
    boolean isVoid();
}
