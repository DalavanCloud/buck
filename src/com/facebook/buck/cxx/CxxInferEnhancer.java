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

package com.facebook.buck.cxx;

import com.facebook.buck.graph.AbstractBreadthFirstThrowingTraversal;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import java.nio.file.Path;
import java.util.Set;

public final class CxxInferEnhancer {

  private CxxInferEnhancer() {}

  public enum InferFlavors {
    INFER(ImmutableFlavor.of("infer")),
    INFER_ANALYZE(ImmutableFlavor.of("infer-analyze")),
    INFER_CAPTURE(ImmutableFlavor.of("infer-capture")),
    INFER_CAPTURE_ALL(ImmutableFlavor.of("infer-capture-all"));

    private final ImmutableFlavor flavor;
    InferFlavors(ImmutableFlavor flavor) {
      this.flavor = flavor;
    }

    public ImmutableFlavor get() {
      return flavor;
    }

    public static ImmutableSet<ImmutableFlavor> getAll() {
      ImmutableSet.Builder<ImmutableFlavor> builder = ImmutableSet.builder();
      for (InferFlavors f : values()) {
        builder.add(f.get());
      }
      return builder.build();
    }

    private static BuildRuleParams paramsWithoutAnyInferFlavor(BuildRuleParams params) {
      BuildRuleParams result = params;
      for (InferFlavors f : values()) {
        result = result.withoutFlavor(f.get());
      }
      return result;
    }

    private static void checkNoInferFlavors(ImmutableSet<Flavor> flavors) {
      for (InferFlavors f : InferFlavors.values()) {
        Preconditions.checkArgument(
            !flavors.contains(f.get()),
            "Unexpected infer-related flavor found: %s", f.toString());
      }
    }
  }

  static class CxxInferCaptureAndAnalyzeRules {
    final ImmutableSet<CxxInferCapture> captureRules;
    final ImmutableSet<CxxInferAnalyze> allAnalyzeRules;

    CxxInferCaptureAndAnalyzeRules(
        ImmutableSet<CxxInferCapture> captureRules,
        ImmutableSet<CxxInferAnalyze> allAnalyzeRules) {
      this.captureRules = captureRules;
      this.allAnalyzeRules = allAnalyzeRules;
    }
  }

  public static ImmutableMap<String, CxxSource> collectSources(
      BuildTarget buildTarget,
      BuildRuleResolver ruleResolver,
      CxxPlatform cxxPlatform,
      CxxConstructorArg args) {
    InferFlavors.checkNoInferFlavors(buildTarget.getFlavors());
    return CxxDescriptionEnhancer.parseCxxSources(
        buildTarget,
        new SourcePathResolver(ruleResolver),
        cxxPlatform,
        args);
  }

  public static BuildRule requireAllTransitiveCaptureBuildRules(
      BuildRuleParams params,
      BuildRuleResolver ruleResolver,
      CxxPlatform cxxPlatform,
      InferBuckConfig inferBuckConfig,
      CxxInferSourceFilter sourceFilter,
      CxxConstructorArg args) throws NoSuchBuildTargetException {
    CxxSourceSet sources = collectSourcesOverDependencies(
        params.getBuildTarget(),
        ruleResolver,
        cxxPlatform,
        args);

    BuildRuleParams cleanParams = InferFlavors.paramsWithoutAnyInferFlavor(params);

    ImmutableSet<CxxInferCapture> captureRules = requireInferCaptureBuildRules(
        cleanParams,
        ruleResolver,
        cxxPlatform,
        sources.toSourcesMap(),
        inferBuckConfig,
        sourceFilter,
        args);

    return ruleResolver.addToIndex(
        new CxxInferCaptureTransitive(
            params.copyWithChanges(
                params.getBuildTarget(),
                Suppliers.ofInstance(
                    ImmutableSortedSet.<BuildRule>naturalOrder()
                        .addAll(captureRules)
                        .build()),
                params.getExtraDeps()),
            new SourcePathResolver(ruleResolver),
            captureRules));
  }

