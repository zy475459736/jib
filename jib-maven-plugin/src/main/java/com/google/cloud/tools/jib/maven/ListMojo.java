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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "list", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class ListMojo extends JibPluginConfiguration {

  private static void printFiles(ImmutableList<Path> files) {
    files.forEach(
        file -> {
          if (Files.isDirectory(file)) {
            try {
              new DirectoryWalker(file)
                  .walk(
                      path -> {
                        System.out.println(path);
                      });
            } catch (IOException ex) {
              ex.printStackTrace();
            }
          } else {
            System.out.println(file);
          }
        });
  }

  @Override
  public void execute() throws MojoExecutionException {
    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());

    // Parses 'from' and 'to' into image reference.
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger);

    System.out.println("JIB FILES LIST START");
    printFiles(mavenProjectProperties.getSourceFilesConfiguration().getDependenciesFiles());
    printFiles(mavenProjectProperties.getSourceFilesConfiguration().getSnapshotDependenciesFiles());
    printFiles(mavenProjectProperties.getSourceFilesConfiguration().getResourcesFiles());
    printFiles(mavenProjectProperties.getSourceFilesConfiguration().getClassesFiles());
    System.out.println("JIB FILES LIST END");
  }
}
