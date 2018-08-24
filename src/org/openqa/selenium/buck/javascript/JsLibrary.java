/*
 * Copyright 2013-present Facebook, Inc.
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

package org.openqa.selenium.buck.javascript;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.attr.BuildOutputInitializer;
import com.facebook.buck.core.rules.attr.InitializableFromDisk;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.WriteFileStep;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class JsLibrary extends AbstractBuildRuleWithDeclaredAndExtraDeps implements
    InitializableFromDisk<JavascriptDependencies>, HasJavascriptDependencies {

  private final Path output;
  @AddToRuleKey
  private final ImmutableSortedSet<BuildRule> deps;
  @AddToRuleKey
  private final ImmutableSortedSet<SourcePath> srcs;
  private JavascriptDependencies joy;
  private final BuildOutputInitializer<JavascriptDependencies> buildOutputInitializer;

  public JsLibrary(
      BuildTarget target,
      ProjectFilesystem filesystem,
      BuildRuleParams params,
      ImmutableSortedSet<SourcePath> srcs) {
    super(target, filesystem, params);
    this.deps = ImmutableSortedSet.copyOf(getDeclaredDeps());
    this.srcs = Preconditions.checkNotNull(srcs);

    this.output = BuildTargetPaths.getGenPath(
        getProjectFilesystem(),
        getBuildTarget(),
        "%s-library.deps");

    buildOutputInitializer = new BuildOutputInitializer<>(getBuildTarget(), this);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    Set<String> allRequires = Sets.newHashSet();
    Set<String> allProvides = Sets.newHashSet();
    JavascriptDependencies smidgen = new JavascriptDependencies();
    for (SourcePath src : srcs) {
      Path path = context.getSourcePathResolver().getAbsolutePath(src);
      JavascriptSource source = new JavascriptSource(path);
      smidgen.add(source);
      allRequires.addAll(source.getRequires());
      allProvides.addAll(source.getProvides());
    }

    allRequires.removeAll(allProvides);

    for (BuildRule dep : deps) {
      Iterator<String> iterator = allRequires.iterator();

      if (!(dep instanceof HasJavascriptDependencies)) {
        continue;
      }
      JavascriptDependencies moreJoy = ((HasJavascriptDependencies) dep).getBundleOfJoy();

      while (iterator.hasNext()) {
        String require = iterator.next();

        Set<JavascriptSource> sources = moreJoy.getDeps(require);
        if (!sources.isEmpty()) {
          smidgen.addAll(sources);
          iterator.remove();
        }
      }
    }

    if (!allRequires.isEmpty()) {
      throw new RuntimeException(
          getBuildTarget() + " --- Missing dependencies for: " + allRequires);
    }

    StringWriter writer = new StringWriter();
    smidgen.writeTo(writer);

    ImmutableList.Builder<Step> builder = ImmutableList.builder();
    builder.add(MkdirStep.of(
        BuildCellRelativePath.fromCellRelativePath(
            context.getBuildCellRootPath(),
            getProjectFilesystem(),
            output.getParent())));
    builder.add(new WriteFileStep(getProjectFilesystem(), writer.toString(), output, false));

    buildableContext.recordArtifact(output);

    return builder.build();
  }

  @Override
  public JavascriptDependencies initializeFromDisk(SourcePathResolver pathResolver)
      throws IOException {
    List<String> allLines = getProjectFilesystem().readLines(output);
    joy = JavascriptDependencies.buildFrom(Joiner.on("\n").join(allLines));
    return joy;
  }

  @Override
  public BuildOutputInitializer<JavascriptDependencies> getBuildOutputInitializer() {
    return buildOutputInitializer;
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return PathSourcePath.of(getProjectFilesystem(), output);
  }

  @Override
  public JavascriptDependencies getBundleOfJoy() {
    return joy;
  }
}
