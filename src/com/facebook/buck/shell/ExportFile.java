/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.shell;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractBuildable;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.FileSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.util.BuckConstant;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Export a file so that it can be easily referenced by other {@link com.facebook.buck.rules.BuildRule}s. There are several
 * valid ways of using export_file (all examples in a build file located at "path/to/buck/BUCK").
 * The most common usage of export_file is:
 * <pre>
 *   export_file(name = 'some-file.html')
 * </pre>
 * This is equivalent to:
 * <pre>
 *   export_file(name = 'some-file.html',
 *     src = 'some-file.html',
 *     out = 'some-file.html')
 * </pre>
 * This results in "//path/to/buck:some-file.html" as the rule, and will export the file
 * "some-file.html" as "some-file.html".
 * <pre>
 *   export_file(
 *     name = 'foobar.html',
 *     src = 'some-file.html',
 *   )
 * </pre>
 * Is equivalent to:
 * <pre>
 *    export_file(name = 'foobar.html', src = 'some-file.html', out = 'foobar.html')
 * </pre>
 * Finally, it's possible to refer to the exported file with a logical name, while controlling the
 * actual file name. For example:
 * <pre>
 *   export_file(name = 'ie-exports',
 *     src = 'some-file.js',
 *     out = 'some-file-ie.js',
 *   )
 * </pre>
 * As a rule of thumb, if the "out" parameter is missing, the "name" parameter is used as the name
 * of the file to be saved.
 */
public class ExportFile extends AbstractBuildable {

  private final SourcePath src;
  private final Path out;
  private final BuildTarget target;

  @VisibleForTesting
  ExportFile(final BuildRuleParams params, Optional<SourcePath> src, Optional<Path> out) {
    this.target = params.getBuildTarget();

    if (src.isPresent()) {
      this.src = src.get();
    } else {
      if (target.getBasePath().length() == 0) {
        this.src = new FileSourcePath(target.getShortName());
      } else {
        String relativeToProject = target.getBaseNameWithSlash() + target.getShortName();
        this.src = new FileSourcePath(relativeToProject);
      }
    }

    Path shortName = Paths.get(params.getBuildTarget().getShortName());
    Path name = out.or(shortName).getFileName();
    this.out = Paths.get(
        BuckConstant.GEN_DIR,
        params.getBuildTarget().getBasePathWithSlash()).resolve(name);
  }

  @Override
  public Iterable<String> getInputsToCompareToOutput() {
    // Ugh. If the source is a build target, we should be invalidated if that target is invalidated
    // and so we only care about the case when the source is, in fact, a local file.
    if (src instanceof FileSourcePath) {
      return ImmutableSet.of(src.asReference());
    }
    return ImmutableSet.of();
  }

  @Override
  public RuleKey.Builder appendDetailsToRuleKey(RuleKey.Builder builder) {
    // TODO(simons): How should src and out factor into this?
    return builder
        .set("src", src.toString());
  }

  @Override
  public List<Step> getBuildSteps(BuildContext context, BuildableContext buildableContext)
      throws IOException {
    Path srcPath = src.resolve(context);

    // This file is copied rather than symlinked so that when it is included in an archive zip and
    // unpacked on another machine, it is an ordinary file in both scenarios.
    ImmutableList.Builder<Step> builder = ImmutableList.<Step>builder()
        .add(new MkdirStep(out.getParent()))
        .add(new CopyStep(srcPath, out));

    return builder.build();
  }

  @Override
  public String getPathToOutputFile() {
    return out.toString();
  }
}
