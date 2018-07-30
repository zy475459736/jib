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

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.AsyncStepFuture;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Logs the message before finalizing an image build. */
class FinalizingStep implements AsyncStep<Void>, Callable<Void> {

  private final BuildConfiguration buildConfiguration;
  private final AsyncStepFuture<Void> asyncStepFuture;

  /**
   * @param listeningExecutorService the {@link ListeningExecutorService} to execute {@code
   *     callable} on
   * @param buildConfiguration the configuration for the execution this step is a part of
   * @param wrappedDependencyLists {@link AsyncStep}s that must be unwrapped for additional {@link
   *     AsyncStep}s to depend on
   * @param dependencyList list of additional {@link AsyncStep}s to depend on directly
   */
  FinalizingStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>> wrappedDependencyLists,
      List<? extends AsyncStep<?>> dependencyList) {
    this.asyncStepFuture =
        AsyncStepFuture.builder(listeningExecutorService, this)
            .addWrappedDependencies(wrappedDependencyLists)
            .addDependencies(dependencyList)
            .build();
    this.buildConfiguration = buildConfiguration;
  }

  // TODO: Replace with returning the AsyncStepFuture itself?
  @Override
  public ListenableFuture<Void> getFuture() {
    return asyncStepFuture.getFuture();
  }

  @Override
  public Void call() throws ExecutionException {
    asyncStepFuture.runAfterWrappedDependencies(
        () -> {
          buildConfiguration.getBuildLogger().lifecycle("Finalizing...");
          return null;
        });

    return null;
  }
}
