/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.apple;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.Tool;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Function;

import org.immutables.value.Value;

/**
 * Adds Apple-specific tools to {@link CxxPlatform}.
 */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractAppleCxxPlatform {

  public static final Function<AppleCxxPlatform, AppleSdkPaths> TO_APPLE_SDK_PATHS =
      new Function<AppleCxxPlatform, AppleSdkPaths>() {
        @Override
        public AppleSdkPaths apply(AppleCxxPlatform platform) {
          return platform.getAppleSdkPaths();
        }
      };

  public abstract CxxPlatform getCxxPlatform();

  public abstract ApplePlatform getApplePlatform();

  public abstract AppleSdkPaths getAppleSdkPaths();

  public abstract Tool getActool();

}
