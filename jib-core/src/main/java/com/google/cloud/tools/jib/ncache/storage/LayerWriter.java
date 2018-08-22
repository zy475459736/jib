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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.ncache.CacheEntry;
import com.google.common.base.Preconditions;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;

class LayerWriter implements TemporaryFileWriter.Writer<CacheEntry.Layer> {

  @FunctionalInterface
  interface FilenameResolver {

    Path getFilename(DescriptorDigest compressedDigest, DescriptorDigest diffId);
  }

  private final Blob layerBlob;
  private final FilenameResolver filenameResolver;
  @Nullable private final DescriptorDigest selectorDigest;
  @Nullable private CacheEntry.Layer layer;

  LayerWriter(
      Blob layerBlob,
      FilenameResolver filenameResolver,
      @Nullable DescriptorDigest selectorDigest) {
    this.layerBlob = layerBlob;
    this.filenameResolver = filenameResolver;
    this.selectorDigest = selectorDigest;
  }

  @Override
  public Path writeTo(Path temporaryFile) throws IOException {
    // Writes the UnwrittenLayer layer BLOB to a file to convert into a CachedLayer.
    try (CountingDigestOutputStream compressedDigestOutputStream =
        new CountingDigestOutputStream(
            new BufferedOutputStream(Files.newOutputStream(temporaryFile)))) {
      // Writes the layer with GZIP compression. The original bytes are captured as the layer's
      // diff ID and the bytes outputted from the GZIP compression are captured as the layer's
      // content descriptor.
      GZIPOutputStream compressorStream = new GZIPOutputStream(compressedDigestOutputStream);
      DescriptorDigest diffId = layerBlob.writeTo(compressorStream).getDigest();

      // The GZIPOutputStream must be closed in order to write out the remaining compressed data.
      compressorStream.close();
      BlobDescriptor compressedBlobDescriptor = compressedDigestOutputStream.toBlobDescriptor();
      DescriptorDigest layerDigest = compressedBlobDescriptor.getDigest();

      // Renames the temporary layer file to the correct filename.
      Path layerFile = filenameResolver.getFilename(compressedBlobDescriptor.getDigest(), diffId);
      Files.createDirectories(layerFile.getParent());

      layer =
          DefaultCacheEntry.newLayer(
              layerDigest,
              diffId,
              compressedBlobDescriptor.getSize(),
              Blobs.from(layerFile),
              selectorDigest);

      return layerFile;
    }
  }

  @Override
  public CacheEntry.Layer getResult() {
    return Preconditions.checkNotNull(layer);
  }
}