  public static CxxSourceSet collectSourcesOverDependencies(
      BuildTarget buildTarget,
      BuildRuleResolver ruleResolver,
      CxxPlatform cxxPlatform,
      CxxConstructorArg args) throws NoSuchBuildTargetException {
    Preconditions.checkState(
        buildTarget.getFlavors().contains(InferFlavors.INFER_CAPTURE_ALL.get()));

    CxxSourceSet.Builder sourcesBuilder = CxxSourceSet.builder();
    for (BuildTarget dep : args.deps.get()) {
      BuildTarget newTarget = BuildTarget.builder(dep)
          .addFlavors(InferFlavors.INFER_CAPTURE_ALL.get())
          .build();
      Optional<CxxSourceSet> sources = ruleResolver.requireMetadata(newTarget, CxxSourceSet.class);
      Preconditions.checkState(
          sources.isPresent(),
          "Expected a valid set of sources for:\n%s",
          Joiner.on(", ").join(newTarget.getFlavors()));
      sourcesBuilder.addAllSourcesSet(sources.get().getSourcesSet());
    }

    Set<Flavor> flavors = Sets.newHashSet(buildTarget.getFlavors());
    flavors.removeAll(InferFlavors.getAll());
    BuildTarget cleanTarget = BuildTarget.builder(buildTarget).setFlavors(flavors).build();
    sourcesBuilder.addAllSourcesSet(
        collectSources(
            cleanTarget,
            ruleResolver,
            cxxPlatform,
            args
        ).entrySet());
    return sourcesBuilder.build();
  }

  public static CxxInferComputeReport requireInferAnalyzeAndReportBuildRuleForCxxDescriptionArg(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathResolver pathResolver,
      CxxPlatform cxxPlatform,
      CxxConstructorArg args,
      InferBuckConfig inferConfig,
      CxxInferSourceFilter sourceFilter) throws NoSuchBuildTargetException {

    BuildRuleParams cleanParams = InferFlavors.paramsWithoutAnyInferFlavor(params);

    BuildRuleParams paramsWithInferFlavor = cleanParams.withFlavor(InferFlavors.INFER.get());

    Optional<CxxInferComputeReport> existingRule = resolver.getRuleOptionalWithType(
        paramsWithInferFlavor.getBuildTarget(), CxxInferComputeReport.class);
    if (existingRule.isPresent()) {
      return existingRule.get();
    }

    CxxInferAnalyze analysisRule = requireInferAnalyzeBuildRuleForCxxDescriptionArg(
        cleanParams,
        resolver,
        pathResolver,
        cxxPlatform,
        args,
        inferConfig,
        sourceFilter);
    return createInferReportRule(
        paramsWithInferFlavor,
        resolver,
        pathResolver,
        analysisRule);
  }

  public static CxxInferAnalyze requireInferAnalyzeBuildRuleForCxxDescriptionArg(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathResolver pathResolver,
      CxxPlatform cxxPlatform,
      CxxConstructorArg args,
      InferBuckConfig inferConfig,
      CxxInferSourceFilter sourceFilter) throws NoSuchBuildTargetException {

    BuildRuleParams cleanParams = InferFlavors.paramsWithoutAnyInferFlavor(params);

    BuildRuleParams paramsWithInferAnalyzeFlavor = cleanParams
        .withFlavor(InferFlavors.INFER_ANALYZE.get());

    Optional<CxxInferAnalyze> existingRule = resolver.getRuleOptionalWithType(
        paramsWithInferAnalyzeFlavor.getBuildTarget(), CxxInferAnalyze.class);
    if (existingRule.isPresent()) {
      return existingRule.get();
    }

    ImmutableMap<String, CxxSource> sources = collectSources(
        cleanParams.getBuildTarget(),
        resolver,
        cxxPlatform,
        args);

    ImmutableSet<CxxInferCapture> captureRules = requireInferCaptureBuildRules(
        cleanParams,
        resolver,
        cxxPlatform,
        sources,
        inferConfig,
        sourceFilter,
        args);

    // Build all the transitive dependencies build rules with the Infer's flavor
    ImmutableSet<CxxInferAnalyze> transitiveDepsLibraryRules = requireTransitiveDependentLibraries(
        cxxPlatform,
        cleanParams.getDeps());

    CxxInferCaptureAndAnalyzeRules cxxInferCaptureAndAnalyzeRules =
        new CxxInferCaptureAndAnalyzeRules(captureRules, transitiveDepsLibraryRules);

    return createInferAnalyzeRule(
        paramsWithInferAnalyzeFlavor,
        resolver,
        pathResolver,
        inferConfig,
        cxxInferCaptureAndAnalyzeRules);
  }

