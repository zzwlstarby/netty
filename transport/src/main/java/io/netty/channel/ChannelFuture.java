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
 * 概述：
 *      ChannelFuture的作用是用来保存Channel异步操作的结果。
 *
 *      Future最早出现于JDK的java.util.concurrent.Future,它用于表示异步操作的结果.由于Netty的Future都是与异步I/O操作相关的,
 *      因此命名为ChannelFuture,代表它与Channel操作相关.
 *
 *      由于Netty中的所有I / O操作都是异步的,因此Netty为了解决调用者如何获取异步操作结果的问题而专门设计了ChannelFuture接口.
 *      因此,Channel与ChannelFuture可以说形影不离的.
 *
 *      在Netty中所有的I/O操作都是异步的。这意味着任何的I/O调用都将立即返回，而不保证这些被请求的I/O操作在调用结束的时候已经完成。
 *      取而代之地，你会得到一个返回的ChannelFuture实例，这个实例将给你一些关于I/O操作结果或者状态的信息。
 *
 *      对于一个ChannelFuture可能已经完成，也可能未完成。当一个I/O操作开始的时候，一个新的future对象就会被创建。在开始的时候，
 *      新的future是未完成的状态－－它既非成功、失败，也非被取消，因为I/O操作还没有结束。如果I/O操作以成功、失败或者被取消中的
 *      任何一种状态结束了，那么这个future将会被标记为已完成，并包含更多详细的信息（例如：失败的原因）。请注意，即使是失败和被
 *      取消的状态，也是属于已完成的状态。
 *
 *      各种各样的方法被提供，用来检查I/O操作是否已完成、等待完成，并寻回I/O操作的结果。它同样允许你添加ChannelFutureListener，
 *      以便于在I/O操作完成的时候，你能够获得通知。
 *
 *      ChannelFuture有两种状态:未完成(uncompleted)和完成(completed).
 *      当令Channel开始一个I/O操作时,会创建一个新的ChannelFuture去异步完成操作.
 *      被创建时的ChannelFuture处于uncompleted状态(非失败,非成功,非取消);一旦ChannelFuture完成I/O操作,ChannelFuture将处于c
 *      ompleted状态,结果可能有三种:
 *      1. 操作成功
 *      2. 操作失败
 *      3. 操作被取消(I/O操作被主动终止)
 *
 *      虽然可以通过ChannelFuture的get()方法获取异步操作的结果,但完成时间是无法预测的,若不设置超时时间则有可能导致线程长时间被阻塞;
 *      若是不能精确的设置超时时间则可能导致I/O操作中断因此,Netty建议通过GenericFutureListener接口执行异步操作结束后的回调.
 *
 *      虽然可以通过ChannelFuture的get()方法获取异步操作的结果,但完成时间是无法预测的,若不设置超时时间则有可能导致线程长时间被阻塞;
 *      若是不能精确的设置超时时间则可能导致I/O操作中断.因此,Netty建议通过GenericFutureListener接口执行异步操作结束后的回调.
 *
 *      另外,ChannelFuture允许添加一个或多个(移除一个或多个)GenericFutureListener监听接口,方法名:addListener(), addListeners(),
 *      removeListener(), removeListeners().
 *
 *      ChannelFuture的用法
 *      优先使用addListener 而不是await和sysn
 *
 *      优先使用addListener(GenericFutureListener)，而非await()
 *      当做了一个I/O操作并有任何后续任务的时候，推荐优先使用addListener(GenericFutureListener)的方式来获得通知，而非await()。
 *      addListener(GenericFutureListener)是非阻塞的。它会把特定的ChannelFutureListener添加到ChannelFuture中，然后I/O线程
 *      会在I/O操作相关的future完成的时候通知监听器。ChannelFutureListener会利于最佳的性能和资源的利用，因为它一点阻塞都没有。
 *      然而，如果你不使用基于事件驱动的编程方式，去实现一个后续式的逻辑会变得诡异和难于理解。
 *
 *      对比来看，await()是一个阻塞的操作。一旦被调用，调用者线程会阻塞，直到操作完成。使用await()来实现一个后续式的逻辑会更容易，
 *      但是调用者线程会非常没必要的阻塞直到I/O操作完成，并且内部的线程通知是相对来说代价昂贵的。更有甚者，在一些特定的情况下会产生
 *      死锁
 *
 *      确实还是有一些情况在调用await()的时候会更方便的。在这种情况下，请确保你不是在一个I/O线程中调用的await()。否则，为了避
 *      免死锁的情况，BlockingOperationException将被提出。
 *
 *      不要混淆I/O timeout和await timeout
 *      你在使用Future.await(long)， Future.await(long, TimeUnit)，Future.awaitUninterruptibly(long)，
 *      或者Future.awaitUninterruptibly(long, TimeUnit)的时候，指定的timeout的值和I/O timeout一点关系都没有。
 *      如果一个操作超时了，future将会被标记为已完成－失败，
 *
 *      ChannelFuture允许添加一个或多个(移除一个或多个)GenericFutureListener监听接口,方法名:addListener(), addListeners(),
 *      removeListener(), removeListeners().
 *
 *
 * 1.Future/Promise 模式
 *   1.1 ChannelFuture的由来
 *    由于Netty中的Handler 处理都是异步IO操作，结果是未知的。
 *    Netty继承和扩展了JDK Future的API，定义了自身的Future系列类型，实现异步操作结果的获取和监控。
 *    其中，最为重要的是ChannelFuture 。
 *    之所以命名为ChannelFuture，表示跟Channel的操作有关。
 *     ChannelFuture用于获取Channel相关的操作结果，添加事件监听器，取消IO操作，同步等待。
 *
 *   1.2来自Netty的官方建议
 *   java.util.concurrent.Future是Java提供的接口，提供了对异步操作的简单干预。
 *   Future接口定义了isDone()、isCancellable()，用来判断异步执行状态。Future接口的get方法，可以用来获取结果。get方法首先会判断任务是否执行完成，如果完成就返回结果，否则阻塞线程，直到任务完成。
 *   Netty官方文档直接说明——Netty的网络操作都是异步的，Netty源码上大量使用了Future/Promise模式。
 *   如果用户操作调用了sync或者await方法，会在对应的future对象上阻塞用户线程，例如future.channel().closeFuture().sync()。
 *
 *   Netty 的Future 接口，在继承了java.util.concurrent.Future的基础上，增加了一系列监听器方法，比如addListener()、removeListener() 等等。Netty强烈建议，通过添加监听器的方式获取IO结果，
 *   而不是通过JDK Future的同步等待的方式去获取IO结果。
 *
 *   1.3Netty 的 Future 接口
 *   Netty扩展了Java的Future，增加了监听器Listener接口，通过监听器可以让异步执行更加有效率，不需要通过get来等待异步执行结束，
 *   而是通过监听器回调来精确地控制异步执行结束的时间点。这一点，正好是Netty在Future模式的最主要的改进。
 *
 *   1.4 ChannelFuture使用的实例
 *   Netty的出站和入站操作，都是异步的。
 *   以最为经典的NIO出站操作——write出站为例，说一下ChannelFuture的使用。
 *   代码如下：
 *   ChannelFuture future = ctx.channel().write(msg);
 *      future.addListener(
 *         new ChannelFutureListener()
 *         {
 *             @Override
 *             public void operationComplete(ChannelFuture future)
 *             {
 *                 // write操作完成后的回调代码
 *             }
 *         });
 *
 *      在write操作调用后，Netty并没有完成对Java NIO底层连接的写入操作，出站操作是异步执行的。
 *      如果需要获取IO结果，可以使用回调的方式。
 *
 *    使用ChannelFuture的异步完成后的回调，需要搭配使用另外的一个接口ChannelFutureListener ，
 *    他从父接口哪里继承了一个被回调到的operationComplete操作完成的方法。
 *
 *    ChannelFutureListener 的父亲接口是GenericFutureListener 接口。
 *    定义如下：
 *    public interface GenericFutureListener
 *                  <F extends Future<?>> extends EventListener
 *    {
 *       void operationComplete(F future) throws Exception;
 *    }
 *    异步操作完成后的回调代码，放在operationComplete方法中的实现中，就可以了。
 *
 *    1.5 Netty的 Promise接口
 *    Netty的Future，只是增加了监听器。整个异步的状态，是不能进行设置和修改的。
 *    换句话说，Future是只读的，是不可以写的。
 *    于是，Netty的 Promise接口扩展了Netty的Future接口，它表示一种可写的Future，就是可以设置异步执行的结果。
 *
 *    部分源码如下：
 *    public interface Promise<V> extends Future<V> {
 *     Promise<V> setSuccess(V result);
 *     Promise<V> setFailure(Throwable cause);
 *     boolean setUncancellable();
 *    //....
 *     }
 *
 *     在IO操作过程，如果顺利完成、或者发生异常，都可以设置Promise的结果，并且通知Promise的Listener们。
 *     而ChannelPromise接口，则继承扩展了Promise和ChannelFuture。所以，ChannelPromise既绑定了Channel，又具备了监听器的功能，
 *     还可以设置IO操作的结果，是Netty实际编程使用的最多的接口。
 *     在AbstratChannel的代码中，相当多的IO操作，都会返回ChannelPromise类型实例作为调用的返回值。 通过这个返回值，
 *     客户程序可以用于读取IO操作的结果，执行IO操作真正完成后的回调。
 *
 *
 *     1.6 ChannelPromise的监控流程
 *     在AbstractChannel中，定义了几个对Channel的异步状态进行监控的Promise和Future成员，用于监控Channel的连接是否成功，连接是否关闭。
 *     源码如下：
 *     public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {
 *         //连接成功的监控
 *         private final ChannelFuture succeededFuture = new SucceededChannelFuture(this, null);
 *         //连接关闭的监控
 *         private final CloseFuture closeFuture = new CloseFuture(this);
 *        //...
 *      }
 *      对于每个Channel对象，都会有唯一的一个CloseFuture 成员，用来表示关闭的异步干预。如果要监控Channel的关闭，或者同步等待Channel关闭。
 *      一般情况下，在应用程序中使用如下的代码：
 *      // Start the server.
 *      ChannelFuture f = b.bind(PORT).sync();
 *      // Wait until the server socket is closed.
 *      f.channel().closeFuture().sync();
 *
 *      一般来说，编写以上代码的都是在Main线程中用来启动ServerBootStrap的，所以Main线程会被阻塞，保证服务端Channel的正常运行。
 *      上面的代码中，channel.closeFuture()不做任何操作，只是简单的返回channel对象中的closeFuture对象。而CloseFuture的sync方法，会将当前线程阻塞在CloseFuture上。
 *      那么，f.channel().closeFuture().sync() 实际是如何工作的呢？
 *
 *      1.7 CloseFuture的sync 同步方法
 *      CloseFuture继承了DefaultPromise的sync同步方法。
 *      DefaultPromise的代码如下：
 *
 *      从源码可以看出，sync方法，调用了await方法。
 *      在await方法中，CloseFuture 使用java 基础的synchronized 方法进行线程同步；并且，使用CloseFuture.wait / notify 这组来自Object根类中的古老方法进行线程之间的等待和唤醒。
 *      在await方法，不断的自旋，判断当前的 CloseFuture 实例的结果是否已经完成，如果没有完成 !isDone() ，就不断的等待。一直到 isDone() 的值为true。
 *
 *
 *      CloseFuture的 isDone() 的条件是否能够满足，和Channel的close 关闭连接的出站操作有关。
 *      下一步，我们来看 isDone() 的条件，如何才能够满足？
 *
 *      1.8 close 出站处理流程
 *      在Netty中，close 关闭连接的操作，属于所有的出站操作的一种。关于Netty出站处理的流程，在前面的文字中，已经非常详细的介绍了。这里不再赘述，只是简单的列出一个流程图。
 *      close 关闭连接的出站操作，其流程如下图所示：
 *      channel.close->pipeline.close->tail.close->outbound.close->head.close->unsafe.close->unsafe.doClose;
 *
 *      1.9 unsafe.doClose
 *       unsafe.doClose 方法中，设置了CloseFuture 的result值。
 *
 *      1.10 closeFuture.setClosed()
 *      在closeFuture.setClosed() 设置关闭的结果的过程中，主要完成以下三个工作：
 *          1  设置result的值
 *          2  notifyAll，唤醒在本Promise上等待的线程
 *          3  回调listener
 *
 *
 *      1.11 警惕死锁：Reactor线程不能sync
 *      在上面的源码中，最终触发future对象的notify动作的线程，都是eventLoop线程（Reactor线程）。
 *      一般情况下，Channel的出站和入站操作，也都是在eventLoop线程的轮询任务中完成的。
 *      例如因为不论是用户直接关闭channel，或者eventLoop的轮询状态关闭channel，都会在eventLoop的线程内完成notify动作。notify那些通过sync操作，正在等待CloseFuture的哪些阻塞线程。
 *      所以不要在Reactor线程内调用future对象的sync或者await方法。如果在Reactor线程进行sync或者await，会有可能引起死锁。
 *      为什么呢？
 *      在Reactor线程进行sync时，会进入等待状态，等待Future(DefaultPromise)的 isDone 的条件满足。通过前面的例子，我们已经看到了，而Future的isDone的条件，又需要Reactor线程的出站或者入站操作来满足。这是，Reactor线程既然已经处于等待状态，怎么可能再进行其他的出站或者入站操作呢？相当于自己等自己，这就是典型的死锁。
 *      在实际开发中，由于应用程序代码都是编写在自定义的channelHandler处理器中，而channelHandler是在eventLoop线程（Reactor线程）内执行的。所以，不能在channelHandler中调用Future(DefaultPromise)的sync或者await两个同步方法。
 *      正确的做法是：通过给Future(DefaultPromise) 增加listeners监听器 的方式，来干预异步操作的过程，处理异步操作的结果。
 *      这样，可以避免使用Future带来的死锁。
 *
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
