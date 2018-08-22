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
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Immutable
 *
 * <p>stores layer blobs by digest can select layer blobs by arbitrarily-defined selector digest
 */
public interface CacheStorage {

  CacheEntry save(
      Blob layerBlob, @Nullable DescriptorDigest selectorDigest, @Nullable Blob metadataBlob)
      throws IOException;

  List<DescriptorDigest> listDigests() throws IOException;

  Optional<CacheEntry> retrieve(DescriptorDigest layerDigest) throws IOException;

  List<DescriptorDigest> listDigestsBySelector(DescriptorDigest layerSelectorDigest)
      throws IOException;
}
