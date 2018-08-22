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
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.ncache.CacheEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Builds a model {@link Image}. */
class BuildImageStep
    implements AsyncStep<AsyncStep<Image<Layer>>>, Callable<AsyncStep<Image<Layer>>> {

  private static final String DESCRIPTION = "Building container configuration";

  private static Layer cacheEntryToLayer(CacheEntry cacheEntry) {
    return new Layer() {
      @Override
      public Blob getBlob() throws LayerPropertyNotFoundException {
        return cacheEntry.getLayer().getBlob();
      }

      @Override
      public BlobDescriptor getBlobDescriptor() throws LayerPropertyNotFoundException {
        return new BlobDescriptor(
            cacheEntry.getLayer().getSize(), cacheEntry.getLayer().getDigest());
      }

      @Override
      public DescriptorDigest getDiffId() throws LayerPropertyNotFoundException {
        return cacheEntry.getLayer().getDiffId();
      }
    };
  }

  private final BuildConfiguration buildConfiguration;
  private final PullBaseImageStep pullBaseImageStep;
  private final PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep;
  private final ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps;

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<AsyncStep<Image<Layer>>> listenableFuture;

  BuildImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      PullBaseImageStep pullBaseImageStep,
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep,
      ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps) {
    this.listeningExecutorService = listeningExecutorService;
    this.buildConfiguration = buildConfiguration;
    this.pullBaseImageStep = pullBaseImageStep;
    this.pullAndCacheBaseImageLayersStep = pullAndCacheBaseImageLayersStep;
    this.buildAndCacheApplicationLayerSteps = buildAndCacheApplicationLayerSteps;

    listenableFuture =
        Futures.whenAllSucceed(
                pullBaseImageStep.getFuture(), pullAndCacheBaseImageLayersStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<AsyncStep<Image<Layer>>> getFuture() {
    return listenableFuture;
  }

  @Override
  public AsyncStep<Image<Layer>> call() throws ExecutionException {
    List<ListenableFuture<?>> dependencies = new ArrayList<>();

    for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
        NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
      dependencies.add(pullAndCacheBaseImageLayerStep.getFuture());
    }
    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      dependencies.add(buildAndCacheApplicationLayerStep.getFuture());
    }
    ListenableFuture<Image<Layer>> future =
        Futures.whenAllSucceed(dependencies)
            .call(this::afterCachedLayersSteps, listeningExecutorService);
    return () -> future;
  }

  private Image<Layer> afterCachedLayersSteps()
      throws ExecutionException, LayerPropertyNotFoundException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // Constructs the image.
      Image.Builder<Layer> imageBuilder = Image.builder();
      for (PullAndCacheBaseImageLayerStep pullAndCacheBaseImageLayerStep :
          NonBlockingSteps.get(pullAndCacheBaseImageLayersStep)) {
        imageBuilder.addLayer(
            cacheEntryToLayer(NonBlockingSteps.get(pullAndCacheBaseImageLayerStep)));
      }
      for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
          buildAndCacheApplicationLayerSteps) {
        imageBuilder.addLayer(
            cacheEntryToLayer(NonBlockingSteps.get(buildAndCacheApplicationLayerStep)));
      }

      // Parameters that we passthrough from the base image
      Image<Layer> baseImage = NonBlockingSteps.get(pullBaseImageStep).getBaseImage();
      imageBuilder.addEnvironment(baseImage.getEnvironment());
      imageBuilder.addLabels(baseImage.getLabels());

      ContainerConfiguration containerConfiguration =
          buildConfiguration.getContainerConfiguration();
      if (containerConfiguration != null) {
        imageBuilder.addEnvironment(containerConfiguration.getEnvironmentMap());
        imageBuilder.setCreated(containerConfiguration.getCreationTime());
        imageBuilder.setEntrypoint(containerConfiguration.getEntrypoint());
        imageBuilder.setJavaArguments(containerConfiguration.getProgramArguments());
        imageBuilder.setExposedPorts(containerConfiguration.getExposedPorts());
        imageBuilder.addLabels(containerConfiguration.getLabels());
      }

      // Gets the container configuration content descriptor.
      return imageBuilder.build();
    }
  }
}
