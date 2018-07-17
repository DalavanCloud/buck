/*
 * Copyright 2018-present Facebook, Inc.
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

package com.facebook.buck.android;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.jvm.java.testutil.AbiCompilationModeTest;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.zip.ZipConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.zip.ZipUtil;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AndroidAppBundleIntegrationTest extends AbiCompilationModeTest {

  private ProjectWorkspace workspace;
  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths(true);
  private ProjectFilesystem filesystem;
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws InterruptedException, IOException {
    AssumeAndroidPlatform.assumeSdkIsAvailable();
    AssumeAndroidPlatform.assumeNdkIsAvailable();
    AssumeAndroidPlatform.assumeBundleBuildIsSupported();
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    setWorkspaceCompilationMode(workspace);
    filesystem = TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());
  }

  @Test
  public void testAppBundleHaveDeterministicTimestamps() throws IOException {
    String target = "//apps/sample:app_bundle_1";
    ProcessResult result = workspace.runBuckCommand("build", target);
    result.assertSuccess();

    // Iterate over each of the entries, expecting to see all zeros in the time fields.
    Path aab =
        workspace.getPath(
            BuildTargets.getGenPath(filesystem, BuildTargetFactory.newInstance(target), "%s.apk"));
    Date dosEpoch = new Date(ZipUtil.dosToJavaTime(ZipConstants.DOS_FAKE_TIME));
    try (ZipInputStream is = new ZipInputStream(Files.newInputStream(aab))) {
      for (ZipEntry entry = is.getNextEntry(); entry != null; entry = is.getNextEntry()) {
        assertThat(entry.getName(), new Date(entry.getTime()), Matchers.equalTo(dosEpoch));
      }
    }

    ZipInspector zipInspector = new ZipInspector(aab);
    zipInspector.assertFileExists("base/dex/classes.dex");
    zipInspector.assertFileExists("base/assets.pb");
    zipInspector.assertFileExists("base/resources.pb");
    zipInspector.assertFileExists("base/manifest/AndroidManifest.xml");
    zipInspector.assertFileExists("base/assets/asset_file.txt");
    zipInspector.assertFileExists("base/res/drawable/tiny_black.png");
    zipInspector.assertFileExists("base/native.pb");

    NativeLibraries nativeLibraries =
        NativeLibraries.parseFrom(zipInspector.getFileContents("base/native.pb"));
    for (TargetedNativeDirectory targetedNativeDirectory : nativeLibraries.getDirectoryList()) {
      assertTrue(targetedNativeDirectory.hasTargeting());
      assertTrue(targetedNativeDirectory.getTargeting().hasAbi());
    }

    Assets assets = Assets.parseFrom(zipInspector.getFileContents("base/assets.pb"));
    for (TargetedAssetsDirectory targetedAssetsDirectory : assets.getDirectoryList()) {
      assertTrue(targetedAssetsDirectory.hasTargeting());
      assertTrue(targetedAssetsDirectory.getTargeting().hasLanguage());
    }
  }

  @Test
  public void testAppBundleHaveCorrectAaptMode() throws IOException {
    String target = "//apps/sample:app_bundle_wrong_aapt_mode";

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "Android App Bundle can only be built with aapt2, but " + target + " is using aapt1.");

    workspace.runBuckBuild(target);
  }
}
