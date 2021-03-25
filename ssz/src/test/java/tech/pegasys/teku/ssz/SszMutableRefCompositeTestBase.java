/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.ssz.SszDataAssert.assertThatSszData;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.schema.SszSchema;

public interface SszMutableRefCompositeTestBase extends SszMutableCompositeTestBase {
  RandomSszDataGenerator generator = new RandomSszDataGenerator();

  default Stream<Arguments> sszMutableByRefCompositeArguments() {
    return SszDataTestBase.passWhenEmpty(
        sszWritableData()
            .map(d -> (SszComposite<?>) d)
            .flatMap(
                data ->
                    IntStream.range(0, data.size())
                        .limit(32)
                        .filter(
                            i ->
                                data.getSchema()
                                    .getChildSchema(i)
                                    .getDefault()
                                    .isWritableSupported())
                        .limit(1)
                        .boxed()
                        .flatMap(
                            updateChildIndex ->
                                Stream.of(
                                    Arguments.of(data.createWritableCopy(), updateChildIndex)))));
  }

  @MethodSource("sszMutableByRefCompositeArguments")
  @ParameterizedTest
  default void getByRef_childUpdatedByRefShouldCommit(
      SszMutableRefComposite<SszData, SszMutableData> data, int updateChildIndex) {
    SszComposite<SszData> origData = data.commitChanges();

    SszMutableData mutableChild = data.getByRef(updateChildIndex);
    SszData newChildValue = updateSomething(mutableChild);

    assertThatSszData(data.get(updateChildIndex))
        .isEqualByGettersTo(newChildValue)
        .isEqualBySszTo(newChildValue);
    IntStream.range(0, data.size())
        .limit(32)
        .forEach(
            i -> {
              if (i != updateChildIndex) {
                assertThatSszData(data.get(i)).isEqualByAllMeansTo(origData.get(i));
              }
            });

    SszComposite<SszData> updatedData = data.commitChanges();

    assertThatSszData(updatedData).isNotEqualByAllMeansTo(data);
    assertThatSszData(updatedData.get(updateChildIndex)).isEqualByAllMeansTo(newChildValue);
    IntStream.range(0, data.size())
        .limit(32)
        .forEach(
            i -> {
              if (i != updateChildIndex) {
                assertThatSszData(updatedData.get(i)).isEqualByAllMeansTo(origData.get(i));
              }
            });
  }

  @MethodSource("sszMutableByRefCompositeArguments")
  @ParameterizedTest
  default void getByRef_invalidatorShouldBeCalledWhenChildUpdated(
      SszMutableRefComposite<SszData, SszMutableData> data, int updateChildIndex) {
    AtomicInteger counter = new AtomicInteger();
    data.setInvalidator(__ -> counter.incrementAndGet());
    SszMutableData mutableChild = data.getByRef(updateChildIndex);
    mutableChild.clear();

    assertThat(counter).hasValue(1);
  }

  @MethodSource("sszMutableByRefCompositeArguments")
  @ParameterizedTest
  default void getByRef_childSetThenUpdatedByRefShouldWork(
      SszMutableRefComposite<SszData, SszMutableData> data, int updateChildIndex) {
    SszComposite<SszData> origData = data.commitChanges();
    SszSchema<?> childSchema = data.getSchema().getChildSchema(updateChildIndex);

    SszData newChildValue = generator.randomData(childSchema);
    data.set(updateChildIndex, newChildValue);

    assertThatSszData(data.get(updateChildIndex))
        .isNotEqualByAllMeansTo(origData.get(updateChildIndex));
    SszMutableData byRef = data.getByRef(updateChildIndex);
    SszData newChildValueByRef = updateSomething(byRef);

    assertThatSszData(data.get(updateChildIndex)).isEqualByGettersTo(newChildValueByRef);
    assertThatSszData(data.commitChanges().get(updateChildIndex))
        .isEqualByAllMeansTo(newChildValueByRef);
  }

  @MethodSource("sszMutableByRefCompositeArguments")
  @ParameterizedTest
  default void set_mutableValueShouldNotBeShared(
      SszMutableRefComposite<SszData, SszMutableData> data, int updateChildIndex) {
    SszSchema<?> childSchema = data.getSchema().getChildSchema(updateChildIndex);

    SszData newChildValue = generator.randomData(childSchema);
    SszMutableData mutableChild = newChildValue.createWritableCopy();
    SszData sszMutableChildUpdated1 = updateSomething(mutableChild);
    data.set(updateChildIndex, mutableChild);

    // updating `mutableChild` should not affect `data`
    SszData sszMutableChildUpdated2 = updateSomething(mutableChild);

    assertThatSszData((SszData) data.getByRef(updateChildIndex))
        .isEqualByGettersTo(sszMutableChildUpdated1);
    assertThatSszData(data.get(updateChildIndex)).isEqualByGettersTo(sszMutableChildUpdated1);
    assertThatSszData(data.commitChanges().get(updateChildIndex))
        .isEqualByAllMeansTo(sszMutableChildUpdated1);
    assertThatSszData(data.commitChanges().get(updateChildIndex))
        .isNotEqualByAllMeansTo(sszMutableChildUpdated2);

    // and vice versa: updating `data` child should not affect `mutableChild`
    SszMutableData childByRef = data.getByRef(updateChildIndex);
    SszData sszMutableChildUpdated3 = updateSomething(childByRef);

    assertThatSszData((SszData) mutableChild).isEqualByGettersTo(sszMutableChildUpdated2);
    assertThatSszData(mutableChild.commitChanges()).isEqualByAllMeansTo(sszMutableChildUpdated2);
    assertThatSszData((SszData) data.getByRef(updateChildIndex))
        .isEqualByGettersTo(sszMutableChildUpdated3);
    assertThatSszData(data.get(updateChildIndex)).isEqualByGettersTo(sszMutableChildUpdated3);
    assertThatSszData(data.commitChanges().get(updateChildIndex))
        .isEqualByAllMeansTo(sszMutableChildUpdated3);
  }

  @SuppressWarnings("unchecked")
  static SszData updateSomething(SszMutableData mutableData) {
    Assumptions.assumeTrue(mutableData instanceof SszMutableComposite);
    SszMutableComposite<SszData> mutableComposite = (SszMutableComposite<SszData>) mutableData;
    Assumptions.assumeTrue(mutableComposite.size() > 0);
    SszComposite<SszData> orig = mutableComposite.commitChanges();
    SszData newChildData = generator.randomData(mutableComposite.getSchema().getChildSchema(0));
    mutableComposite.set(0, newChildData);
    SszMutableComposite<SszData> writableCopy = orig.createWritableCopy();
    writableCopy.set(0, newChildData);
    return writableCopy.commitChanges();
  }
}