  private static ImmutableSet<CxxInferAnalyze> requireTransitiveDependentLibraries(
      final CxxPlatform cxxPlatform,
      final Iterable<? extends BuildRule> deps) throws NoSuchBuildTargetException {
    final ImmutableSet.Builder<CxxInferAnalyze> depsBuilder = ImmutableSet.builder();
    new AbstractBreadthFirstThrowingTraversal<BuildRule, NoSuchBuildTargetException>(deps) {
      @Override
      public ImmutableSet<BuildRule> visit(BuildRule buildRule) throws NoSuchBuildTargetException {
        if (buildRule instanceof CxxLibrary) {
          CxxLibrary library = (CxxLibrary) buildRule;
          depsBuilder.add(
              (CxxInferAnalyze) library.requireBuildRule(
                  InferFlavors.INFER_ANALYZE.get(),
                  cxxPlatform.getFlavor()));
          return buildRule.getDeps();
        }
        return ImmutableSet.of();
      }
    }.start();
    return depsBuilder.build();
  }

  private static ImmutableList<CxxPreprocessorInput>
  computePreprocessorInputForCxxBinaryDescriptionArg(
      BuildRuleParams params,
      CxxPlatform cxxPlatform,
      CxxBinaryDescription.Arg args,
      HeaderSymlinkTree headerSymlinkTree) throws NoSuchBuildTargetException {
    return CxxDescriptionEnhancer.collectCxxPreprocessorInput(
        params,
        cxxPlatform,
        CxxFlags.getLanguageFlags(
            args.preprocessorFlags,
            args.platformPreprocessorFlags,
            args.langPreprocessorFlags,
            cxxPlatform),
        ImmutableList.of(headerSymlinkTree),
        args.frameworks.or(ImmutableSortedSet.<FrameworkPath>of()),
        CxxPreprocessables.getTransitiveCxxPreprocessorInput(
            cxxPlatform,
            FluentIterable.from(params.getDeps())
                .filter(Predicates.instanceOf(CxxPreprocessorDep.class))));
  }

  private static ImmutableList<CxxPreprocessorInput>
  computePreprocessorInputForCxxLibraryDescriptionArg(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathResolver pathResolver,
      CxxPlatform cxxPlatform,
      CxxLibraryDescription.Arg args,
      HeaderSymlinkTree headerSymlinkTree) throws NoSuchBuildTargetException {
    return CxxDescriptionEnhancer.collectCxxPreprocessorInput(
        params,
        cxxPlatform,
        CxxFlags.getLanguageFlags(
            args.preprocessorFlags,
            args.platformPreprocessorFlags,
            args.langPreprocessorFlags,
            cxxPlatform),
        ImmutableList.of(headerSymlinkTree),
        ImmutableSet.<FrameworkPath>of(),
        CxxLibraryDescription.getTransitiveCxxPreprocessorInput(
            params,
            resolver,
            pathResolver,
            cxxPlatform,
            CxxFlags.getLanguageFlags(
                args.exportedPreprocessorFlags,
                args.exportedPlatformPreprocessorFlags,
                args.exportedLangPreprocessorFlags,
                cxxPlatform),
            CxxDescriptionEnhancer.parseExportedHeaders(
                params.getBuildTarget(),
                pathResolver,
                Optional.of(cxxPlatform),
                args),
            args.frameworks.or(ImmutableSortedSet.<FrameworkPath>of())));
  }

