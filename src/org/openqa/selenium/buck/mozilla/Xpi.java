/*
 * Copyright 2014-present Facebook, Inc.
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

package org.openqa.selenium.buck.mozilla;

import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.zip.ZipStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.openqa.selenium.buck.file.SrcZipAwareFileBundler;

import java.nio.file.Path;

public class Xpi extends AbstractBuildRule {

  private final Path scratch;
  private final Path output;
  @AddToRuleKey
  private final Path chrome;
  @AddToRuleKey
  private final ImmutableSortedSet<SourcePath> components;
  @AddToRuleKey
  private final ImmutableSortedSet<SourcePath> content;
  @AddToRuleKey
  private final Path install;
  @AddToRuleKey
  private final ImmutableSortedSet<SourcePath> resources;
  @AddToRuleKey
  private final ImmutableSortedSet<SourcePath> platforms;

  public Xpi(
      BuildRuleParams params,
      SourcePathResolver resolver,
      Path chrome,
      ImmutableSortedSet<SourcePath> components,
      ImmutableSortedSet<SourcePath> content,
      Path install,
      ImmutableSortedSet<SourcePath> resources,
      ImmutableSortedSet<SourcePath> platforms) {
    super(params, resolver);

    this.chrome = Preconditions.checkNotNull(chrome);
    this.components = Preconditions.checkNotNull(components);
    this.content = Preconditions.checkNotNull(content);
    this.install = Preconditions.checkNotNull(install);
    this.resources = Preconditions.checkNotNull(resources);
    this.platforms = Preconditions.checkNotNull(platforms);

    this.output = BuildTargets.getGenPath(
        getBuildTarget(),
        String.format("%%s/%s.xpi", getBuildTarget().getShortName()));

    this.scratch = BuildTargets.getScratchPath(getBuildTarget(), "%s-xpi");
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.add(new MakeCleanDirectoryStep(scratch));

    steps.add(CopyStep.forFile(chrome, scratch.resolve("chrome.manifest")));
    steps.add(CopyStep.forFile(install, scratch.resolve("install.rdf")));

    SrcZipAwareFileBundler bundler = new SrcZipAwareFileBundler(getBuildTarget());

    Path contentDir = scratch.resolve("content");
    steps.add(new MkdirStep(contentDir));
    bundler.copy(getResolver(), steps, contentDir, content, true);

    Path componentDir = scratch.resolve("components");
    steps.add(new MkdirStep(componentDir));
    bundler.copy(getResolver(), steps, componentDir, components, true);

    Path platformDir = scratch.resolve("platform");
    steps.add(new MkdirStep(platformDir));
    bundler.copy(getResolver(), steps, platformDir, platforms, false);

    Path resourceDir = scratch.resolve("resource");
    steps.add(new MkdirStep(resourceDir));
    bundler.copy(getResolver(), steps, resourceDir, resources, true);

    steps.add(new MakeCleanDirectoryStep(output.getParent()));
    steps.add(new ZipStep(
        output.normalize().toAbsolutePath(),
        ImmutableSet.<Path>of(),
        false,
        ZipStep.DEFAULT_COMPRESSION_LEVEL,
        scratch));

    buildableContext.recordArtifact(output);

    return steps.build();
  }

  @Override
  public Path getPathToOutputFile() {
    return output;
  }
}
