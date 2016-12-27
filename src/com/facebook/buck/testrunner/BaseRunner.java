/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.testrunner;

import com.facebook.buck.test.selectors.TestSelectorList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Base class for both the JUnit and TestNG runners.
 */
public abstract class BaseRunner {
  protected static final String ENCODING = "UTF-8";
  // This is to be extended when introducing new information into the result .xml file and allow
  // consumers of that information to understand whether the testrunner they're using supports the
  // new features.
  protected static final String[] RUNNER_CAPABILITIES = {"simple_test_selector"};

  protected File outputDirectory;
  protected List<String> testClassNames;
  protected long defaultTestTimeoutMillis;
  protected TestSelectorList testSelectorList;
  protected boolean isDryRun;
  protected boolean shouldExplainTestSelectors;

  public abstract void run() throws Throwable;

  /**
   * The test result file is written as XML to avoid introducing a dependency on JSON (see class
   * overview). We use the format used by junit for fun and games.
   */
  protected void writeResult(String testClassName, List<TestResult> results)
      throws IOException, ParserConfigurationException, TransformerException {
    // XML writer logic taken from:
    // http://www.genedavis.com/library/xml/java_dom_xml_creation.jsp

    Document buckDoc = createBuckOutput(testClassName, results);
    prettyPrint(testClassName, ".xml", buckDoc);
    Document junitDoc = createJUnitOutput(testClassName, results);
    prettyPrint(testClassName, "-junit4.xml", junitDoc);
  }

  private void prettyPrint(String testClassName, String suffix, Document doc)
      throws TransformerException, IOException {
    // Create an XML transformer that pretty-prints with a 2-space indent.
    // The transformer factory uses a system property to find the class to use. We need to default
    // to the system default since we have the user's classpath and they may not have everything set
    // up for the XSLT transform to work.
    String vendor = System.getProperty("java.vm.vendor");
    String factoryClass;
    if ("IBM Corporation".equals(vendor)) {
      // Used in the IBM JDK --- from
      // https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.aix.80.doc/user/xml/using_xml.html
      factoryClass = "com.ibm.xtq.xslt.jaxp.compiler.TransformerFactoryImpl";
    } else {
      // Used in the OpenJDK and the Oracle JDK.
      factoryClass = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";
    }
    // When we get this far, we're exiting, so no need to reset the property.
    System.setProperty("javax.xml.transform.TransformerFactory", factoryClass);
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer trans = transformerFactory.newTransformer();
    trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    // Write the result to a file.
    String testSelectorSuffix = "";
    if (!testSelectorList.isEmpty()) {
      testSelectorSuffix += ".test_selectors";
    }
    if (isDryRun) {
      testSelectorSuffix += ".dry_run";
    }
    OutputStream output;
    if (outputDirectory != null) {
      File outputFile = new File(outputDirectory, testClassName + testSelectorSuffix + suffix);
      output = new BufferedOutputStream(new FileOutputStream(outputFile));
    } else {
      output = System.out;
    }
    StreamResult streamResult = new StreamResult(output);
    DOMSource source = new DOMSource(doc);
    trans.transform(source, streamResult);
    if (outputDirectory != null) {
      output.close();
    }
  }

  private Document createBuckOutput(String testClassName, List<TestResult> results)
      throws ParserConfigurationException {
    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = docBuilder.newDocument();
    doc.setXmlVersion("1.1");

    Element root = doc.createElement("testcase");
    root.setAttribute("name", testClassName);
    root.setAttribute("runner_capabilities", getRunnerCapabilities());
    doc.appendChild(root);

    for (TestResult result : results) {
      Element test = doc.createElement("test");

      // suite attribute
      test.setAttribute("suite", result.testClassName);

      // name attribute
      test.setAttribute("name", result.testMethodName);

      // success attribute
      boolean isSuccess = result.isSuccess();
      test.setAttribute("success", Boolean.toString(isSuccess));

      // type attribute
      test.setAttribute("type", result.type.toString());

      // time attribute
      long runTime = result.runTime;
      test.setAttribute("time", String.valueOf(runTime));

      // Include failure details, if appropriate.
      Throwable failure = result.failure;
      if (failure != null) {
        String message = failure.getMessage();
        test.setAttribute("message", message);

        String stacktrace = stackTraceToString(failure);
        test.setAttribute("stacktrace", stacktrace);
      }

      // stdout, if non-empty.
      if (result.stdOut != null) {
        Element stdOutEl = doc.createElement("stdout");
        stdOutEl.appendChild(doc.createTextNode(result.stdOut));
        test.appendChild(stdOutEl);
      }

      // stderr, if non-empty.
      if (result.stdErr != null) {
        Element stdErrEl = doc.createElement("stderr");
        stdErrEl.appendChild(doc.createTextNode(result.stdErr));
        test.appendChild(stdErrEl);
      }

      root.appendChild(test);
    }

    return doc;
  }

