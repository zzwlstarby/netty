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

package io.netty.util;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool of {@link Constant}s.
 *
 * ConstantPool 常量池，来管理和维护Constant，
 * 常量池主要在ChannelOption或者AttributeKey等应用类中生成维护对应的常量。
 *
 * @param <T> the type of the constant
 */

/**
 * 概述：
 *      此类主要用作维护某种类型常量的常量池使用。
 *
 *      其中 ConcurrentMap<String, T> constants 成员变量 存放着 系统常量的类型，来看getOrCreate 方法
 *      创建并返回类型，并且是线程安全，我们来看如何保证线程安全的 putIfAbsent 这个方法 他是util.Concurrent.ConcurrentMap包里的方法，
 *      这个方法主要是通过 key 如果存在就返回值，如果不存在就返回null，比如两个线程 同时进入getOrCreate 方法里 并且 第一个线程 进行到final T tempConstant = newConstant(nextId(), name);
 *      而第二个线程 已经执行完   constant = constants.putIfAbsent(name, tempConstant); 这步，也就是说
 *      由于此时根据name 这个key 还并没有值 返回为Null， 此时 继续 if (constant == null) { return tempConstant;  }
 *      我们直接返回 刚才第二个线程 NEW出的  final T tempConstant = newConstant(nextId(), name); 这个值，再看第一个线程 执行 到 
 *      constant = constants.putIfAbsent(name, tempConstant); 返回的是第二个线程 刚存进去的值，这样通过 两个 
 *      if (constant == null) 与ConcurrentMap 就能保证 此方法的线程安全

 * @param <T>
 */
public abstract class ConstantPool<T extends Constant<T>> {

    /**
     * 存储常量，key为常量的name，value为常量对象。
     */
    private final ConcurrentMap<String, T> constants = PlatformDependent.newConcurrentHashMap();

    /**
     * 在常量池中创建常量时，设置常量的id，则通过此变量设置当前常量的id
     */
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     *
     * 根据类名的第一名字和第二名字字符串获取或者生成常量。
     */
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        if (firstNameComponent == null) {
            throw new NullPointerException("firstNameComponent");
        }
        if (secondNameComponent == null) {
            throw new NullPointerException("secondNameComponent");
        }

        return valueOf(firstNameComponent.getName() + '#' + secondNameComponent);
    }

    /**
     * Returns the {@link Constant} which is assigned to the specified {@code name}.
     * If there's no such {@link Constant}, a new one will be created and returned.
     * Once created, the subsequent calls with the same {@code name} will always return the previously created one
     * (i.e. singleton.)
     *
     * @param name the name of the {@link Constant}
     */
    public T valueOf(String name) {
        checkNotNullAndNotEmpty(name);
        return getOrCreate(name);
    }

    /**
     * Get existing constant by name or creates new one if not exists. Threadsafe
     *
     * 根据名称获取已经存在的常量对象，如果不存在则线程安全的创建一个常量对象。
     *
     * @param name the name of the {@link Constant}
     *
     * 概述：
     *     通过name得到一个已近存在的constant ，没有的话直接创建，线程安全的
     */
    private T getOrCreate(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T tempConstant = newConstant(nextId(), name);
            //ConcurrentHashMap单独的读写操作(get,put,putIfAbsent...)是完全线程安全且一致的
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {//考虑多线程的时候，二次判空处理
                return tempConstant;
            }
        }

        return constant;
    }

    /**
     * Returns {@code true} if a {@link AttributeKey} exists for the given {@code name}.
     *
     * 根据常量name，判断当前常量是否存在，如果存在则返回true
     *
     */
    public boolean exists(String name) {
        checkNotNullAndNotEmpty(name);
        return constants.containsKey(name);
    }

    /**
     * Creates a new {@link Constant} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link Constant} for the given {@code name} exists.
     *
     * 线程安全的方式根据常量名称创建一个常量对象，如果常量已经存在，则抛异常。
     */
    public T newInstance(String name) {
        checkNotNullAndNotEmpty(name);
        return createOrThrow(name);
    }

    /**
     * Creates constant by name or throws exception. Threadsafe
     * 线程安全的方式按名称创建常量或抛出异常。
     * @param name the name of the {@link Constant}
     */
    private T createOrThrow(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T tempConstant = newConstant(nextId(), name);
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                return tempConstant;
            }
        }

        throw new IllegalArgumentException(String.format("'%s' is already in use", name));
    }

    /**
     * 判断当前常量name是否为null或者空字符串
     * @param name
     * @return
     */
    private static String checkNotNullAndNotEmpty(String name) {
        ObjectUtil.checkNotNull(name, "name");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        return name;
    }

    /**
     * 子类 实现生成常量对象的方法。
     * @param id
     * @param name
     * @return
     */
    protected abstract T newConstant(int id, String name);

    /**
     * 创建新的常量时，生成对应的id
     * @return
     */
    @Deprecated
    public final int nextId() {
        return nextId.getAndIncrement();
    }
}
