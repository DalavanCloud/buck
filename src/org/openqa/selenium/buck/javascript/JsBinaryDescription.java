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

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePaths;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class JsBinaryDescription implements
    Description<JsBinaryDescription.Arg>,
    ImplicitDepsInferringDescription<JsBinaryDescription.Arg> {

  private static final BuildRuleType TYPE = BuildRuleType.of("js_binary");
  private final JavascriptConfig config;

  public JsBinaryDescription(JavascriptConfig config) {
    this.config = config;
  }

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> JsBinary createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return new JsBinary(
        params,
        new SourcePathResolver(resolver),
        config.getClosureCompiler(args.compiler, new SourcePathResolver(resolver)),
        params.getDeclaredDeps(),
        args.srcs,
        args.defines,
        args.flags,
        args.externs);
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      Function<Optional<String>, Path> cellRoots,
      Arg arg) {
    SourcePath compiler = config.getClosureCompilerSourcePath(arg.compiler);
    return SourcePaths.filterBuildTargetSourcePaths(Collections.singleton(compiler));
  }

  public static class Arg {
    public Optional<List<String>> defines;
    public Optional<List<SourcePath>> externs;
    public Optional<List<String>> flags;
    public ImmutableSortedSet<SourcePath> srcs;
    public Optional<SourcePath> compiler;

    public Optional<ImmutableSortedSet<BuildTarget>> deps;
  }
}
