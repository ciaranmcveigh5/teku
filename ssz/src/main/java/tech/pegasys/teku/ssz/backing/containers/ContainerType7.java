/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.ssz.backing.containers;

import java.util.List;
import java.util.function.BiFunction;
import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.ViewType;

/** Autogenerated by tech.pegasys.teku.ssz.backing.ContainersGenerator */
public abstract class ContainerType7<
        C extends ContainerViewRead,
        V0 extends ViewRead,
        V1 extends ViewRead,
        V2 extends ViewRead,
        V3 extends ViewRead,
        V4 extends ViewRead,
        V5 extends ViewRead,
        V6 extends ViewRead>
    extends ContainerViewType<C> {

  public static <
          C extends ContainerViewRead,
          V0 extends ViewRead,
          V1 extends ViewRead,
          V2 extends ViewRead,
          V3 extends ViewRead,
          V4 extends ViewRead,
          V5 extends ViewRead,
          V6 extends ViewRead>
      ContainerType7<C, V0, V1, V2, V3, V4, V5, V6> create(
          ViewType<V0> fieldType0,
          ViewType<V1> fieldType1,
          ViewType<V2> fieldType2,
          ViewType<V3> fieldType3,
          ViewType<V4> fieldType4,
          ViewType<V5> fieldType5,
          ViewType<V6> fieldType6,
          BiFunction<ContainerType7<C, V0, V1, V2, V3, V4, V5, V6>, TreeNode, C> instanceCtor) {
    return new ContainerType7<>(
        fieldType0, fieldType1, fieldType2, fieldType3, fieldType4, fieldType5, fieldType6) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  protected ContainerType7(
      ViewType<V0> fieldType0,
      ViewType<V1> fieldType1,
      ViewType<V2> fieldType2,
      ViewType<V3> fieldType3,
      ViewType<V4> fieldType4,
      ViewType<V5> fieldType5,
      ViewType<V6> fieldType6) {

    super(
        List.of(
            fieldType0, fieldType1, fieldType2, fieldType3, fieldType4, fieldType5, fieldType6));
  }
}