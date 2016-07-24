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

package com.facebook.buck.lua;

import com.facebook.buck.cxx.AbstractCxxLibrary;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLinkableEnhancer;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPreprocessAndCompile;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorDep;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxSource;
import com.facebook.buck.cxx.CxxSourceRuleFactory;
import com.facebook.buck.cxx.HeaderVisibility;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.cxx.Linkers;
import com.facebook.buck.cxx.NativeLinkableInput;
import com.facebook.buck.file.WriteFile;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.WriteStringTemplateRule;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.util.Escaper;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;

import org.immutables.value.Value;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link Starter} implementation which builds a starter as a native executable.
 */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractNativeExecutableStarter implements Starter {

  private static final String NATIVE_STARTER_CXX_SOURCE =
      "com/facebook/buck/lua/native-starter.cpp.in";

  abstract BuildRuleParams getBaseParams();
  abstract BuildRuleResolver getRuleResolver();
  abstract SourcePathResolver getPathResolver();
  abstract LuaConfig getLuaConfig();
  abstract CxxBuckConfig getCxxBuckConfig();
  abstract CxxPlatform getCxxPlatform();
  abstract Path getOutput();
  abstract String getMainModule();
  abstract Optional<BuildTarget> getNativeStarterLibrary();
  abstract Optional<Path> getRelativeModulesDir();
  abstract Optional<Path> getRelativePythonModulesDir();
  abstract Optional<Path> getRelativeNativeLibsDir();

  private String getNativeStarterCxxSourceTemplate() {
    try {
      return Resources.toString(Resources.getResource(NATIVE_STARTER_CXX_SOURCE), Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CxxSource getNativeStarterCxxSource() {
    BuildTarget templateTarget =
        BuildTarget.builder(getBaseParams().getBuildTarget())
            .addFlavors(ImmutableFlavor.of("native-starter-cxx-source-template"))
            .build();
    getRuleResolver().addToIndex(
        new WriteFile(
            getBaseParams().copyWithChanges(
                templateTarget,
                Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>of()),
                Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>of())),
            getPathResolver(),
            getNativeStarterCxxSourceTemplate(),
            BuildTargets.getGenPath(
                getBaseParams().getProjectFilesystem(),
                templateTarget,
                "%s/native-starter.cpp.in"),
            /* executable */ false));

    BuildTarget target =
        BuildTarget.builder(getBaseParams().getBuildTarget())
            .addFlavors(ImmutableFlavor.of("native-starter-cxx-source"))
            .build();
    Path output =
        BuildTargets.getGenPath(
            getBaseParams().getProjectFilesystem(),
            target,
            "%s/native-starter.cpp");
    getRuleResolver().addToIndex(
        WriteStringTemplateRule.from(
            getBaseParams(),
            getPathResolver(),
            target,
            output,
            new BuildTargetSourcePath(templateTarget),
            ImmutableMap.of(
                "MAIN_MODULE",
                Escaper.escapeAsPythonString(getMainModule()),
                "MODULES_DIR",
                getRelativeModulesDir().isPresent() ?
                    Escaper.escapeAsPythonString(getRelativeModulesDir().get().toString()) :
                    "NULL",
                "PY_MODULES_DIR",
                getRelativePythonModulesDir().isPresent() ?
                    Escaper.escapeAsPythonString(getRelativePythonModulesDir().get().toString()) :
                    "NULL",
                "EXT_SUFFIX",
                Escaper.escapeAsPythonString(getCxxPlatform().getSharedLibraryExtension())),
            /* executable */ false));

    return CxxSource.of(
        CxxSource.Type.CXX,
        new BuildTargetSourcePath(target),
        ImmutableList.<String>of());
  }

  private ImmutableList<CxxPreprocessorInput> getTransitiveCxxPreprocessorInput(
      CxxPlatform cxxPlatform,
      Iterable<? extends CxxPreprocessorDep> deps)
      throws NoSuchBuildTargetException {
    ImmutableList.Builder<CxxPreprocessorInput> inputs = ImmutableList.builder();
    inputs.addAll(
        CxxPreprocessables.getTransitiveCxxPreprocessorInput(
            cxxPlatform,
            FluentIterable.from(deps)
                .filter(BuildRule.class)));
    for (CxxPreprocessorDep dep :
        Iterables.filter(deps, Predicates.not(Predicates.instanceOf(BuildRule.class)))) {
      inputs.add(dep.getCxxPreprocessorInput(cxxPlatform, HeaderVisibility.PUBLIC));
    }
    return inputs.build();
  }

  private Iterable<? extends AbstractCxxLibrary> getNativeStarterDeps() {
    return ImmutableList.of(
        getNativeStarterLibrary().isPresent() ?
            getRuleResolver().getRuleWithType(
                getNativeStarterLibrary().get(),
                AbstractCxxLibrary.class) :
            getLuaConfig().getLuaCxxLibrary(getRuleResolver()));
  }

  @Override
  public SourcePath build() throws NoSuchBuildTargetException {
    BuildTarget target =
        BuildTarget.builder(getBaseParams().getBuildTarget())
            .addFlavors(ImmutableFlavor.of("native-starter"))
            .build();
    Iterable<? extends AbstractCxxLibrary> nativeStarterDeps = getNativeStarterDeps();
    ImmutableMap<CxxPreprocessAndCompile, SourcePath> objects =
        CxxSourceRuleFactory.requirePreprocessAndCompileRules(
            getBaseParams(),
            getRuleResolver(),
            getPathResolver(),
            getCxxBuckConfig(),
            getCxxPlatform(),
            ImmutableList.<CxxPreprocessorInput>builder()
                .add(
                    CxxPreprocessorInput.builder()
                        .putAllPreprocessorFlags(
                            CxxSource.Type.CXX,
                            getNativeStarterLibrary().isPresent() ?
                                ImmutableList.<String>of() :
                                ImmutableList.of("-DBUILTIN_NATIVE_STARTER"))
                        .build())
                .addAll(getTransitiveCxxPreprocessorInput(getCxxPlatform(), nativeStarterDeps))
                .build(),
            ImmutableMultimap.<CxxSource.Type, String>of(),
            Optional.<SourcePath>absent(),
            getCxxBuckConfig().getPreprocessMode(),
            ImmutableMap.of("native-starter.cpp", getNativeStarterCxxSource()),
            CxxSourceRuleFactory.PicType.PDC);
    getRuleResolver().addToIndex(
        CxxLinkableEnhancer.createCxxLinkableBuildRule(
            getCxxBuckConfig(),
            getCxxPlatform(),
            getBaseParams(),
            getRuleResolver(),
            getPathResolver(),
            target,
            Linker.LinkType.EXECUTABLE,
            Optional.<String>absent(),
            getOutput(),
            Linker.LinkableDepType.SHARED,
            nativeStarterDeps,
            Optional.<Linker.CxxRuntimeType>absent(),
            Optional.<SourcePath>absent(),
            ImmutableSet.<BuildTarget>of(),
            NativeLinkableInput.builder()
                .addAllArgs(
                    getRelativeNativeLibsDir().isPresent() ?
                        StringArg.from(
                            Linkers.iXlinker(
                                "-rpath",
                                String.format(
                                    "%s/%s",
                                    getCxxPlatform().getLd().resolve(getRuleResolver()).origin(),
                                    getRelativeNativeLibsDir().get().toString()))) :
                        ImmutableList.<com.facebook.buck.rules.args.Arg>of())
                .addAllArgs(SourcePathArg.from(getPathResolver(), objects.values()))
                .build()));
    return new BuildTargetSourcePath(target);
  }

}
