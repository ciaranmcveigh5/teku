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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetBlock extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/blocks/:block_id";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ChainDataProvider chainDataProvider;

  public GetBlock(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetBlock(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlock")
            .summary("Get block")
            .description(
                "Retrieves block details for given block id.\n\n"
                    + "__NOTE__: only phase 0 blocks are returned, use `/eth/v2/beacon/blocks/{block_id}` for multiple milestone support.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      deprecated = true,
      summary = "Get block",
      tags = {TAG_BEACON},
      description =
          "Retrieves block details for given block id.\n\n"
              + "__NOTE__: only phase 0 blocks are returned, use `/eth/v2/beacon/blocks/{block_id}` for multiple milestone support.",
      pathParams = {@OpenApiParam(name = PARAM_BLOCK_ID, description = PARAM_BLOCK_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = GetBlockResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> future =
        chainDataProvider.getSignedBeaconBlock(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeObjectAndMetaData -> {
              if (maybeObjectAndMetaData.isEmpty()) {
                return AsyncApiResponse.respondNotFound();
              }

              ObjectAndMetaData<SignedBeaconBlock> response = maybeObjectAndMetaData.get();

              if (!chainDataProvider
                  .getMilestoneAtSlot(response.getData().getMessage().getSlot())
                  .equals(SpecMilestone.PHASE0)) {
                final String message =
                    String.format(
                        "Slot %s is not a phase0 slot, please fetch via /eth/v2/beacon/blocks",
                        response.getData().getMessage().getSlot());
                return AsyncApiResponse.respondWithError(SC_BAD_REQUEST, message);
              }

              return AsyncApiResponse.respondOk(response.getData());
            }));
  }

  private static SerializableTypeDefinition<SignedBeaconBlock> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    SerializableTypeDefinition<SignedBeaconBlock> schemaDefinition =
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.PHASE0)
            .getSignedBeaconBlockSchema()
            .getJsonTypeDefinition();

    return SerializableTypeDefinition.object(SignedBeaconBlock.class)
        .name("GetBlockResponse")
        .withField("data", schemaDefinition, Function.identity())
        .build();
  }
}
