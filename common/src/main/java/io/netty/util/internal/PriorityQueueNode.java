/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.internal;

/**
 * Provides methods for {@link DefaultPriorityQueue} to maintain internal state. These methods should generally not be
 * used outside the scope of {@link DefaultPriorityQueue}.
 *
 * 当前接口提供了维护{@link DefaultPriorityQueue}内部状态的方法。通常不应在{@link DefaultPriorityQueue}的范围之外使用这些方法。
 * 也就是说PriorityQueueNode的方法应该在{@link DefaultPriorityQueue}范围之内使用。
 */
public interface PriorityQueueNode {

    /**
     * This should be used to initialize the storage returned by {@link #priorityQueueIndex(DefaultPriorityQueue)}.
     *
     * 这应该用于初始化{@link #priorityQueueIndex（DefaultPriorityQueue）}返回的存储。
     */
    int INDEX_NOT_IN_QUEUE = -1;

    /**
     * Get the last value set by {@link #priorityQueueIndex(DefaultPriorityQueue, int)} for the value corresponding to
     * {@code queue}.
     *
     * <p>
     * Throwing exceptions from this method will result in undefined behavior.
     */
    int priorityQueueIndex(DefaultPriorityQueue<?> queue);

    /**
     * Used by {@link DefaultPriorityQueue} to maintain state for an element in the queue.
     * 由{@link DefaultPriorityQueue}用于维护队列中元素的状态。
     * 此方法为指定队列设置索引。
     * <p>
     * Throwing exceptions from this method will result in undefined behavior.
     * @param queue The queue for which the index is being set. 正在为其设置索引的队列
     * @param i The index as used by {@link DefaultPriorityQueue}. {@link DefaultPriorityQueue}使用的索引。
     *
     *
     */
    void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i);

}