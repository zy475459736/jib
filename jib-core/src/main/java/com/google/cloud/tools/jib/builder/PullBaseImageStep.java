/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.image.DuplicateLayerException;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Pulls the base image manifest. */
class PullBaseImageStep extends AsyncStep<Image> {

  static final ImmutableSet<Class<? extends AsyncStep<?>>> dependencies =
      ImmutableSet.of(AuthenticatePullStep.class);

  private final Map<Class<? extends AsyncStep<?>>, AsyncStep<?>> dependencyMap = new HashMap<>();

  private static final String DESCRIPTION = "Pulling base image manifest";

  private static final String DESCRIPTION = "Pulling base image manifest";

  private final BuildConfiguration buildConfiguration;

  PullBaseImageStep(BuildConfiguration buildConfiguration) {
    this.buildConfiguration = buildConfiguration;
  }

  /** Depends on {@code authenticatePullStep}. */
  @Override
  public Image call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          DuplicateLayerException, LayerCountMismatchException, ExecutionException,
          InterruptedException {
    AuthenticatePullStep authenticatePullStep = getDependency(AuthenticatePullStep.class);

    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
              // TODO: Simplify with a method on authenticatePullStep.
              NonBlockingFutures.get(authenticatePullStep.getFuture()),
              buildConfiguration.getBaseImageServerUrl(),
              buildConfiguration.getBaseImageName());

      ManifestTemplate manifestTemplate =
          registryClient.pullManifest(buildConfiguration.getBaseImageTag());

      // TODO: Make schema version be enum.
      switch (manifestTemplate.getSchemaVersion()) {
        case 1:
          V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
          return JsonToImageTranslator.toImage(v21ManifestTemplate);

        case 2:
          V22ManifestTemplate v22ManifestTemplate = (V22ManifestTemplate) manifestTemplate;

          ByteArrayOutputStream containerConfigurationOutputStream = new ByteArrayOutputStream();
          registryClient.pullBlob(
              v22ManifestTemplate.getContainerConfigurationDigest(),
              containerConfigurationOutputStream);
          String containerConfigurationString =
              new String(containerConfigurationOutputStream.toByteArray(), StandardCharsets.UTF_8);

          ContainerConfigurationTemplate containerConfigurationTemplate =
              JsonTemplateMapper.readJson(
                  containerConfigurationString, ContainerConfigurationTemplate.class);
          return JsonToImageTranslator.toImage(v22ManifestTemplate, containerConfigurationTemplate);
      }

      throw new IllegalStateException("Unknown manifest schema version");
    }
  }

  <U extends AsyncStep<?>> U getDependency(Class<U> dependencyClass) {
    if (!dependencyClass.isInstance(dependencyMap.get(dependencyClass))) {
      throw new IllegalStateException("Dependency class mismatch");
    }
    return (U) dependencyMap.get(dependencyClass);
  }

  <U extends AsyncStep<?>> void setDependency(Class<U> dependencyClass, U dependency) {
    // Checks if the dependency is of the right class.
    if (!dependencies.contains(dependencyClass)) {
      throw new IllegalArgumentException("Not a valid dependency for " + getClass());
    }

    dependencyMap.put(dependencyClass, dependency);
  }

  @Override
  /** Submit the step to the {@code listeningExecutorService} to run when ready. */
  void submitTo(ListeningExecutorService listeningExecutorService) {
    // Checks if all dependencies are set.
    for (Class<? extends AsyncStep<?>> dependency : dependencies) {
      if (!dependencyMap.containsKey(dependency)) {
        throw new IllegalStateException("Dependency " + dependency + " not set for " + getClass());
      }
    }

    future =
        Futures.whenAllSucceed(getDependency(AuthenticatePullStep.class).getFuture())
            .call(this, listeningExecutorService);
  }
}
