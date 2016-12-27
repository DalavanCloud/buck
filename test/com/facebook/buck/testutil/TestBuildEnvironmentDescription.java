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

package com.facebook.buck.testutil;

import com.facebook.buck.util.environment.BuildEnvironmentDescription;

import java.util.Optional;

public class TestBuildEnvironmentDescription {

  public static final BuildEnvironmentDescription INSTANCE =
      BuildEnvironmentDescription.builder()
          .setUser("test_user")
          .setHostname("test_hostname")
          .setOs("test_os")
          .setAvailableCores(1)
          .setSystemMemory(1024L)
          .setBuckDirty(Optional.of(false))
          .setBuckCommit("test_commit")
          .setJavaVersion("test_java_version")
          .setJsonProtocolVersion(1)
          .build();

  private TestBuildEnvironmentDescription() {

  }
}
