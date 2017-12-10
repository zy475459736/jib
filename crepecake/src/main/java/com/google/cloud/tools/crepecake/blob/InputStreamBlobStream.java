/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.blob;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestException;
import javax.annotation.Nonnull;

/** A {@link BlobStream} that streams from an {@link InputStream}. */
class InputStreamBlobStream implements BlobStream {

  private final InputStream inputStream;

  private final byte[] byteBuffer = new byte[8192];

  private BlobDescriptor writtenBlobDescriptor;

  InputStreamBlobStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  protected void writeFromInputStream(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    long bytesWritten = 0;
    int bytesRead;
    while ((bytesRead = inputStream.read(byteBuffer)) != -1) {
      outputStream.write(byteBuffer, 0, bytesRead);
      bytesWritten += bytesRead;
    }
    outputStream.flush();
    writtenBlobDescriptor = new BlobDescriptor(bytesWritten);
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException, DigestException {
    writeFromInputStream(inputStream, outputStream);
  }

  @Nonnull
  @Override
  public BlobDescriptor getWrittenBlobDescriptor() {
    if (null == writtenBlobDescriptor) {
      BlobStream.super.getWrittenBlobDescriptor();
    }
    return writtenBlobDescriptor;
  }
}
