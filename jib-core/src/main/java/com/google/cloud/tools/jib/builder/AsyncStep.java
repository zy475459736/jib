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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

abstract class AsyncStep<T> implements Callable<T> {

  interface DependencyEnum {

    Class<?> getDependencyClass();
  }

  static class Factory {

    private final ListeningExecutorService listeningExecutorService;
    private final BuildConfiguration buildConfiguration;

    Factory(
        ListeningExecutorService listeningExecutorService, BuildConfiguration buildConfiguration) {
      this.listeningExecutorService = listeningExecutorService;
      this.buildConfiguration = buildConfiguration;
    }

    <U extends AsyncStep<?>> U make(Class<U> asyncStepClass, Object... dependencies) {
      try {
        Constructor<U> constructor =
            asyncStepClass.getDeclaredConstructor(BuildConfiguration.class);
        U asyncStep = constructor.newInstance(buildConfiguration);

        // Adds all the dependencies.
        DependencyEnum currentEnum = null;
        for (Object dependency : dependencies) {
          if (currentEnum == null) {
            if (!(dependency instanceof DependencyEnum)) {
              throw new IllegalArgumentException(
                  "Dependencies should alternate between Enum and AsyncStep");
            }
            currentEnum = (DependencyEnum) dependency;

          } else {
            asyncStep.setDependency(currentEnum, (AsyncStep<?>) dependency);
          }
        }

        // Submits to the executor.
        asyncStep.submitTo(listeningExecutorService);

        return asyncStep;

      } catch (NoSuchMethodException
          | InstantiationException
          | IllegalAccessException
          | InvocationTargetException ex) {
        throw new IllegalStateException("Reflection calls should not fail", ex);
      }
    }
  }

  Map<DependencyEnum, AsyncStep<?>> dependencyMap = new HashMap<>();
  ListenableFuture<T> future;

  <U extends AsyncStep<?>> void setDependency(DependencyEnum dependencyEnum, U dependency) {
    Class<?> dependencyClass = dependencyEnum.getDependencyClass();
    // Checks if the dependency is of the right class.
    if (dependency.getClass() != dependencyClass) {
      throw new IllegalArgumentException("Dependency class mismatch");
    }

    dependencyMap.put(dependencyEnum, dependency);
  }

  /** Must be called after {@link #submitTo}. */
  ListenableFuture<T> getFuture() {
    if (future == null) {
      throw new IllegalStateException("Cannot get future before submitting to an executor");
    }
    return future;
  }

  /** Submit the step to the {@code listeningExecutorService} to run when ready. */
  void submitTo(ListeningExecutorService listeningExecutorService) {
    future = listeningExecutorService.submit(this);
  }
}
