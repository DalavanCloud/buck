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
package com.facebook.buck.util.environment;

import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.network.HostnameFetching;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultExecutionEnvironment implements ExecutionEnvironment {
  private static final String HARDWARE_PORT_WI_FI = "Hardware Port: Wi-Fi";
  private static final Pattern DEVICE_PATTERN = Pattern.compile("Device: (\\w*)");
  private static final Pattern SSID_PATTERN =
      Pattern.compile("Current Wi\\-Fi Network: (.*)$", Pattern.MULTILINE);
  private static final long  MEGABYTE = 1024L * 1024L;
  private final Platform platform;
  private final ProcessExecutor processExecutor;
  private final ImmutableMap<String, String> environment;
  private final Properties properties;

  public DefaultExecutionEnvironment(
      ProcessExecutor processExecutor,
      ImmutableMap<String, String> environment,
      Properties properties) {
    this.platform = Platform.detect();
    this.processExecutor = processExecutor;
    this.environment = environment;
    this.properties = properties;
  }

  @Override
  public String getHostname() {
    String localHostname;
    try {
      localHostname = HostnameFetching.getHostname();
    } catch (IOException e) {
      localHostname = "unknown";
    }
    return localHostname;
  }

  @Override
  public String getUsername() {
    return getenv("USER", getProperty("user.name", "<unknown>"));
  }

  @Override
  public int getAvailableCores() {
    return Runtime.getRuntime().availableProcessors();
  }

  @Override
  public long getTotalMemoryInMb() {
    OperatingSystemMXBean osBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    return osBean.getTotalPhysicalMemorySize() / MEGABYTE;
  }

  @Override
  public Platform getPlatform() {
    return platform;
  }

  @Override
  public Optional<String> getWifiSsid() throws InterruptedException {
    // TODO(royw): Support Linux and Windows.
    if (getPlatform().equals(Platform.MACOS)) {
      try {
        ProcessExecutor.Result allNetworksResult = this.processExecutor.launchAndExecute(
            ProcessExecutorParams.builder()
                .addCommand("networksetup", "-listallhardwareports")
                .build(),
            /* options */ ImmutableSet.of(ProcessExecutor.Option.EXPECTING_STD_OUT),
            /* stdin */ Optional.<String>absent(),
            /* timeOutMs */ Optional.<Long>absent(),
            /* timeOutHandler */ Optional.<Function<Process, Void>>absent());

        if (allNetworksResult.getExitCode() == 0) {
          String allNetworks = allNetworksResult.getStdout().get();
          Optional<String> wifiNetwork = parseNetworksetupOutputForWifi(allNetworks);
          if (wifiNetwork.isPresent()) {
            ProcessExecutor.Result wifiNameResult = this.processExecutor.launchAndExecute(
                ProcessExecutorParams.builder()
                    .addCommand("networksetup", "-getairportnetwork", wifiNetwork.get())
                    .build(),
                /* options */ ImmutableSet.of(ProcessExecutor.Option.EXPECTING_STD_OUT),
                /* stdin */ Optional.<String>absent(),
                /* timeOutMs */ Optional.<Long>absent(),
                /* timeOutHandler */ Optional.<Function<Process, Void>>absent());
            if (wifiNameResult.getExitCode() == 0) {
              return parseWifiSsid(wifiNameResult.getStdout().get());
            }
          }
        }
      } catch (IOException e) {
        return Optional.absent();
      }
    }
    return Optional.absent();
  }

  @Override
  public String getenv(String key, String defaultValue) {
    String value = environment.get(key);
    return value != null ? value : defaultValue;
  }

  @Override
  public String getProperty(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  @VisibleForTesting
  static Optional<String> parseNetworksetupOutputForWifi(String listAllHardwareOutput) {
    Iterable<String> lines = Splitter.on("\n")
        .trimResults()
        .omitEmptyStrings()
        .split(listAllHardwareOutput);

    boolean foundWifiLine = false;
    for (String line : lines) {
      if (line.equals(HARDWARE_PORT_WI_FI)) {
        foundWifiLine = true;
      } else if (foundWifiLine) {
        Matcher match = DEVICE_PATTERN.matcher(line);
        if (match.matches()) {
          return Optional.of(match.group(1));
        }
      }
    }
    return Optional.absent();
  }

  @VisibleForTesting
  static Optional<String> parseWifiSsid(String getAirportOutput) {
    Matcher match = SSID_PATTERN.matcher(getAirportOutput);
    if (match.find()) {
      return Optional.of(match.group(1));
    }
    return Optional.absent();
  }
}
