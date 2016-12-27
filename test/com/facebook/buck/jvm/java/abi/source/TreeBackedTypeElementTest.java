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

package com.facebook.buck.jvm.java.abi.source;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.testutil.CompilerTreeApiParameterized;
import com.google.common.base.Joiner;
import com.sun.source.tree.CompilationUnitTree;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;

import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

@RunWith(CompilerTreeApiParameterized.class)
public class TreeBackedTypeElementTest extends CompilerTreeApiTest {
  private static final String JAVAC = "javac";
  private static final String TREES = "trees";

  @Parameterized.Parameters
  public static Object[] getParameters() {
    return new Object[] { JAVAC, TREES };
  }

  @Parameterized.Parameter
  public String implementation;
  private Elements elements;

  @Test
  public void testGetSimpleName() throws IOException {
    compile("public class Foo {}");
    TypeElement element = elements.getTypeElement("Foo");

    assertNameEquals("Foo", element.getSimpleName());
  }

  @Test
  public void testGetQualifiedNameUnnamedPackage() throws IOException {
    compile("public class Foo {}");

    TypeElement element = elements.getTypeElement("Foo");

    assertNameEquals("Foo", element.getQualifiedName());
  }

  @Test
  public void testGetQualifiedNameNamedPackage() throws IOException {
    compile(Joiner.on('\n').join(
        "package com.facebook.buck;",
        "public class Foo {}"));

    TypeElement element = elements.getTypeElement("com.facebook.buck.Foo");

    assertNameEquals("com.facebook.buck.Foo", element.getQualifiedName());
  }

  @Test
  public void testGetQualifiedNameInnerClass() throws IOException {
    compile(Joiner.on('\n').join(
        "package com.facebook.buck;",
        "public class Foo {",
        "  public class Bar { }",
        "}"));

    TypeElement element = elements.getTypeElement("com.facebook.buck.Foo.Bar");

    assertNameEquals("com.facebook.buck.Foo.Bar", element.getQualifiedName());
  }

  @Test
  public void testGetQualifiedNameEnumAnonymousMember() throws IOException {
    compile(Joiner.on('\n').join(
        "public enum Foo {",
        "  BAR,",
        "  BAZ() {",
        "  }",
        "}"
    ));

    // Expect no crash. Enum anonymous members don't have qualified names, but at one point during
    // development we would crash trying to make one for them.
  }

  private void assertNameEquals(String expected, Name actual) {
    assertEquals(elements.getName(expected), actual);
  }

  @Override
  protected Iterable<? extends CompilationUnitTree> compile(String source) throws IOException {
    final Iterable<? extends CompilationUnitTree> result = super.compile(source);

    switch (implementation) {
      case JAVAC:
        elements = javacElements;
        break;
      case TREES:
        elements = treesElements;
        break;
    }

    return result;
  }

}
