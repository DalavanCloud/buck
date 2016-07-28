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

package com.facebook.buck.maven;

import com.facebook.buck.jvm.java.HasMavenCoordinates;
import com.facebook.buck.jvm.java.MavenPublishable;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildRule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Pom {

  private static final MavenXpp3Writer POM_WRITER = new MavenXpp3Writer();
  private static final DefaultModelBuilderFactory MODEL_BUILDER_FACTORY =
      new DefaultModelBuilderFactory();
  /**
   * Consistent with the value used in the implementation of {@link MavenXpp3Writer#write}
   */
  private static final String POM_MODEL_VERSION = "4.0.0";

  private final Model model;
  private final MavenPublishable publishable;
  private final Path path;

  public Pom(Path path, MavenPublishable buildRule) throws IOException {
    this.path = path;
    this.publishable = buildRule;
    this.model = constructModel();
    applyBuildRule();
  }

  public static Path generatePomFile(MavenPublishable rule) throws IOException {
    Path pom = getPomPath(rule);
    generatePomFile(rule, pom);
    return pom;
  }

  private static Path getPomPath(HasMavenCoordinates rule) {
    return rule.getProjectFilesystem().resolve(
        BuildTargets.getGenPath(
            rule.getProjectFilesystem(),
            rule.getBuildTarget(),
            "%s.pom"));
  }

  @VisibleForTesting
  static void generatePomFile(
      MavenPublishable rule,
      Path optionallyExistingPom) throws IOException {
    new Pom(optionallyExistingPom, rule).flushToFile();
  }

  private void applyBuildRule() {
    if (!HasMavenCoordinates.MAVEN_COORDS_PRESENT_PREDICATE.apply(publishable)) {
      throw new IllegalArgumentException(
          "Cannot retrieve maven coordinates for target" +
              publishable.getBuildTarget().getFullyQualifiedName());
    }
    DefaultArtifact artifact = new DefaultArtifact(getMavenCoords(publishable).get());

    Iterable<Artifact> deps = FluentIterable
        .from(publishable.getMavenDeps())
        .filter(HasMavenCoordinates.MAVEN_COORDS_PRESENT_PREDICATE)
        .transform(
            new Function<HasMavenCoordinates, Artifact>() {
              @Override
              public Artifact apply(HasMavenCoordinates input) {
                return new DefaultArtifact(input.getMavenCoords().get());
              }
            });

    updateModel(artifact, deps);
  }

  private Model constructModel() throws IOException {
    File file = path.toFile();
    Model model;
    if (file.isFile()) {
      ModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()
          .setPomFile(file);
      ModelBuilder modelBuilder = MODEL_BUILDER_FACTORY.newInstance();
      try {
        ModelBuildingResult modelBuildingResult = modelBuilder.build(modelBuildingRequest);

        // Would contain extra stuff: <build/>, <repositories/>, <pluginRepositories/>, <reporting/>
        // model = modelBuildingResult.getEffectiveModel();

        model = Preconditions.checkNotNull(modelBuildingResult.getRawModel());
      } catch (ModelBuildingException e) {
        throw new RuntimeException(e);
      }
    } else {
      model = new Model();
      model.setModelVersion(POM_MODEL_VERSION);
    }

    model.setUrl("http://www.seleniumhq.org/");

    model.setDescription(
        "Selenium automates browsers. That's it! " +
        "What you do with that power is entirely up to you.");

    License license = new License();
    license.setName("The Apache Software License, Version 2.0");
    license.setUrl("http://www.apache.org/licenses/LICENSE-2.0.txt");
    license.setDistribution("repo");
    model.addLicense(license);

    Scm scm = new Scm();
    scm.setUrl("https://github.com/SeleniumHQ/selenium/");
    scm.setConnection("scm:git:git@github.com:SeleniumHQ/selenium.git");
    scm.setDeveloperConnection("scm:git:git@github.com:SeleniumHQ/selenium.git");
    model.setScm(scm);

    Developer developer = new Developer();
    developer.setName("Simon Stewart");
    developer.setId("simon.m.stewart");
    developer.addRole("Owner");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Daniel Wagner-Hall");
    developer.setId("dawagner");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Eran Mes");
    developer.setId("eran.mes@gmail.com");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Jim Evans");
    developer.setId("james.h.evans.jr");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Jari Bakken");
    developer.setId("jari.bakken");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Michael Tamm");
    developer.setId("michael.tamm2");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("David Burns");
    developer.setId("theautomatedtester");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Kristian Rosenvold");
    developer.setId("krosenvold");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Luke Inman-Semerau");
    developer.setId("lsemerau");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    developer = new Developer();
    developer.setName("Alexei Barantsev");
    developer.setId("barancev");
    developer.addRole("Committer");
    model.addDeveloper(developer);

    return model;
  }

  private void updateModel(Artifact mavenCoordinates, Iterable<Artifact> deps) {
    model.setGroupId(mavenCoordinates.getGroupId());
    model.setArtifactId(mavenCoordinates.getArtifactId());
    model.setVersion(mavenCoordinates.getVersion());
    if (Strings.isNullOrEmpty(model.getName())) {
      model.setName(mavenCoordinates.getArtifactId()); // better than nothing
    }

    // Dependencies
    ImmutableMap<DepKey, Dependency> depIndex = Maps.uniqueIndex(
        getModel().getDependencies(), new Function<Dependency, DepKey>() {
          @Override
          public DepKey apply(Dependency input) {
            return new DepKey(input);
          }
        });
    for (Artifact artifactDep : deps) {
      DepKey key = new DepKey(artifactDep);
      Dependency dependency = depIndex.get(key);
      if (dependency == null) {
        dependency = key.createDependency();
        getModel().addDependency(dependency);
      }
      updateDependency(dependency, artifactDep);
    }
  }

  private static void updateDependency(Dependency dependency, Artifact providedMavenCoordinates) {
    dependency.setVersion(providedMavenCoordinates.getVersion());
    if (providedMavenCoordinates.getClassifier() != null &&
        !"".equals(providedMavenCoordinates.getClassifier())) {
      dependency.setClassifier(providedMavenCoordinates.getClassifier());
    }
  }

  public void flushToFile() throws IOException {
    getModel(); // Ensure model is initialized, reading file if necessary
    flushTo(Files.newOutputStream(getPath()));
  }

  private void flushTo(OutputStream destination) throws IOException {
    POM_WRITER.write(new OutputStreamWriter(destination,
        Charset.forName(getModel().getModelEncoding())), getModel());
  }

  private static Optional<String> getMavenCoords(BuildRule buildRule) {
    if (buildRule instanceof HasMavenCoordinates) {
      return ((HasMavenCoordinates) buildRule).getMavenCoords();
    }
    return Optional.absent();
  }

  public Model getModel() {
    return model;
  }

  public Path getPath() {
    return path;
  }

  private static final class DepKey {
    private final String groupId;
    private final String artifactId;

    public DepKey(Artifact artifact) {
      groupId = artifact.getGroupId();
      artifactId = artifact.getArtifactId();
      validate();
    }

    public DepKey(Dependency dependency) {
      groupId = dependency.getGroupId();
      artifactId = dependency.getArtifactId();
      validate();
    }

    private void validate() {
      Preconditions.checkNotNull(groupId);
      Preconditions.checkNotNull(artifactId);
    }

    public Dependency createDependency() {
      Dependency dependency = new Dependency();
      dependency.setGroupId(groupId);
      dependency.setArtifactId(artifactId);
      return dependency;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof DepKey)) {
        return false;
      }

      DepKey depKey = (DepKey) o;

      return Objects.equals(groupId, depKey.groupId) &&
          Objects.equals(artifactId, depKey.artifactId);
    }

    @Override
    public int hashCode() {
      int result = groupId.hashCode();
      result = 31 * result + artifactId.hashCode();
      return result;
    }
  }
}
