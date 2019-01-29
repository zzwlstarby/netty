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
 * A singleton which is safe to compare via the {@code ==} operator. Created and managed by {@link ConstantPool}.
 *
 * 通过{@code ==}运算符可以安全比较的一个单例。由{@link ConstantPool}创建和管理。
 *
 * 概述：
 *      此接口支撑了netty上层应用所需要的所有常量的常量体系。
 *      netty本身需要的常量都是通过实现Constant接口的类来保存的。
 *
 *     是单例的并且是可以用过“==”安全比较的。使用ConstantPool创建和管理
 */
public interface Constant<T extends Constant<T>> extends Comparable<T> {

    /**
     * Returns the unique number assigned to this {@link Constant}.
     * 返回分配给此{@link Constant}的唯一编号。
     */
    int id();

    /**
     * Returns the name of this {@link Constant}.
     * 返回此{@link Constant}的名称。
     */
    String name();
}