  private Document createJUnitOutput(String testClassName, List<TestResult> results)
      throws ParserConfigurationException {
    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = docBuilder.newDocument();
    doc.setXmlVersion("1.1");

    Element testSuite = doc.createElement("testsuite");
    testSuite.setAttribute("name", testClassName);
    testSuite.setAttribute("tests", String.valueOf(results.size()));

    long errorCount = 0;
    float duration = 0;

    for (TestResult result : results) {
      Element testcase = doc.createElement("testcase");
      testcase.setAttribute("name", result.testMethodName);
      testcase.setAttribute("classname", result.testClassName);
      testcase.setAttribute("time", String.format(Locale.US, "%.3f", (result.runTime / 1000f)));
      duration += result.runTime;

      if (!result.isSuccess()) {
        errorCount++;
        Element failure = doc.createElement("failure");
        StringBuilder textContent = new StringBuilder();
        if (result.failure != null) {
          failure.setAttribute("message", result.failure.getMessage());
          String stackTrace = stackTraceToString(result.failure);
          textContent.append(stackTrace).append("\n");
        } else {
          failure.setAttribute("message", "An unknown failure occurred");
        }

        // stdout, if non-empty.
        if (result.stdOut != null) {
          textContent.append("====== STDOUT =====\n\n");
          textContent.append(result.stdOut);
          textContent.append("\n\n");
        }

        // stderr, if non-empty.
        if (result.stdErr != null) {
          textContent.append("====== STDERR =====\n\n");
          textContent.append(result.stdErr);
          textContent.append("\n\n");
        }

        failure.setTextContent(textContent.toString());
        testcase.appendChild(failure);
      }
      testSuite.appendChild(testcase);
    }

    testSuite.setAttribute("failures", String.valueOf(errorCount));
    testSuite.setAttribute("errors", "0");
    testSuite.setAttribute("time", String.format("%.3f", (duration / 1000f)));
    doc.appendChild(testSuite);

    return doc;
  }

  private static String getRunnerCapabilities() {
    StringBuilder result = new StringBuilder();
    int capsLen = RUNNER_CAPABILITIES.length;
    for (int i = 0; i < capsLen; i++) {
      String capability = RUNNER_CAPABILITIES[i];
      result.append(capability);
      if (i != capsLen - 1) {
        result.append(',');
      }
    }
    return result.toString();
  }

  private String stackTraceToString(Throwable exc) {
    StringWriter writer = new StringWriter();
    exc.printStackTrace(new PrintWriter(writer, /* autoFlush */true));
    return writer.toString();
  }

  /**
   * Expected arguments are:
   * <ul>
   *   <li>(string) output directory
   *   <li>(long) default timeout in milliseconds (0 for no timeout)
   *   <li>(string) newline separated list of test selectors
   *   <li>(string...) fully-qualified names of test classes
   * </ul>
   */
  protected void parseArgs(String... args) {
    File outputDirectory = null;
    long defaultTestTimeoutMillis = Long.MAX_VALUE;
    TestSelectorList.Builder testSelectorList = TestSelectorList.builder();
    boolean isDryRun = false;
    boolean shouldExplainTestSelectors = false;

    List<String> testClassNames = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--default-test-timeout":
          defaultTestTimeoutMillis = Long.parseLong(args[++i]);
          break;
        case "--test-selectors":
          List<String> rawSelectors = Arrays.asList(args[++i].split("\n"));
          testSelectorList.addRawSelectors(rawSelectors);
          break;
        case "--simple-test-selector":
          try {
            testSelectorList.addSimpleTestSelector(args[++i]);
          } catch (IllegalArgumentException e) {
            System.err.printf("--simple-test-selector takes 2 args: [suite] and [method name].");
            System.exit(1);
          }
          break;
        case "--b64-test-selector":
          try {
            testSelectorList.addBase64EncodedTestSelector(args[++i]);
          } catch (IllegalArgumentException e) {
            System.err.printf("--b64-test-selector takes 2 args: [suite] and [method name].");
            System.exit(1);
          }
          break;
        case "--explain-test-selectors":
          shouldExplainTestSelectors = true;
          break;
        case "--dry-run":
          isDryRun = true;
          break;
        case "--output":
          outputDirectory = new File(args[++i]);
          if (!outputDirectory.exists()) {
            System.err.printf("The output directory did not exist: %s\n", outputDirectory);
            System.exit(1);
          }
          break;
        default:
          testClassNames.add(args[i]);
      }
    }

    if (testClassNames.isEmpty()) {
      System.err.println("Must specify at least one test.");
      System.exit(1);
    }

    this.outputDirectory = outputDirectory;
    this.defaultTestTimeoutMillis = defaultTestTimeoutMillis;
    this.isDryRun = isDryRun;
    this.testClassNames = testClassNames;
    this.testSelectorList = testSelectorList.build();
    this.shouldExplainTestSelectors = shouldExplainTestSelectors;
  }

  protected void runAndExit() {
    // Run the tests.
    try {
      run();
    } catch (Throwable e){
      e.printStackTrace();
    } finally {
      // Explicitly exit to force the test runner to complete even if tests have sloppily left
      // behind non-daemon threads that would have otherwise forced the process to wait and
      // eventually timeout.
      //
      // Separately, we're using a successful exit code regardless of test outcome since JUnitRunner
      // is designed to execute all tests and produce a report of success or failure.  We've done
      // that successfully if we've gotten here.
      System.exit(0);
    }
  }
}
