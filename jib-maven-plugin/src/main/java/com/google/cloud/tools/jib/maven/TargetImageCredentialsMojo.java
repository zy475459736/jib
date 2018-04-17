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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.RetrieveRegistryCredentialsStep;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.credentials.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mojo(name="targetImageCredentials")
public class TargetImageCredentialsMojo extends AbstractMojo {

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Parameter(defaultValue = "gcr.io/distroless/java", required = true)
  private String from;

  @Parameter private String registry;

  @Parameter private List<String> credHelpers;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // Parses 'from' into image reference.
    ImageReference baseImage = getBaseImageReference();

    // Checks Maven settings for registry credentials.
    session.getSettings().getServer(baseImage.getRegistry());
    Map<String, Authorization> registryCredentials = new HashMap<>(2);
    // Retrieves credentials for the base image registry.
    Authorization baseImageRegistryCredentials =
        getRegistryCredentialsFromSettings(baseImage.getRegistry());
    if (baseImageRegistryCredentials != null) {
      registryCredentials.put(baseImage.getRegistry(), baseImageRegistryCredentials);
    }
    // Retrieves credentials for the target registry.
    Authorization targetRegistryCredentials = getRegistryCredentialsFromSettings(registry);
    if (targetRegistryCredentials != null) {
      registryCredentials.put(registry, targetRegistryCredentials);
    }
    RegistryCredentials mavenSettingsCredentials =
        RegistryCredentials.from("Maven settings", registryCredentials);

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new MavenBuildLogger(getLog()))
            .setBaseImage(baseImage)
            .setTargetImage(ImageReference.of(registry, "ignored", null))
            .setCredentialHelperNames(credHelpers)
            .setKnownRegistryCredentials(mavenSettingsCredentials)
            .setMainClass("ignored")
            .build();

    RetrieveRegistryCredentialsStep retrieveRegistryCredentialsStep = new RetrieveRegistryCredentialsStep(buildConfiguration, registry);
    try {
      Authorization authorization = retrieveRegistryCredentialsStep.call();
      System.out.println("STARTAUTH");
      System.out.println(authorization);

    } catch (IOException | NonexistentDockerCredentialHelperException ex) {
      throw new MojoExecutionException("Retrieve credentials failed", ex);
    }
  }

  /** @return the {@link ImageReference} parsed from {@link #from}. */
  private ImageReference getBaseImageReference() throws MojoFailureException {
    try {
      return ImageReference.parse(from);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoFailureException("Parameter 'from' is invalid", ex);
    }
  }

  /** Attempts to retrieve credentials for {@code registry} from Maven settings. */
  @Nullable
  private Authorization getRegistryCredentialsFromSettings(String registry) {
    Server registryServerSettings = session.getSettings().getServer(registry);
    if (registryServerSettings == null) {
      return null;
    }
    return Authorizations.withBasicCredentials(
        registryServerSettings.getUsername(), registryServerSettings.getPassword());
  }
}
