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

package tech.pegasys.teku.networking.eth2.gossip;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class PublisherEnabledGossipManager<T extends SszData>
    extends AbstractGossipManager<T> {

  protected PublisherEnabledGossipManager(
      final RecentChainData recentChainData,
      final GossipTopicName topicName,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<T> processor,
      final GossipPublisher<T> publisher,
      final SszSchema<T> gossipType,
      final int maxMessageSize) {
    super(
        recentChainData,
        topicName,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        processor,
        gossipType,
        maxMessageSize);
    publisher.subscribe(this::publishMessage);
  }

  // Methods overridden to add synchronization because the publisher subscription will trigger
  // publishing messages without going through GossipForkManager where synchronization is normally
  // done

  @Override
  protected synchronized void publishMessage(final T message) {
    super.publishMessage(message);
  }

  @Override
  public synchronized void subscribe() {
    super.subscribe();
  }

  @Override
  public synchronized void unsubscribe() {
    super.unsubscribe();
  }
}
