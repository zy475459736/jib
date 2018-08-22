/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.ncache.Cache;
import com.google.cloud.tools.jib.ncache.CacheReadEntry;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep
    implements AsyncStep<CacheReadEntry>, Callable<CacheReadEntry> {

  private static final String DESCRIPTION = "Pulling base image layer %s";

  private final BuildConfiguration buildConfiguration;
  private final Cache cache;
  private final DescriptorDigest layerDigest;
  private final @Nullable Authorization pullAuthorization;

  private final ListenableFuture<CacheReadEntry> listenableFuture;

  PullAndCacheBaseImageLayerStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      Cache cache,
      DescriptorDigest layerDigest,
      @Nullable Authorization pullAuthorization) {
    this.buildConfiguration = buildConfiguration;
    this.cache = cache;
    this.layerDigest = layerDigest;
    this.pullAuthorization = pullAuthorization;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<CacheReadEntry> getFuture() {
    return listenableFuture;
  }

  @Override
  public CacheReadEntry call() throws IOException, RegistryException {
    try (Timer ignored =
        new Timer(buildConfiguration.getBuildLogger(), String.format(DESCRIPTION, layerDigest))) {
      RegistryClient registryClient =
          RegistryClient.factory(
                  buildConfiguration.getBuildLogger(),
                  buildConfiguration.getBaseImageConfiguration().getImageRegistry(),
                  buildConfiguration.getBaseImageConfiguration().getImageRepository())
              .setAllowInsecureRegistries(buildConfiguration.getAllowInsecureRegistries())
              .setAuthorization(pullAuthorization)
              .newRegistryClient();

      // Checks if the layer already exists in the cache.
      Optional<CacheReadEntry> optionalCacheReadEntry = cache.getLayer(layerDigest);
      if (optionalCacheReadEntry.isPresent()) {
        return optionalCacheReadEntry.get();
      }

      // TODO: Make this exception handling more readable.
      try {
        return cache.saveBaseImageLayer(
            Blobs.from(
                outputStream -> {
                  try {
                    registryClient.pullBlob(layerDigest, outputStream);

                  } catch (RegistryException ex) {
                    throw new IOException(ex);
                  }
                }));
      } catch (IOException ex) {
        if (ex.getCause() instanceof RegistryException) {
          throw (RegistryException) (ex.getCause());
        }
        throw ex;
      }
    }
  }
}
