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

package com.google.cloud.tools.jib.ncache.storage;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.ncache.CacheEntry;
import com.google.cloud.tools.jib.ncache.CacheStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Default cache storage engine.
 *
 * <p>Does not require reading metadata or diff ID files. Constant time retrieval by layer digest.
 * Constant time retrieval by metadata.
 *
 * <p>{@code .layer} - layer blob {@code .metadata} - metadata blob {@code .metadata.digest} -
 * metadata digest {@code .layer.digest} - layer digest {@code layers/} - stores layers {@code
 * metadata/} - stores metadata {@code selectors/} - stores metadata selectors
 */
public class DefaultCacheStorage implements CacheStorage {

  private static DescriptorDigest asDescriptorDigest(String hash) {
    try {
      return DescriptorDigest.fromHash(hash);

    } catch (DigestException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private final Path cacheDirectory;
  private final TemporaryFileWriter temporaryFileWriter;

  public DefaultCacheStorage(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
    temporaryFileWriter = new TemporaryFileWriter(cacheDirectory);
  }

  @Override
  public CacheEntry save(
      Blob layerBlob, @Nullable DescriptorDigest selectorDigest, @Nullable Blob metadataBlob)
      throws IOException {
    CacheEntry.Layer layer = saveLayerBlob(layerBlob, selectorDigest);

    CacheEntry.Metadata metadata = null;
    if (metadataBlob != null) {
      metadata = saveMetadata(layer.getDigest(), selectorDigest, metadataBlob);
    }

    return DefaultCacheEntry.newCacheEntry(layer, metadata);
  }

  @Override
  public List<DescriptorDigest> listDigests() throws IOException {
    try (Stream<String> layerDirectoriesInCache = getLayerDirectoriesInCache()) {
      return layerDirectoriesInCache
          .map(DefaultCacheStorage::asDescriptorDigest)
          .collect(Collectors.toList());
    }
  }

  @Override
  public Optional<CacheEntry> retrieve(DescriptorDigest layerDigest) throws IOException {
    Path layerDirectory = getLayerDirectory(layerDigest);

    CacheEntry.Layer layer = null;
    CacheEntry.Metadata metadata = null;

    try (Stream<Path> filesInLayerDirectory = Files.list(layerDirectory)) {
      for (Path path : filesInLayerDirectory.collect(Collectors.toList())) {
        String filename = path.getFileName().toString();
        if (filename.endsWith(".layer")) {
          layer = DefaultCacheEntry.newLayer(layerDigest, path);
        } else if (filename.endsWith(".metadata")) {
          DescriptorDigest metadataDigest =
              asDescriptorDigest(filename.substring(0, DescriptorDigest.HASH_REGEX.length()));
          metadata = DefaultCacheEntry.newMetadata(getMetadataFilename(metadataDigest));
        }
      }
    }

    if (layer == null || metadata == null) {
      // TODO: Throw metadata corruption exception.
      return Optional.empty();
    }

    return Optional.of(DefaultCacheEntry.newCacheEntry(layer, metadata));
  }

  @Override
  public List<DescriptorDigest> listDigestsBySelector(DescriptorDigest layerSelectorDigest)
      throws IOException {
    Path metadataSelectorDirectory = getSelectorDirectory(layerSelectorDigest);
    if (!Files.exists(metadataSelectorDirectory)) {
      return Collections.emptyList();
    }

    try (Stream<Path> layerDigestFiles = Files.list(metadataSelectorDirectory)) {
      return layerDigestFiles
          .map(
              path ->
                  asDescriptorDigest(
                      path.getFileName()
                          .toString()
                          .substring(0, DescriptorDigest.HASH_REGEX.length())))
          .collect(Collectors.toList());
    }
  }

  private Path getLayerDirectory(DescriptorDigest layerDigest) {
    return cacheDirectory.resolve("layers").resolve(layerDigest.getHash());
  }

  private Path getLayerFilename(DescriptorDigest compressedDigest, DescriptorDigest diffId) {
    return getLayerDirectory(compressedDigest).resolve(diffId.getHash() + ".layer");
  }

  private Path getLayerMetadataFilename(
      DescriptorDigest layerDigest, DescriptorDigest metadataDigest) {
    return getLayerDirectory(layerDigest).resolve(metadataDigest.getHash() + ".metadata.digest");
  }

  private Path getMetadataFilename(DescriptorDigest metadataDigest) {
    return cacheDirectory.resolve("metadata").resolve(metadataDigest.getHash() + ".metadata");
  }

  private Path getSelectorDirectory(DescriptorDigest metadataSelectorDigest) {
    return cacheDirectory.resolve("selectors").resolve(metadataSelectorDigest.getHash());
  }

  private Path getSelectorFilename(DescriptorDigest selectorDigest, DescriptorDigest layerDigest) {
    return getSelectorDirectory(selectorDigest).resolve(layerDigest.getHash() + ".layer.digest");
  }

  private Stream<String> getLayerDirectoriesInCache() throws IOException {
    try (Stream<Path> fileStream = Files.list(cacheDirectory)) {
      return fileStream
          .filter(path -> Files.isDirectory(path))
          .map(path -> path.getFileName().toString())
          .filter(directoryName -> directoryName.matches(DescriptorDigest.HASH_REGEX));
    }
  }

  private CacheEntry.Layer saveLayerBlob(Blob layerBlob, @Nullable DescriptorDigest selectorDigest)
      throws IOException {
    return temporaryFileWriter.write(
        new LayerWriter(layerBlob, this::getLayerFilename, selectorDigest));
  }

  private CacheEntry.Metadata saveMetadata(
      DescriptorDigest layerDigest, @Nullable DescriptorDigest selectorDigest, Blob metadataBlob)
      throws IOException {
    // Writes metadata blobs to metadata/.
    DescriptorDigest metadataDigest =
        temporaryFileWriter.write(new MetadataWriter(metadataBlob, this::getMetadataFilename));

    // Writes file to associate layer with its metadata.
    Files.createFile(getLayerMetadataFilename(layerDigest, metadataDigest));

    // Writes metadata selector file.
    if (selectorDigest != null) {
      Files.createFile(getSelectorFilename(selectorDigest, layerDigest));
    }

    return DefaultCacheEntry.newMetadata(getMetadataFilename(metadataDigest));
  }
}
