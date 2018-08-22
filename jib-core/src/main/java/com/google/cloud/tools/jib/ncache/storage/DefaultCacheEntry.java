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
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.ncache.CacheEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.Optional;
import javax.annotation.Nullable;

class DefaultCacheEntry implements CacheEntry {

  static class DefaultLayer implements CacheEntry.Layer {

    private final DescriptorDigest layerDigest;
    private final DescriptorDigest diffId;
    private final Blob blob;
    private final long size;
    @Nullable private final DescriptorDigest selectorDigest;

    private DefaultLayer(
        DescriptorDigest layerDigest,
        DescriptorDigest diffId,
        long size,
        Blob blob,
        @Nullable DescriptorDigest selectorDigest) {
      this.layerDigest = layerDigest;
      this.diffId = diffId;
      this.size = size;
      this.blob = blob;
      this.selectorDigest = selectorDigest;
    }

    @Override
    public DescriptorDigest getDigest() {
      return layerDigest;
    }

    @Override
    public DescriptorDigest getDiffId() {
      return diffId;
    }

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public Blob getBlob() {
      return blob;
    }

    @Override
    public Optional<DescriptorDigest> getSelector() {
      return Optional.ofNullable(selectorDigest);
    }
  }

  static class DefaultMetadata implements CacheEntry.Metadata {

    private final Blob blob;

    private DefaultMetadata(Blob blob) {
      this.blob = blob;
    }

    @Override
    public Blob getBlob() {
      return blob;
    }
  }

  static Layer newLayer(DescriptorDigest layerDigest, Path layerFile) throws IOException {
    return new DefaultLayer(
        layerDigest,
        asDescriptorDigest(
            layerFile.getFileName().toString().substring(0, DescriptorDigest.HASH_REGEX.length())),
        Files.size(layerFile),
        Blobs.from(layerFile),
        null);
  }

  static Layer newLayer(
      DescriptorDigest layerDigest,
      DescriptorDigest diffId,
      long size,
      Blob layerBlob,
      @Nullable DescriptorDigest selectorDigest) {
    return new DefaultLayer(layerDigest, diffId, size, layerBlob, selectorDigest);
  }

  static Metadata newMetadata(Path metadataFile) {
    return new DefaultMetadata(Blobs.from(metadataFile));
  }

  static CacheEntry newCacheEntry(CacheEntry.Layer layer, @Nullable CacheEntry.Metadata metadata) {
    return new DefaultCacheEntry(layer, metadata);
  }

  private static DescriptorDigest asDescriptorDigest(String hash) {
    try {
      return DescriptorDigest.fromHash(hash);

    } catch (DigestException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private final CacheEntry.Layer layer;
  @Nullable private final CacheEntry.Metadata metadata;

  private DefaultCacheEntry(CacheEntry.Layer layer, @Nullable CacheEntry.Metadata metadata) {
    this.layer = layer;
    this.metadata = metadata;
  }

  @Override
  public Layer getLayer() {
    return layer;
  }

  @Override
  public Optional<Metadata> getMetadata() {
    return Optional.ofNullable(metadata);
  }
}
