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
package io.netty.util;

/**
 * Holds {@link Attribute}s which can be accessed via {@link AttributeKey}.
 *
 *
 *
 * Implementations must be Thread-safe.
 *
 * 实现当前接口的方法必须时线程安全的
 *
 * 概述：
 *      谁实现了AttributeMap接口？
        答案是所有的Channel和ChannelHandlerContext，
 *
 *      AttributeMap是存储属性的一个map
 *
 *      AttributeKey作为AttributeMap的key，Attribute作为AttributeMap的value：
 *      AttributeMap 、 Attribute 、 AttributeKey 分别对应Map、value、Key，netty对他们进行了一次封装。
 */
public interface AttributeMap {
    /**
     * Get the {@link Attribute} for the given {@link AttributeKey}. This method will never return null, but may return
     * an {@link Attribute} which does not have a value set yet.
     */
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * Returns {@code} true if and only if the given {@link Attribute} exists in this {@link AttributeMap}.
     */
    <T> boolean hasAttr(AttributeKey<T> key);
}