  private static ImmutableSet<CxxInferCapture> requireInferCaptureBuildRules(
      final BuildRuleParams params,
      final BuildRuleResolver resolver,
      CxxPlatform cxxPlatform,
      ImmutableMap<String, CxxSource> sources,
      InferBuckConfig inferBuckConfig,
      CxxInferSourceFilter sourceFilter,
      CxxConstructorArg args) throws NoSuchBuildTargetException {

    InferFlavors.checkNoInferFlavors(params.getBuildTarget().getFlavors());

    SourcePathResolver pathResolver = new SourcePathResolver(resolver);

    ImmutableMap<Path, SourcePath> headers = CxxDescriptionEnhancer.parseHeaders(
        params.getBuildTarget(),
        pathResolver,
        Optional.of(cxxPlatform),
        args);

    // Setup the header symlink tree and combine all the preprocessor input from this rule
    // and all dependencies.
    HeaderSymlinkTree headerSymlinkTree = CxxDescriptionEnhancer.requireHeaderSymlinkTree(
        params,
        resolver,
        pathResolver,
        cxxPlatform,
        headers,
        HeaderVisibility.PRIVATE);

    ImmutableList<CxxPreprocessorInput> preprocessorInputs;

    if (args instanceof CxxBinaryDescription.Arg) {
      preprocessorInputs = computePreprocessorInputForCxxBinaryDescriptionArg(
          params,
          cxxPlatform,
          (CxxBinaryDescription.Arg) args,
          headerSymlinkTree);
    } else if (args instanceof CxxLibraryDescription.Arg) {
      preprocessorInputs = computePreprocessorInputForCxxLibraryDescriptionArg(
          params,
          resolver,
          pathResolver,
          cxxPlatform,
          (CxxLibraryDescription.Arg) args,
          headerSymlinkTree);
    } else {
      throw new IllegalStateException("Only Binary and Library args supported.");
    }

    CxxSourceRuleFactory factory = CxxSourceRuleFactory.of(
        params,
        resolver,
        pathResolver,
        cxxPlatform,
        preprocessorInputs,
        CxxFlags.getLanguageFlags(
            args.compilerFlags,
            args.platformCompilerFlags,
            args.langCompilerFlags,
            cxxPlatform),
        args.prefixHeader,
        CxxSourceRuleFactory.PicType.PDC);
    return factory.requireInferCaptureBuildRules(
        sources,
        inferBuckConfig,
        sourceFilter);
  }

  private static CxxInferAnalyze createInferAnalyzeRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathResolver pathResolver,
      InferBuckConfig inferConfig,
      CxxInferCaptureAndAnalyzeRules captureAnalyzeRules) {
    return resolver.addToIndex(
        new CxxInferAnalyze(
            params.copyWithChanges(
                params.getBuildTarget(),
                Suppliers.ofInstance(
                    ImmutableSortedSet.<BuildRule>naturalOrder()
                        .addAll(captureAnalyzeRules.captureRules)
                        .addAll(captureAnalyzeRules.allAnalyzeRules)
                        .build()),
                params.getExtraDeps()),
            pathResolver,
            inferConfig,
            captureAnalyzeRules));
  }

  private static CxxInferComputeReport createInferReportRule(
      BuildRuleParams buildRuleParams,
      BuildRuleResolver buildRuleResolver,
      SourcePathResolver sourcePathResolver,
      CxxInferAnalyze analysisToReport) {
    return buildRuleResolver.addToIndex(
        new CxxInferComputeReport(
            buildRuleParams.copyWithDeps(
                Suppliers.ofInstance(
                    ImmutableSortedSet.<BuildRule>naturalOrder()
                        .addAll(analysisToReport.getTransitiveAnalyzeRules())
                        .add(analysisToReport)
                        .build()),
                buildRuleParams.getExtraDeps()),
            sourcePathResolver,
            analysisToReport));
  }
}
