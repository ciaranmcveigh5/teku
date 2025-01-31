/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BEACON_BLOCK_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SUBCOMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BEACON_BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SUBCOMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetSyncCommitteeContributionResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class GetSyncCommitteeContribution extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/sync_committee_contribution";
  private final ValidatorDataProvider provider;

  public GetSyncCommitteeContribution(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetSyncCommitteeContribution(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getSyncCommitteeContribution")
            .summary("Produce a sync committee contribution")
            .description(
                "Returns a `SyncCommitteeContribution` that is the aggregate of `SyncCommitteeMessage` "
                    + "values known to this node matching the specified slot, subcommittee index and beacon block root.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .queryParamRequired(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .queryParamRequired(SUBCOMMITTEE_INDEX_PARAMETER)
            .queryParamRequired(BEACON_BLOCK_ROOT_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.provider = validatorDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Produce a sync committee contribution",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      queryParams = {
        @OpenApiParam(
            name = SLOT,
            description =
                "`uint64` The slot for which a sync committee contribution should be created.",
            required = true),
        @OpenApiParam(
            name = SUBCOMMITTEE_INDEX,
            description = "`uint64` The subcommittee index for which to produce the contribution.",
            required = true),
        @OpenApiParam(
            name = BEACON_BLOCK_ROOT,
            description = "`bytes32` The block root for which to produce the contribution.",
            required = true)
      },
      description =
          "Returns a `SyncCommitteeContribution` that is the aggregate of `SyncCommitteeMessage` "
              + "values known to this node matching the specified slot, subcommittee index and beacon block root.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetSyncCommitteeContributionResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid request syntax."),
        @OpenApiResponse(
            status = RES_NOT_FOUND,
            description = "No matching sync committee messages were found"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));
    final Bytes32 blockRoot = request.getQueryParameter(BEACON_BLOCK_ROOT_PARAMETER);
    final Integer subcommitteeIndex = request.getQueryParameter(SUBCOMMITTEE_INDEX_PARAMETER);

    if (subcommitteeIndex < 0
        || subcommitteeIndex >= NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT) {
      throw new IllegalArgumentException(
          String.format(
              "Subcommittee index needs to be between 0 and %s, %s is outside of this range.",
              NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT - 1, subcommitteeIndex));
    } else if (provider.isPhase0Slot(slot)) {
      throw new IllegalArgumentException(String.format("Slot %s is not an Altair slot", slot));
    }

    final SafeFuture<Optional<SyncCommitteeContribution>> future =
        provider.createSyncCommitteeContribution(slot, subcommitteeIndex, blockRoot);

    request.respondAsync(
        future.thenApply(
            maybeSyncCommitteeContribution ->
                maybeSyncCommitteeContribution
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<SyncCommitteeContribution> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SyncCommitteeContributionSchema typeDefinition =
        SchemaDefinitionsAltair.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ALTAIR))
            .getSyncCommitteeContributionSchema();

    return SerializableTypeDefinition.object(SyncCommitteeContribution.class)
        .name("GetSyncCommitteeContributionResponse")
        .withField("data", typeDefinition.getJsonTypeDefinition(), Function.identity())
        .build();
  }
}
