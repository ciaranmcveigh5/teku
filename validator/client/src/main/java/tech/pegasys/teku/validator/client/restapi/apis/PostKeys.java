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

package tech.pegasys.teku.validator.client.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;

public class PostKeys extends RestApiEndpoint {
  private final KeyManager keyManager;
  public static final String ROUTE = "/eth/v1/keystores";
  private final Path slashingProtectionPath;

  public PostKeys(final KeyManager keyManager, final Path slashingProtectionPath) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("ImportKeystores")
            .summary("Import Keystores")
            .description("Import keystores generated by the Eth2.0 deposit CLI tooling.")
            .withBearerAuthSecurity()
            .requestBodyType(ValidatorTypes.POST_KEYS_REQUEST)
            .response(SC_OK, "Success response", ValidatorTypes.POST_KEYS_RESPONSE)
            .withAuthenticationResponses()
            .build());
    this.keyManager = keyManager;
    this.slashingProtectionPath = slashingProtectionPath;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final PostKeysRequest body = request.getRequestBody();
    if (body.getPasswords().size() != body.getKeystores().size()) {
      request.respondError(
          SC_BAD_REQUEST,
          String.format(
              "Keystores count (%d) and Passwords count (%d) differ, cannot proceed.",
              body.getKeystores().size(), body.getPasswords().size()));
      return;
    }
    if (body.getKeystores().isEmpty()) {
      request.respondOk(Collections.emptyList());
      return;
    }

    final Optional<SlashingProtectionImporter> slashingData =
        readSlashingProtectionDataIfPresent(body.getSlashingProtection());

    request.respondOk(
        keyManager.importValidators(body.getKeystores(), body.getPasswords(), slashingData));
  }

  private Optional<SlashingProtectionImporter> readSlashingProtectionDataIfPresent(
      final Optional<String> slashingData) {
    if (slashingData.isPresent()) {
      final InputStream slashingProtectionData =
          IOUtils.toInputStream(slashingData.get(), StandardCharsets.UTF_8);
      final SlashingProtectionImporter importer =
          new SlashingProtectionImporter(slashingProtectionPath);
      try {
        final Optional<String> initialiseError = importer.initialise(slashingProtectionData);
        if (initialiseError.isPresent()) {
          throw new BadRequestException(initialiseError.get());
        }
        return Optional.of(importer);
      } catch (IOException ex) {
        throw new BadRequestException(ex.getMessage());
      }
    }
    return Optional.empty();
  }
}