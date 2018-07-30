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

package com.google.cloud.tools.jib.async;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class AsyncStepFuture<V> {

  /** Builds the {@link AsyncStepFuture} with a submitted {@link ListenableFuture}. */
  public static class Builder<V> {

    private final ListeningExecutorService listeningExecutorService;
    private final Callable<V> callable;

    private final List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>>
        wrappedDependencyLists = new ArrayList<>();
    private final List<AsyncStep<?>> dependencyList = new ArrayList<>();

    public Builder<V> addWrappedDependency(
        AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>> wrappedDependency) {
      wrappedDependencyLists.add(wrappedDependency);
      return this;
    }

    public Builder<V> addWrappedDependencies(
        List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>> wrappedDependencies) {
      wrappedDependencyLists.addAll(wrappedDependencies);
      return this;
    }

    public Builder<V> addDependency(AsyncStep<?> dependency) {
      dependencyList.add(dependency);
      return this;
    }

    public Builder<V> addDependencies(List<? extends AsyncStep<?>> dependencies) {
      dependencyList.addAll(dependencies);
      return this;
    }

    /**
     * Submits the {@link Callable} to run after the dependencies and initializes the {@link
     * AsyncStepFuture} with the submitted {@link ListenableFuture}. Note that this does not mean
     * that the submitted {@link Callable} runs after the wrapped dependencies. if using wrapped
     * dependencies, use the {@link #runAfterWrappedDependencies} to submit a second callable that
     * runs only after the wrapped dependencies have finished.
     *
     * @return the built {@link AsyncStepFuture}
     */
    public AsyncStepFuture<V> build() {
      List<ListenableFuture<?>> dependenciesFutures = new ArrayList<>(dependencyList.size());
      for (AsyncStep<?> dependency : dependencyList) {
        dependenciesFutures.add(dependency.getFuture());
      }
      ListenableFuture<V> listenableFuture =
          Futures.whenAllSucceed(dependenciesFutures).call(callable, listeningExecutorService);

      return new AsyncStepFuture<>(
          listeningExecutorService, listenableFuture, wrappedDependencyLists);
    }

    private Builder(ListeningExecutorService listeningExecutorService, Callable<V> callable) {
      this.listeningExecutorService = listeningExecutorService;
      this.callable = callable;
    }
  }

  public static <V> Builder<V> builder(
      ListeningExecutorService listeningExecutorService, Callable<V> callable) {
    return new Builder<>(listeningExecutorService, callable);
  }

  private final ListeningExecutorService listeningExecutorService;
  private final ListenableFuture<V> listenableFuture;
  private final List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>>
      wrappedDependencyLists;

  private AsyncStepFuture(
      ListeningExecutorService listeningExecutorService,
      ListenableFuture<V> listenableFuture,
      List<AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>>> wrappedDependencyLists) {
    this.listeningExecutorService = listeningExecutorService;
    this.listenableFuture = listenableFuture;
    this.wrappedDependencyLists = wrappedDependencyLists;
  }

  /**
   * Submits the {@code callable} to run after the wrapped dependencies in {@link
   * #wrappedDependencyLists}. This method must be called in the original {@link Callable} given to
   * {@link #builder} to call this second {@code callable} only after the wrapped dependencies have
   * finished.
   *
   * @param callable the {@code callable} to call after the wrapped dependencies have finished
   * @param <T> the return type of the {@code callable}
   * @return the submitted {@link ListenableFuture}
   * @throws ExecutionException if an exception occurred during execution
   */
  public <T> ListenableFuture<T> runAfterWrappedDependencies(Callable<T> callable)
      throws ExecutionException {
    // Unwrap the wrapped dependencies.
    List<ListenableFuture<?>> unwrappedDependencies = new ArrayList<>();
    for (AsyncStep<? extends ImmutableList<? extends AsyncStep<?>>> wrappedDependency :
        wrappedDependencyLists) {
      for (AsyncStep<?> unwrappedDependency : NonBlockingSteps.get(wrappedDependency)) {
        unwrappedDependencies.add(unwrappedDependency.getFuture());
      }
    }

    return Futures.whenAllSucceed(unwrappedDependencies).call(callable, listeningExecutorService);
  }

  public ListenableFuture<V> getFuture() {
    return listenableFuture;
  }
}
