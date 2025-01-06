/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.compatibility.tradefed;

import static org.junit.Assert.*;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildProvider;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


/**
 * Tests for mts-tradefed.
 */
@RunWith(JUnit4.class)
public class MtsTradefedTest {

    private static final String PROPERTY_NAME = "MTS_ROOT";
    private static final String SUITE_FULL_NAME = "Mainline Test Suite";
    private static final String SUITE_NAME = "MTS";
    private static final String SUITE_PLAN = "mts";
    private static final String DYNAMIC_CONFIG_URL = "";
    private static final String REGEX_PATTERN_TEST_MODULE = "value=\\\"(.*)\\\"";
    private static final String REGEX_PATTERN_CONFIG = "([^\\/]+)\\.config";

    private String mOriginalProperty = null;

    @Before
    public void setUp() throws Exception {
        mOriginalProperty = System.getProperty(PROPERTY_NAME);
    }

    @After
    public void tearDown() throws Exception {
        if (mOriginalProperty != null) {
            System.setProperty(PROPERTY_NAME, mOriginalProperty);
        }
    }

    @Test
    public void testSuiteInfoLoad() throws Exception {
        // Test the values in the manifest can be loaded
        File root = FileUtil.createTempDir("root");
        System.setProperty(PROPERTY_NAME, root.getAbsolutePath());
        File base = new File(root, "android-mts");
        base.mkdirs();
        File tests = new File(base, "testcases");
        tests.mkdirs();
        CompatibilityBuildProvider provider = new CompatibilityBuildProvider();
        OptionSetter setter = new OptionSetter(provider);
        setter.setOptionValue("plan", SUITE_PLAN);
        setter.setOptionValue("dynamic-config-url", DYNAMIC_CONFIG_URL);
        IBuildInfo info = provider.getBuild();
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(info);
        assertEquals("Incorrect suite full name", SUITE_FULL_NAME, helper.getSuiteFullName());
        assertEquals("Incorrect suite name", SUITE_NAME, helper.getSuiteName());
        FileUtil.recursiveDelete(root);
    }

    @Test
    public void testModulesTaggedWithIncludeFilterLoad() throws Exception {
        String mtsRootVar = "MTS_ROOT";
        String suiteRoot = System.getProperty(mtsRootVar);
        if (Strings.isNullOrEmpty(suiteRoot)) {
            fail(String.format("Should run within a suite context: %s doesn't exist", mtsRootVar));
        }
        File testcases = new File(suiteRoot, "/android-mts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exist", testcases));
            return;
        }

        // Get all the tests configs in the testcases/ folder
        Set<File> listConfigs = FileUtil.findFilesObject(testcases, ".*\\.config");
        assertTrue(listConfigs.size() > 0);

        // Get all the test modules tagged with include-filter in tools/ folder
        File tools = new File(suiteRoot, "/android-mts/tools/");

        File mtsTestLists = new File(tools, "mts-tradefed.jar");
        Set<String> testModules = getTestModulesTaggedWithIncludeFilterFromXML(mtsTestLists);
        assertTrue(testModules.size() > 0);

        Set<String> configs = new HashSet<String>();
        for (File config : listConfigs) {
          Pattern pattern = Pattern.compile(REGEX_PATTERN_CONFIG);
          Matcher matcher = pattern.matcher(config.getName());
          if (matcher.find()) {
            String configName = matcher.group(1);
            configs.add(configName);
          }
        }
        for (String testModule : testModules) {
          assertTrue(String.format("%s not in configs", testModule), configs.contains(testModule));
        }
    }

    private Set<String> getTestModulesTaggedWithIncludeFilterFromXML(File jarFile)
        throws ZipException, IOException {

    Set<String> testModules = new HashSet<String>();

    JarFile jar = new JarFile(jarFile);
    // Getting the files into the jar
    Enumeration<JarEntry> enumeration = jar.entries();

    // Iterates into the files in the jar file
    while (enumeration.hasMoreElements()) {
      ZipEntry zipEntry = enumeration.nextElement();

      if (zipEntry.getName().endsWith(".xml") && zipEntry.getName().contains("tests-list")) {
        if (zipEntry.getName().contains("bluetooth")
            || zipEntry.getName().contains("bt")
            || zipEntry.getName().contains("smoke")) {
          continue;
        }
        // Relative path of file into the jar.
        String moduleTestList = zipEntry.getName();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(moduleTestList);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        while (true) {
          String s = reader.readLine();
          if (s == null) {
            break;
          }
          if (s.contains("compatibility:include-filter")) {
            Pattern pattern = Pattern.compile(REGEX_PATTERN_TEST_MODULE);
            Matcher matcher = pattern.matcher(s);
            if (matcher.find()) {
              String testModuleAndTestName = matcher.group(1);
              if (testModuleAndTestName.contains(" ")) {
                String testModuleName = testModuleAndTestName.substring(0, testModuleAndTestName.indexOf(' '));
                testModules.add(testModuleName);
              } else {
                testModules.add(testModuleAndTestName.trim());
              }
            }
          }
        }
        reader.close();
        inputStream.close();
      }
    }
    jar.close();
    return testModules;
  }
}
