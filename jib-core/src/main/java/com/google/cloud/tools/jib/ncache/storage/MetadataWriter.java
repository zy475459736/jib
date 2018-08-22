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
import com.google.common.base.Preconditions;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

public class MetadataWriter implements TemporaryFileWriter.Writer<DescriptorDigest> {

  @FunctionalInterface
  interface FilenameResolver {

    Path getFilename(DescriptorDigest metadataDigest);
  }

  private final Blob metadataBlob;
  private final FilenameResolver filenameResolver;
  @Nullable private DescriptorDigest metadataDigest;

  MetadataWriter(Blob metadataBlob, FilenameResolver filenameResolver) {
    this.metadataBlob = metadataBlob;
    this.filenameResolver = filenameResolver;
  }

  @Override
  public Path writeTo(Path temporaryFile) throws IOException {
    try (OutputStream tempLayerFileOutputStrema =
        new BufferedOutputStream(Files.newOutputStream(temporaryFile))) {
      metadataDigest = metadataBlob.writeTo(tempLayerFileOutputStrema).getDigest();
      return filenameResolver.getFilename(metadataDigest);
    }
  }

  @Override
  public DescriptorDigest getResult() {
    return Preconditions.checkNotNull(metadataDigest);
  }
}
