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
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleCreationContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.HasDeclaredDeps;
import com.facebook.buck.rules.HasSrcs;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Supplier;
import org.immutables.value.Value;

public class ClosureBinaryDescription implements
    Description<JsBinaryArg>,
    ImplicitDepsInferringDescription<JsBinaryArg> {

  private final JavascriptConfig config;

  public ClosureBinaryDescription(JavascriptConfig config) {
    this.config = config;
  }

  @Override
  public Class<JsBinaryArg> getConstructorArgType() {
    return JsBinaryArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContext context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      JsBinaryArg args) {
    BuildRuleResolver resolver = context.getBuildRuleResolver();
    SourcePathRuleFinder finder = new SourcePathRuleFinder(resolver);
    Supplier<? extends SortedSet<BuildRule>> declaredDeps = params
        .getDeclaredDeps();
    return new JsBinary(
        buildTarget,
        context.getProjectFilesystem(),
        params,
        config.getClosureCompiler(args.getCompiler(), finder),
        declaredDeps,
        args.getSrcs(),
        args.getDefines(),
        args.getFlags(),
        args.getExterns(),
        args.getNoFormat());
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      JsBinaryArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    extraDepsBuilder.addAll(
        RichStream.of(config.getClosureCompilerSourcePath(constructorArg.getCompiler()))
            .filter(BuildTargetSourcePath.class)
            .map(BuildTargetSourcePath::getTarget)
            .collect(ImmutableList.toImmutableList()));
  }

  @BuckStyleImmutable
  @Value.Immutable
  public interface AbstractJsBinaryArg extends CommonDescriptionArg, HasSrcs, HasDeclaredDeps {
    ImmutableList<String> getDefines();
    ImmutableList<SourcePath> getExterns();
    ImmutableList<String> getFlags();
    Optional<Boolean> getNoFormat();
    Optional<SourcePath> getCompiler();
  }
}
