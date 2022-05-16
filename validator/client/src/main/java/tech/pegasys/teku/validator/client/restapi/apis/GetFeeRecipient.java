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

package tech.pegasys.teku.validator.client.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.validator.client.BeaconProposerPreparer;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;

public class GetFeeRecipient extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/feerecipient";
  private final Optional<BeaconProposerPreparer> beaconProposerPreparer;
  private static final String PARAM_PUBKEY = "pubkey";
  static final ParameterMetadata<BLSPublicKey> PARAM_PUBKEY_TYPE =
      new ParameterMetadata<>(
          PARAM_PUBKEY,
          new StringBasedPrimitiveTypeDefinition.StringTypeBuilder<BLSPublicKey>()
              .formatter(value -> value.toBytesCompressed().toHexString())
              .parser(value -> BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(value)))
              .pattern("^0x[a-fA-F0-9]{96}$")
              .example(
                  "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
              .build());

  private static final SerializableTypeDefinition<GetFeeRecipientResponse> FEE_RECIPIENT_DATA =
      SerializableTypeDefinition.object(GetFeeRecipientResponse.class)
          .name("GetFeeRecipientData")
          .withField(
              "ethaddress", Eth1Address.ETH1ADDRESS_TYPE, GetFeeRecipientResponse::getEthAddress)
          .withOptionalField(
              "pubkey", ValidatorTypes.PUBKEY_TYPE, GetFeeRecipientResponse::getPublicKey)
          .build();

  static final SerializableTypeDefinition<GetFeeRecipientResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GetFeeRecipientResponse.class)
          .withField("data", FEE_RECIPIENT_DATA, Function.identity())
          .build();

  public GetFeeRecipient(final Optional<BeaconProposerPreparer> beaconProposerPreparer) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("GetFeeRecipient")
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .summary("Get validator fee recipient")
            .description(
                "List the validator public key to eth address mapping for fee recipient feature on a specific public key. "
                    + "The validator public key will return with the default fee recipient address if a specific one was not found.\n\n"
                    + "WARNING: The fee_recipient is not used on Phase0 or Altair networks.")
            .response(SC_OK, "Success response", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.beaconProposerPreparer = beaconProposerPreparer;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    final Optional<Eth1Address> maybeFeeRecipient =
        beaconProposerPreparer.isPresent()
            ? beaconProposerPreparer.get().getFeeRecipient(publicKey)
            : Optional.empty();
    if (maybeFeeRecipient.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Fee recipient not found");
      return;
    }
    request.respondOk(new GetFeeRecipientResponse(maybeFeeRecipient.get()));
  }

  static class GetFeeRecipientResponse {
    private final Eth1Address ethAddress;
    private final Optional<BLSPublicKey> publicKey;

    public GetFeeRecipientResponse(
        final Eth1Address ethAddress, final Optional<BLSPublicKey> publicKey) {
      this.ethAddress = ethAddress;
      this.publicKey = publicKey;
    }

    public GetFeeRecipientResponse(final Eth1Address ethAddress) {
      this(ethAddress, Optional.empty());
    }

    public Eth1Address getEthAddress() {
      return ethAddress;
    }

    public Optional<BLSPublicKey> getPublicKey() {
      return publicKey;
    }
  }
}