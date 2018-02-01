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

import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class AsyncStep<T> implements Callable<T> {

  interface DependencyEnum {

    Class<?> getResultClass();
  }

  static <U extends AsyncStep<?>> U init(Class<U> asyncStepClass, Object... dependencies) throws IllegalAccessException, InstantiationException {
    U asyncStep = asyncStepClass.newInstance();

    DependencyEnum currentEnum = null;
    for (Object dependency : dependencies) {
      if (currentEnum == null) {
        if (!(dependency instanceof DependencyEnum)) {
          throw new IllegalArgumentException("Dependencies should alternate between Enum and AsyncStep");
        }
        currentEnum = (DependencyEnum)dependency;

      } else {
        asyncStep.setDependency(currentEnum, (AsyncStep<?>) dependency);
      }
    }

    return asyncStep;
  }

  private Map<DependencyEnum, AsyncStep<?>> dependencyMap = new HashMap<>();
  private NonBlockingListenableFuture<T> future;

  <U extends AsyncStep<?>> void setDependency(DependencyEnum dependencyEnum, U dependency) {
    dependencyMap.put(dependencyEnum, dependency);
  }

  /**
   * Must be called after {@link #submitTo}.
   */
  NonBlockingListenableFuture<T> getFuture() {
    if (future == null) {
      throw new IllegalStateException("Cannot get future before submitting to an executor");
    }
    return future;
  }

  /** Submit the step to the {@code listeningExecutorService} to run when ready. */
  void submitTo(ListeningExecutorService listeningExecutorService) {
    future = new NonBlockingListenableFuture<>(listeningExecutorService.submit(this));
  }
}
