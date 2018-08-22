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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes to a temporary file first because the final filename can only be known after write. */
class TemporaryFileWriter {

  interface Writer<R> {

    /**
     * Writes to the temporary file and returns the destination file to move the temporary file to.
     *
     * @param temporaryFile the temporary file to write to
     * @return the destination file to move the temporary file to
     * @throws IOException if an I/O exception occurs
     */
    Path writeTo(Path temporaryFile) throws IOException;

    R getResult();
  }

  private final Path directory;

  TemporaryFileWriter(Path directory) {
    this.directory = directory;
  }

  <R> R write(Writer<R> writer) throws IOException {
    Path temporaryFile = Files.createTempFile(directory, null, null);
    temporaryFile.toFile().deleteOnExit();

    Path destinationFile = writer.writeTo(temporaryFile);
    try {
      Files.move(temporaryFile, destinationFile);

    } catch (FileAlreadyExistsException ignored) {
      // If the file already exists, we skip renaming and use the existing file. This happens if a
      // new layer happens to have the same content as a previously-cached layer.
      //
      // Do not attempt to remove the try-catch block with the idea of checking file existence
      // before moving; there can be concurrent file moves.
    }

    return writer.getResult();
  }
}
