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

import com.google.api.client.util.Base64;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.RetrieveRegistryCredentialsStep;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;

@Mojo(name = "targetImageCredentials")
public class TargetImageCredentialsMojo extends AbstractMojo {

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Parameter private String registry;

  @Parameter private List<String> credHelpers;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (registry == null) {
      registry = ImageReference.getDefaultRegistry();
    }

    // Checks Maven settings for registry credentials.
    Map<String, Authorization> registryCredentials = new HashMap<>(2);
    // Retrieves credentials for the target registry.
    Authorization targetRegistryCredentials = getRegistryCredentialsFromSettings(registry);
    if (targetRegistryCredentials != null) {
      registryCredentials.put(registry, targetRegistryCredentials);
    }
    RegistryCredentials mavenSettingsCredentials =
        RegistryCredentials.from("Maven settings", registryCredentials);

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new MavenBuildLogger(getLog()))
            .setBaseImage(ImageReference.of(null, "ignored", null))
            .setTargetImage(ImageReference.of(registry, "ignored", null))
            .setCredentialHelperNames(credHelpers)
            .setKnownRegistryCredentials(mavenSettingsCredentials)
            .setMainClass("ignored")
            .build();

    RetrieveRegistryCredentialsStep retrieveRegistryCredentialsStep =
        new RetrieveRegistryCredentialsStep(buildConfiguration, registry);
    try {
      Authorization authorization = retrieveRegistryCredentialsStep.call();
      if (authorization == null) {
        throw new MojoExecutionException("Could not retrieve credentials for " + registry);
      }

      if (!"Basic".equals(authorization.getScheme())) {
        throw new MojoExecutionException("Credentials not retrieve correctly");
      }

      System.out.println("STARTAUTH");

      // Split a token into username and secret using the 'username:secret' format. Uses first colon
      // to split.
      String token =
          new String(Base64.decodeBase64(authorization.getToken()), StandardCharsets.UTF_8);
      int colonIndex = token.indexOf(':');
      String username = token.substring(0, colonIndex);
      String secret = token.substring(colonIndex + 1);

      System.out.println(username);
      System.out.println(secret);

      Server server = new Server();
      server.setId("_jib_credentials");
      server.setUsername(username);
      server.setPassword(secret);
      session.getSettings().addServer(server);

    } catch (IOException | NonexistentDockerCredentialHelperException ex) {
      throw new MojoExecutionException("Retrieve credentials failed", ex);
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
