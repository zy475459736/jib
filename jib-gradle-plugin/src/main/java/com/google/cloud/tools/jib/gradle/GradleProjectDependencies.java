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

package com.google.cloud.tools.jib.gradle;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.SourceSet;

public class GradleProjectDependencies {

  Map<String, Set<Path>> getDependencies(Project project, SourceSet sourceSet) {
    Map<String, Set<Path>> dependencyMap = new HashMap<>();

    Consumer<ResolvedDependency> handleDep =
        resolvedDependency -> {
          Set<Path> artifactSet =
              resolvedDependency
                  .getModuleArtifacts()
                  .stream()
                  .map(resolvedArtifact -> resolvedArtifact.getFile().toPath())
                  .collect(Collectors.toSet());
          dependencyMap.put(resolvedDependency.getName(), artifactSet);
        };
    Consumer<ResolvedDependency> recursiveDependencyConsumer =
        resolvedDependency -> {
          handleDep.accept(resolvedDependency);
          resolvedDependency.getChildren().forEach(handleDep);
        };

    Configuration runtimeClasspathConfiguration =
        project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
    runtimeClasspathConfiguration
        .getResolvedConfiguration()
        .getFirstLevelModuleDependencies()
        .forEach(recursiveDependencyConsumer);

    return dependencyMap;
  }
}
