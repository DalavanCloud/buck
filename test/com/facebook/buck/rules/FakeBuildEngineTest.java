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

package com.facebook.buck.rules;

import static com.facebook.buck.rules.BuildRuleSuccessType.BUILT_LOCALLY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.facebook.buck.artifact_cache.CacheResult;
import com.facebook.buck.artifact_cache.NoopArtifactCache;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.timing.DefaultClock;
import com.facebook.buck.util.ObjectMappers;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

public class FakeBuildEngineTest {

  @Test
  public void buildRuleFutureHasResult() throws Exception {
    BuildTarget fakeBuildTarget = BuildTargetFactory.newInstance("//foo:bar");
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    FakeBuildRule fakeBuildRule = new FakeBuildRule(fakeBuildTarget, pathResolver);
    BuildResult fakeBuildResult =
        BuildResult.success(fakeBuildRule, BUILT_LOCALLY, CacheResult.miss());
    FakeBuildEngine fakeEngine = new FakeBuildEngine(
        ImmutableMap.of(fakeBuildTarget, fakeBuildResult),
        ImmutableMap.of(fakeBuildTarget, new RuleKey("00")));
    assertThat(
        fakeEngine
            .build(
                BuildEngineBuildContext.builder()
                    .setBuildContext(FakeBuildContext.NOOP_CONTEXT)
                    .setArtifactCache(new NoopArtifactCache())
                    .setBuildId(new BuildId())
                    .setObjectMapper(ObjectMappers.newDefaultInstance())
                    .setClock(new DefaultClock())
                    .build(),
                TestExecutionContext.newInstance(),
                fakeBuildRule)
            .get(),
        equalTo(fakeBuildResult));
  }

  @Test
  public void buildRuleResultIsPresent() throws Exception {
    BuildTarget fakeBuildTarget = BuildTargetFactory.newInstance("//foo:bar");
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    FakeBuildRule fakeBuildRule = new FakeBuildRule(fakeBuildTarget, pathResolver);
    BuildResult fakeBuildResult =
        BuildResult.success(fakeBuildRule, BUILT_LOCALLY, CacheResult.miss());
    FakeBuildEngine fakeEngine = new FakeBuildEngine(
        ImmutableMap.of(fakeBuildTarget, fakeBuildResult),
        ImmutableMap.of(fakeBuildTarget, new RuleKey("00")));
    assertThat(
        fakeEngine.getBuildRuleResult(fakeBuildTarget),
        equalTo(fakeBuildResult));
  }

  @Test
  public void buildRuleIsBuilt() throws Exception {
    BuildTarget fakeBuildTarget = BuildTargetFactory.newInstance("//foo:bar");
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    FakeBuildRule fakeBuildRule = new FakeBuildRule(fakeBuildTarget, pathResolver);
    BuildResult fakeBuildResult =
        BuildResult.success(fakeBuildRule, BUILT_LOCALLY, CacheResult.miss());
    FakeBuildEngine fakeEngine = new FakeBuildEngine(
        ImmutableMap.of(fakeBuildTarget, fakeBuildResult),
        ImmutableMap.of(fakeBuildTarget, new RuleKey("00")));
    assertThat(
        fakeEngine.isRuleBuilt(fakeBuildTarget),
        is(true));
  }

  @Test
  public void unbuiltRuleIsNotBuilt() throws Exception {
    BuildTarget fakeBuildTarget = BuildTargetFactory.newInstance("//foo:bar");
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    FakeBuildRule fakeBuildRule = new FakeBuildRule(fakeBuildTarget, pathResolver);
    BuildResult fakeBuildResult =
        BuildResult.success(fakeBuildRule, BUILT_LOCALLY, CacheResult.miss());
    FakeBuildEngine fakeEngine = new FakeBuildEngine(
        ImmutableMap.of(fakeBuildTarget, fakeBuildResult),
        ImmutableMap.of(fakeBuildTarget, new RuleKey("00")));
    BuildTarget anotherFakeBuildTarget = BuildTargetFactory.newInstance("//foo:baz");
    assertThat(
        fakeEngine.isRuleBuilt(anotherFakeBuildTarget),
        is(false));
  }

  @Test
  public void ruleKeyIsPresent() throws Exception {
    BuildTarget fakeBuildTarget = BuildTargetFactory.newInstance("//foo:bar");
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    FakeBuildRule fakeBuildRule = new FakeBuildRule(fakeBuildTarget, pathResolver);
    BuildResult fakeBuildResult =
        BuildResult.success(fakeBuildRule, BUILT_LOCALLY, CacheResult.miss());
    FakeBuildEngine fakeEngine = new FakeBuildEngine(
        ImmutableMap.of(fakeBuildTarget, fakeBuildResult),
        ImmutableMap.of(fakeBuildTarget, new RuleKey("00")));
    assertThat(
        fakeEngine.getRuleKey(fakeBuildTarget),
        equalTo(new RuleKey("00")));
  }
}
