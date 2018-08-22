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

import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.builder.TestJibLogger;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.ncache.Cache;
import com.google.cloud.tools.jib.ncache.CacheReadEntry;
import com.google.cloud.tools.jib.ncache.storage.DefaultCacheStorage;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildAndCacheApplicationLayerStep}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildAndCacheApplicationLayerStepTest {

  // TODO: Consolidate with BuildStepsIntegrationTest.
  private static final String EXTRACTION_PATH = "/some/extraction/path/";

  private static final String EXTRA_FILES_LAYER_EXTRACTION_PATH = "/extra";

  /** Lists the files in the {@code resourcePath} resources directory. */
  private static ImmutableList<Path> getFilesList(String resourcePath)
      throws URISyntaxException, IOException {
    try (Stream<Path> fileStream =
        Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
      return fileStream.collect(ImmutableList.toImmutableList());
    }
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildConfiguration mockBuildConfiguration;
  private Path temporaryCacheDirectory;

  private LayerConfiguration fakeDependenciesLayerConfiguration;
  private LayerConfiguration fakeSnapshotDependenciesLayerConfiguration;
  private LayerConfiguration fakeResourcesLayerConfiguration;
  private LayerConfiguration fakeClassesLayerConfiguration;
  private LayerConfiguration fakeExtraFilesLayerConfiguration;
  private LayerConfiguration emptyLayerConfiguration;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    fakeDependenciesLayerConfiguration =
        LayerConfiguration.builder()
            .addEntry(getFilesList("application/dependencies"), EXTRACTION_PATH + "libs/")
            .build();
    fakeSnapshotDependenciesLayerConfiguration =
        LayerConfiguration.builder()
            .addEntry(getFilesList("application/snapshot-dependencies"), EXTRACTION_PATH + "libs/")
            .build();
    fakeResourcesLayerConfiguration =
        LayerConfiguration.builder()
            .addEntry(getFilesList("application/resources"), EXTRACTION_PATH + "resources/")
            .build();
    fakeClassesLayerConfiguration =
        LayerConfiguration.builder()
            .addEntry(getFilesList("application/classes"), EXTRACTION_PATH + "classes/")
            .build();
    fakeExtraFilesLayerConfiguration =
        LayerConfiguration.builder()
            .addEntry(
                ImmutableList.of(
                    Paths.get(Resources.getResource("fileA").toURI()),
                    Paths.get(Resources.getResource("fileB").toURI())),
                EXTRA_FILES_LAYER_EXTRACTION_PATH)
            .build();
    emptyLayerConfiguration = LayerConfiguration.builder().build();
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(new TestJibLogger());
    temporaryCacheDirectory = temporaryFolder.newFolder().toPath();
  }

  private List<CacheReadEntry> buildFakeLayersToCache() throws ExecutionException {
    List<CacheReadEntry> applicationCacheEntries = new ArrayList<>();

    Cache cache = Cache.init(new DefaultCacheStorage(temporaryCacheDirectory));
    ImmutableList<BuildAndCacheApplicationLayerStep> buildAndCacheApplicationLayerSteps =
        BuildAndCacheApplicationLayerStep.makeList(
            MoreExecutors.newDirectExecutorService(), mockBuildConfiguration, cache);

    for (BuildAndCacheApplicationLayerStep buildAndCacheApplicationLayerStep :
        buildAndCacheApplicationLayerSteps) {
      applicationCacheEntries.add(NonBlockingSteps.get(buildAndCacheApplicationLayerStep));
    }

    return applicationCacheEntries;
  }

  @Test
  public void testRun() throws LayerPropertyNotFoundException, IOException, ExecutionException {
    ImmutableList<LayerConfiguration> fakeLayerConfigurations =
        ImmutableList.of(
            fakeDependenciesLayerConfiguration,
            fakeSnapshotDependenciesLayerConfiguration,
            fakeResourcesLayerConfiguration,
            fakeClassesLayerConfiguration,
            fakeExtraFilesLayerConfiguration);
    Mockito.when(mockBuildConfiguration.getLayerConfigurations())
        .thenReturn(fakeLayerConfigurations);

    // Populates the cache.
    List<CacheReadEntry> applicationCacheEntries = buildFakeLayersToCache();
    Assert.assertEquals(5, applicationCacheEntries.size());

    // Re-initialize cache with the updated metadata.
    Cache cache = Cache.init(new DefaultCacheStorage(temporaryCacheDirectory));

    ImmutableList<LayerEntry> dependenciesLayerEntries =
        fakeLayerConfigurations.get(0).getLayerEntries();
    ImmutableList<LayerEntry> snapshotDependenciesLayerEntries =
        fakeLayerConfigurations.get(1).getLayerEntries();
    ImmutableList<LayerEntry> resourcesLayerEntries =
        fakeLayerConfigurations.get(2).getLayerEntries();
    ImmutableList<LayerEntry> classesLayerEntries =
        fakeLayerConfigurations.get(3).getLayerEntries();
    ImmutableList<LayerEntry> extraFilesLayerEntries =
        fakeLayerConfigurations.get(4).getLayerEntries();

    // Verifies that the cached layers are up-to-date.
    cache
        .getUpToDateLayerByLayerEntries(dependenciesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(0).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
    cache
        .getUpToDateLayerByLayerEntries(snapshotDependenciesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(1).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
    cache
        .getUpToDateLayerByLayerEntries(resourcesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(2).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
    cache
        .getUpToDateLayerByLayerEntries(classesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(3).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
    cache
        .getUpToDateLayerByLayerEntries(extraFilesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(4).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
  }

  @Test
  public void testRun_emptyLayersIgnored() throws IOException, ExecutionException {
    ImmutableList<LayerConfiguration> fakeLayerConfigurations =
        ImmutableList.of(
            fakeDependenciesLayerConfiguration,
            emptyLayerConfiguration,
            fakeResourcesLayerConfiguration,
            fakeClassesLayerConfiguration,
            emptyLayerConfiguration);
    Mockito.when(mockBuildConfiguration.getLayerConfigurations())
        .thenReturn(fakeLayerConfigurations);

    // Populates the cache.
    List<CacheReadEntry> applicationCacheEntries = buildFakeLayersToCache();
    Assert.assertEquals(3, applicationCacheEntries.size());

    ImmutableList<LayerEntry> dependenciesLayerEntries =
        fakeLayerConfigurations.get(0).getLayerEntries();
    ImmutableList<LayerEntry> resourcesLayerEntries =
        fakeLayerConfigurations.get(2).getLayerEntries();
    ImmutableList<LayerEntry> classesLayerEntries =
        fakeLayerConfigurations.get(3).getLayerEntries();

    // Re-initialize cache with the updated metadata.
    Cache cache = Cache.init(new DefaultCacheStorage(temporaryCacheDirectory));

    // Verifies that the cached layers are up-to-date.
    cache
        .getUpToDateLayerByLayerEntries(dependenciesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(0).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
    cache
        .getUpToDateLayerByLayerEntries(resourcesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(1).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
    cache
        .getUpToDateLayerByLayerEntries(classesLayerEntries)
        .ifPresent(
            cacheReadEntry ->
                Assert.assertEquals(
                    applicationCacheEntries.get(2).getLayerDigest(),
                    cacheReadEntry.getLayerDigest()));
  }
}
