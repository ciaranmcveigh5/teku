/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.loader;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;

public class ValidatorLoader {

  private final SlashingProtector slashingProtector;
  private final PublicKeyLoader publicKeyLoader;
  private final AsyncRunner asyncRunner;
  private final MetricsSystem metricsSystem;

  private ValidatorLoader(
      final SlashingProtector slashingProtector,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem) {
    this.slashingProtector = slashingProtector;
    this.publicKeyLoader = publicKeyLoader;
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
  }

  public static ValidatorLoader create(
      final SlashingProtector slashingProtector,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem) {
    return new ValidatorLoader(slashingProtector, publicKeyLoader, asyncRunner, metricsSystem);
  }

  public Map<BLSPublicKey, Validator> initializeValidators(
      final ValidatorConfig config, final InteropConfig interopConfig) {
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        Suppliers.memoize(new HttpClientExternalSignerFactory(config)::get);
    return initializeValidators(config, interopConfig, externalSignerHttpClientFactory);
  }

  @VisibleForTesting
  Map<BLSPublicKey, Validator> initializeValidators(
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final Supplier<HttpClient> externalSignerHttpClientFactory) {

    final Map<BLSPublicKey, ValidatorProvider> validatorProviders = new HashMap<>();
    if (interopConfig.isInteropEnabled()) {
      addValidatorsFromSource(
          validatorProviders,
          slashingProtected(new MockStartValidatorSource(interopConfig, asyncRunner)));
    } else {
      // External signers take preference to local if the same key is specified in both places
      addNewExternalSigners(config, externalSignerHttpClientFactory, validatorProviders);
      addNewLocalSigners(config, validatorProviders);
    }

    // Get validator connection info and create a new Validator object and put it into the
    // Validators map
    final Map<BLSPublicKey, Validator> validators = new HashMap<>();
    validatorProviders.forEach(
        (key, provider) ->
            validators.put(
                key,
                new Validator(
                    provider.getPublicKey(),
                    provider.createSigner(),
                    config.getGraffitiProvider())));

    STATUS_LOG.validatorsInitialised(
        validators.values().stream()
            .map(Validator::getPublicKey)
            .map(BLSPublicKey::toAbbreviatedString)
            .collect(Collectors.toList()));
    return validators;
  }

  private void addNewLocalSigners(
      final ValidatorConfig config, final Map<BLSPublicKey, ValidatorProvider> validatorProviders) {
    if (config.getValidatorKeystorePasswordFilePairs() != null) {
      addValidatorsFromSource(
          validatorProviders,
          new LocalValidatorSource(
              new KeystoresValidatorKeyProvider(new KeystoreLocker(), config), asyncRunner));
    }
  }

  private void addNewExternalSigners(
      final ValidatorConfig config,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final Map<BLSPublicKey, ValidatorProvider> validatorProviders) {
    final ValidatorSource externalValidatorSource =
        new ExternalValidatorSource(
            metricsSystem, config, externalSignerHttpClientFactory, publicKeyLoader, asyncRunner);
    addValidatorsFromSource(
        validatorProviders,
        config.isValidatorExternalSignerSlashingProtectionEnabled()
            ? slashingProtected(externalValidatorSource)
            : externalValidatorSource);
  }

  private void addValidatorsFromSource(
      final Map<BLSPublicKey, ValidatorProvider> validators, final ValidatorSource source) {
    source
        .getAvailableValidators()
        .forEach(
            validatorProvider ->
                validators.putIfAbsent(validatorProvider.getPublicKey(), validatorProvider));
  }

  private ValidatorSource slashingProtected(final ValidatorSource validatorSource) {
    return new SlashingProtectedValidatorSource(validatorSource, slashingProtector);
  }
}