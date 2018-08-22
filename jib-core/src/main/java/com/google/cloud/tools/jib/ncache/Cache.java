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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.ncache.json.MetadataTemplate;
import com.google.cloud.tools.jib.ncache.json.MetadataTranslator;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Provides useful actions performed on a cache. The cache is backed by a {@link CacheStorage}. */
public class Cache {

  public static Cache init(CacheStorage cacheStorage) {
    return new Cache(cacheStorage);
  }

  public static DescriptorDigest getSelectorDigest(List<LayerEntry> layerEntries)
      throws IOException {
    return JsonTemplateMapper.toBlob(
            () ->
                layerEntries
                    .stream()
                    .map(MetadataTranslator::toTemplate)
                    .collect(Collectors.toList()))
        .writeTo(ByteStreams.nullOutputStream())
        .getDigest();
  }

  /**
   * @param path the file to check.
   * @return the last modified time for the file at {@code path}. Recursively finds the most recent
   *     last modified time for all subfiles if the file is a directory.
   * @throws IOException if checking the last modified time fails.
   */
  private static FileTime getLastModifiedTime(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      List<Path> subFiles = new DirectoryWalker(path).walk();
      FileTime maxLastModifiedTime = FileTime.from(Instant.MIN);

      // Finds the max last modified time for the subfiles.
      for (Path subFilePath : subFiles) {
        FileTime subFileLastModifiedTime = Files.getLastModifiedTime(subFilePath);
        if (subFileLastModifiedTime.compareTo(maxLastModifiedTime) > 0) {
          maxLastModifiedTime = subFileLastModifiedTime;
        }
      }

      return maxLastModifiedTime;
    }

    return Files.getLastModifiedTime(path);
  }

  private static FileTime getLatestModificationTime(List<LayerEntry> layerEntries)
      throws IOException {
    FileTime sourceFilesLastModifiedTime = FileTime.from(Instant.MIN);
    for (LayerEntry layerEntry : layerEntries) {
      for (Path path : layerEntry.getSourceFiles()) {
        FileTime lastModifiedTime = getLastModifiedTime(path);
        if (lastModifiedTime.compareTo(sourceFilesLastModifiedTime) > 0) {
          sourceFilesLastModifiedTime = lastModifiedTime;
        }
      }
    }
    return sourceFilesLastModifiedTime;
  }

  private final CacheStorage cacheStorage;

  private Cache(CacheStorage cacheStorage) {
    this.cacheStorage = cacheStorage;
  }

  /**
   * @param layerDigest the layer digest of the layer to get.
   * @return the cached layer with digest {@code layerDigest}, or {@code null} if not found.
   * @throws IOException if an I/O exception occurs
   */
  public Optional<CacheEntry> getLayer(DescriptorDigest layerDigest) throws IOException {
    return cacheStorage.retrieve(layerDigest);
  }

  public CacheEntry saveBaseImageLayer(Blob layerBlob) throws IOException {
    return cacheStorage.save(layerBlob, null, null);
  }

  public CacheEntry saveApplicationLayer(
      Blob layerBlob, DescriptorDigest layerSelectorDigest, Blob metadataBlob) throws IOException {
    return cacheStorage.save(layerBlob, layerSelectorDigest, metadataBlob);
  }

  /**
   * Gets an up-to-date layer that is built from the {@code sourceFiles}.
   *
   * <p>The method returns the first up-to-date layer found. This is safe because the source files
   * will not have been modified since creation of any up-to-date layer (ie. all up-to-date layers
   * should have the same file contents).
   *
   * @param layerEntries the layer's content entries
   * @return an up-to-date layer containing the source files.
   * @throws IOException if reading the source files fails.
   */
  public Optional<CacheEntry> getUpToDateLayerByLayerEntries(ImmutableList<LayerEntry> layerEntries)
      throws IOException {
    // Serializes layerEntries as a JSON blob - the digests of which become the selector digest.
    DescriptorDigest metadataSelectorDigest = getSelectorDigest(layerEntries);

    // Grabs all the layers that have matching source files.
    List<DescriptorDigest> matchingLayerDigests =
        cacheStorage.listDigestsBySelector(metadataSelectorDigest);
    if (matchingLayerDigests.isEmpty()) {
      return Optional.empty();
    }

    // Grabs all the layer cache entries.
    List<CacheEntry> cacheEntries = new ArrayList<>(matchingLayerDigests.size());
    for (DescriptorDigest layerDigest : matchingLayerDigests) {
      cacheStorage.retrieve(layerDigest).ifPresent(cacheEntries::add);
    }

    // Determines the latest modification time for the source files.
    FileTime sourceFilesLastModifiedTime = getLatestModificationTime(layerEntries);

    // Checks if at least one of the matched layers is up-to-date.
    for (CacheEntry cacheEntry : cacheEntries) {
      Optional<CacheEntry.Metadata> optionalMetadata = cacheEntry.getMetadata();
      if (!optionalMetadata.isPresent()) {
        continue;
      }

      MetadataTemplate metadataTemplate =
          JsonTemplateMapper.readJson(
              Blobs.writeToString(optionalMetadata.get().getBlob()), MetadataTemplate.class);

      if (sourceFilesLastModifiedTime.compareTo(metadataTemplate.getLastModifiedTime()) <= 0) {
        // This layer is an up-to-date layer.
        return Optional.of(cacheEntry);
      }
    }

    return Optional.empty();
  }
}
