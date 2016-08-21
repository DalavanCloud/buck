/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.eden;

import com.facebook.buck.io.DefaultProjectFilesystemDelegate;
import com.facebook.buck.io.ProjectFilesystemDelegate;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.facebook.eden.thrift.EdenError;
import com.facebook.thrift.TException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.nio.file.Path;

public final class EdenProjectFilesystemDelegate implements ProjectFilesystemDelegate {

  private final EdenMount mount;

  /** Delegate to forward requests to for files that are outside of the {@link #mount}. */
  private final ProjectFilesystemDelegate delegate;

  public EdenProjectFilesystemDelegate(EdenMount mount) {
    this(mount, new DefaultProjectFilesystemDelegate(mount.getProjectRoot()));
  }

  @VisibleForTesting
  EdenProjectFilesystemDelegate(EdenMount mount, ProjectFilesystemDelegate delegate) {
    this.mount = mount;
    this.delegate = delegate;
  }

  @Override
  public Sha1HashCode computeSha1(Path pathRelativeToProjectRootOrJustAbsolute) throws IOException {
    Path fileToHash = getPathForRelativePath(pathRelativeToProjectRootOrJustAbsolute);
    return computeSha1(fileToHash, /* retryWithRealPathIfEdenError */ true);
  }

  private Sha1HashCode computeSha1(Path path, boolean retryWithRealPathIfEdenError)
      throws IOException {
    Preconditions.checkArgument(path.isAbsolute());
    Optional<Path> entry = mount.getPathRelativeToProjectRoot(path);

    // TODO(t12516031): Generalize this to check if entry is under any of the Eden client's bind
    // mounts rather than hardcoding a test for buck-out/.
    if (entry.isPresent() && !entry.get().toString().contains("buck-out")) {
      try {
        return mount.getSha1(entry.get());
      } catch (TException e) {
        throw new IOException(e);
      } catch (EdenError e) {
        if (retryWithRealPathIfEdenError) {
          // It's possible that an EdenError was thrown because entry.get() was a path to a symlink,
          // which is not supported by Eden's getSha1() API. Try again if the real path is different
          // from the original path.
          Path realPath = path.toRealPath();
          if (!realPath.equals(path)) {
            return computeSha1(realPath, /* retryWithRealPathIfEdenError */ false);
          }
        }
        throw new IOException(e);
      }
    }

    return delegate.computeSha1(path);
  }

  @Override
  public Path getPathForRelativePath(Path pathRelativeToProjectRootOrJustAbsolute) {
    return delegate.getPathForRelativePath(pathRelativeToProjectRootOrJustAbsolute);
  }
}
