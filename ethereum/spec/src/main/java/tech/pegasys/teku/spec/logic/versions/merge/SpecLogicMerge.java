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

package tech.pegasys.teku.spec.logic.versions.merge;

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.forkchoice.TransitionStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MergeTransitionHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.PowBlock;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.merge.block.BlockProcessorMerge;
import tech.pegasys.teku.spec.logic.versions.merge.forktransition.MergeStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.merge.forktransition.TransitionStoreUtil;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.BeaconStateAccessorsMerge;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;
import tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch.EpochProcessorMerge;
import tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch.ValidatorStatusFactoryMerge;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class SpecLogicMerge extends AbstractSpecLogic {

  private final ExecutionPayloadUtil executionPayloadUtil;
  private final MergeTransitionHelpers mergeTransitionHelpers;
  private final TransitionStoreUtil transitionStoreUtil;

  private TransitionStore transitionStore;

  private SpecLogicMerge(
      final Predicates predicates,
      final MiscHelpersMerge miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryMerge validatorStatusFactory,
      final EpochProcessorMerge epochProcessor,
      final BlockProcessorMerge blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final ExecutionPayloadUtil executionPayloadUtil,
      final MergeTransitionHelpers mergeTransitionHelpers,
      final TransitionStore transitionStore,
      final TransitionStoreUtil transitionStoreUtil,
      final MergeStateUpgrade stateUpgrade) {
    super(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        Optional.of(stateUpgrade));
    this.executionPayloadUtil = executionPayloadUtil;
    this.mergeTransitionHelpers = mergeTransitionHelpers;
    this.transitionStoreUtil = transitionStoreUtil;

    this.transitionStore = transitionStore;
  }

  public static SpecLogicMerge create(
      final SpecConfigMerge config, final SchemaDefinitionsMerge schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpersMerge miscHelpers = new MiscHelpersMerge(config);
    final BeaconStateAccessorsMerge beaconStateAccessors =
        new BeaconStateAccessorsMerge(config, predicates, miscHelpers);
    final BeaconStateMutators beaconStateMutators =
        new BeaconStateMutators(config, miscHelpers, beaconStateAccessors);

    // Operation validaton
    final OperationSignatureVerifier operationSignatureVerifier =
        new OperationSignatureVerifier(miscHelpers, beaconStateAccessors);

    // Util
    final ValidatorsUtil validatorsUtil =
        new ValidatorsUtil(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config, schemaDefinitions, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtil attestationUtil = new AttestationUtil(beaconStateAccessors, miscHelpers);
    final OperationValidator operationValidator =
        OperationValidator.create(
            config, predicates, miscHelpers, beaconStateAccessors, attestationUtil);
    final ValidatorStatusFactoryMerge validatorStatusFactory =
        new ValidatorStatusFactoryMerge(
            config, beaconStateUtil, attestationUtil, beaconStateAccessors, predicates);
    final EpochProcessorMerge epochProcessor =
        new EpochProcessorMerge(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory);
    final ExecutionPayloadUtil executionPayloadUtil = new ExecutionPayloadUtil();
    final BlockProcessorMerge blockProcessor =
        new BlockProcessorMerge(
            config,
            predicates,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            operationSignatureVerifier,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            operationValidator,
            executionPayloadUtil);
    final MergeTransitionHelpers mergeTransitionHelpers = new MergeTransitionHelpers(miscHelpers);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(
            config,
            beaconStateAccessors,
            attestationUtil,
            blockProcessor,
            miscHelpers,
            mergeTransitionHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, blockProcessor);

    // State upgrade
    final MergeStateUpgrade stateUpgrade =
        new MergeStateUpgrade(config, schemaDefinitions, beaconStateAccessors);

    // Transition total difficulty is set on transition logic upgrade
    final TransitionStore transitionStore = TransitionStore.create(UInt256.ZERO);
    final TransitionStoreUtil transitionStoreUtil = new TransitionStoreUtil(config);

    return new SpecLogicMerge(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        executionPayloadUtil,
        mergeTransitionHelpers,
        transitionStore,
        transitionStoreUtil,
        stateUpgrade);
  }

  @Override
  public void initializeTransitionStore(BeaconState state) {
    PowBlock powBlock = mergeTransitionHelpers.getPowBlock(state.getEth1_data().getBlock_hash());
    Preconditions.checkArgument(powBlock.isProcessed, "Anchor PowBlock is not processed");
    Preconditions.checkArgument(powBlock.isValid, "Anchor PowBlock is invalid");

    this.transitionStore = transitionStoreUtil.getTransitionStore(powBlock);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return Optional.empty();
  }

  @Override
  public Optional<MergeTransitionHelpers> getMergeTransitionHelpers() {
    return Optional.of(mergeTransitionHelpers);
  }

  @Override
  public Optional<ExecutionPayloadUtil> getExecutionPayloadUtil() {
    return Optional.of(executionPayloadUtil);
  }

  @Override
  public Optional<TransitionStore> getTransitionStore() {
    return Optional.of(transitionStore);
  }
}