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

package com.google.cloud.tools.jib.ncache.json;

import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MetadataTranslator {

  public static LayerEntryTemplate toTemplate(LayerEntry layerEntry) {
    return new LayerEntryTemplate(
        toStrings(layerEntry.getSourceFiles()), layerEntry.getExtractionPath());
  }

  public static MetadataTemplate toTemplate(ImmutableList<LayerEntry> layerEntries) {
    return new MetadataTemplate()
        .setLayerEntries(
            layerEntries.stream().map(MetadataTranslator::toTemplate).collect(Collectors.toList()))
        .setLastModifiedTime(FileTime.from(Instant.now()));
  }

  private static List<String> toStrings(List<Path> paths) {
    List<String> strings = new ArrayList<>(paths.size());
    for (Path path : paths) {
      strings.add(path.toString());
    }
    return strings;
  }

  private MetadataTranslator() {}
}
