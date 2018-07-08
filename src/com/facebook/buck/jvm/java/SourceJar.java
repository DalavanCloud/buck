package com.facebook.buck.jvm.java;

import static com.facebook.buck.maven.aether.AetherUtil.CLASSIFIER_SOURCES;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.description.BuildRuleParams;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.HasMavenCoordinates;
import com.facebook.buck.log.Logger;
import com.facebook.buck.maven.aether.AetherUtil;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.util.zip.ZipCompressionLevel;
import com.facebook.buck.zip.ZipStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

public class SourceJar extends AbstractBuildRuleWithDeclaredAndExtraDeps
    implements MavenPublishable {

  private final static Logger LOG = Logger.get(SourceJar.class);

  @AddToRuleKey
  private final ImmutableSet<SourcePath> sources;
  @AddToRuleKey
  private final Optional<String> mavenCoords;
  @AddToRuleKey
  private final Optional<SourcePath> mavenPomTemplate;
  @AddToRuleKey
  private final ImmutableSortedSet<HasMavenCoordinates> mavenDeps;

  private final Path output;
  private final Path scratchDir;

  public SourceJar(
      BuildTarget target,
      ProjectFilesystem filesystem,
      BuildRuleParams params,
      ImmutableSet<SourcePath> sources,
      Optional<String> mavenCoords,
      Optional<SourcePath> mavenPomTemplate,
      ImmutableSortedSet<HasMavenCoordinates> mavenDeps) {
    super(target, filesystem, params);

    this.mavenCoords = mavenCoords.map(coord -> AetherUtil.addClassifier(coord, CLASSIFIER_SOURCES));
    this.mavenPomTemplate = mavenPomTemplate;
    this.mavenDeps = mavenDeps;
    this.sources = sources;

    this.output = BuildTargets.getGenPath(
        getProjectFilesystem(),
        getBuildTarget(),
        String.format("%%s/%s-sources.jar", getBuildTarget().getShortName()));
    this.scratchDir = BuildTargets.getScratchPath(
        getProjectFilesystem(),
        getBuildTarget(),
        String.format("%%s/%s-sources.tmp", getBuildTarget().getShortName()));
  }

  @Override
  public Optional<String> getMavenCoords() {
    return mavenCoords;
  }

  @Override
  public Iterable<HasMavenCoordinates> getMavenDeps() {
    return mavenDeps;
  }

  @Override
  public Iterable<BuildRule> getPackagedDependencies() {
    return ImmutableSet.of(this);
  }

  @Override
  public Optional<SourcePath> getPomTemplate() {
    return mavenPomTemplate;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.addAll(MakeCleanDirectoryStep.of(
        BuildCellRelativePath.fromCellRelativePath(
            context.getBuildCellRootPath(),
            getProjectFilesystem(),
            scratchDir)));
    steps.addAll(MakeCleanDirectoryStep.of(
        BuildCellRelativePath.fromCellRelativePath(
            context.getBuildCellRootPath(),
            getProjectFilesystem(),
            output.getParent())));

    JavaFileParser javaFileParser = JavaFileParser.createJavaFileParser(
        JavacOptions.builder().setSourceLevel("8").build());

    Function<SourcePath, String> pathToPackage = sourcepath -> {
      Path absolutePath = context.getSourcePathResolver().getAbsolutePath(sourcepath);

      // Only try and parse java code if it's java code.
      if ("java".equals(MorePaths.getFileExtension(absolutePath))) {
        String code;
        try {
          code = new String(Files.readAllBytes(absolutePath), StandardCharsets.UTF_8);
          Optional<String> fromSource = javaFileParser.getPackageNameFromSource(code);
          if (fromSource.isPresent()) {
            return fromSource.get();
          }
        } catch (Exception e) {
          // Continue, because this might not even _be_ a java file.
          LOG.debug(e, "Unable to parse %s", sourcepath);
        }
      }

      // Fallback to just look at the original package resolver.
      Path relativePath = context.getSourcePathResolver().getRelativePath(sourcepath);
      return context.getJavaPackageFinder().findJavaPackage(relativePath);
    };
    pathToPackage = pathToPackage.andThen(name -> name.replace('.', '/'));

    // Copy all source files to the correct scratch directory
    Set<Path> directories = new HashSet<>();
    for (SourcePath source : sources) {
      // Resolve the file.
      String packageName = pathToPackage.apply(source);
      Path absolutePath = context.getSourcePathResolver().getAbsolutePath(source);
      Path destination = scratchDir.resolve(packageName).resolve(absolutePath.getFileName());

      if (directories.add(destination.getParent())) {
        steps.add(MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(),
                getProjectFilesystem(),
                destination.getParent())));
      }

      steps.add(CopyStep.forFile(getProjectFilesystem(), absolutePath, destination));
    }

    // Then zip them up
    steps.add(new ZipStep(
        getProjectFilesystem(),
        output,
        ImmutableSet.of(),
        false,
        ZipCompressionLevel.DEFAULT,
        scratchDir));

    return steps.build();
  }

  @Nullable
  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
  }
}
