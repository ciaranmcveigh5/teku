/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.api.blockselector;

import tech.pegasys.teku.api.ObjectAndMetaData;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlockAndMetaData extends ObjectAndMetaData<SignedBeaconBlock> {

  public BlockAndMetaData(
      final SignedBeaconBlock data,
      final SpecMilestone milestone,
      final boolean executionOptimistic,
      final boolean bellatrixEnabled,
      final boolean canonical) {
    super(data, milestone, executionOptimistic, bellatrixEnabled, canonical);
  }
}
