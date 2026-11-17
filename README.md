<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Apache Maven Reporting Executor

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven.reporting/maven-reporting-exec.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.apache.maven.reporting/maven-reporting-exec)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/org/apache/maven/reporting/maven-reporting-exec/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/org/apache/maven/reporting/maven-reporting-exec/README.md)
[![Verify](https://github.com/apache/maven-reporting-exec/actions/workflows/maven-verify.yml/badge.svg)](https://github.com/apache/maven-reporting-exec/actions/workflows/maven-verify.yml)

Turns the report plugins in a POM's `<reporting>` section into configured `MavenReport` instances. The
[Maven Site Plugin](https://maven.apache.org/plugins/maven-site-plugin/) calls it during `mvn site`: it resolves each
report plugin's version, merges its configuration with `build/pluginManagement`, loads it in its own class realm and
runs any forked lifecycle a report needs. Plugins that render reports themselves can call `MavenReportExecutor` the
same way.

Requires Java 8 or later; built against the Maven 3.9 core API.

- Documentation: <https://maven.apache.org/shared/maven-reporting-exec/>
- Issues: <https://github.com/apache/maven-reporting-exec/issues>

## Build

```shell
mvn verify
```

This also runs the integration tests under `src/it`, which drive report plugins through the executor, including
forked lifecycles and report plugins built on different Doxia versions. Run one of them with
`mvn verify -Dinvoker.test=forked-lifecycle`.

## Contributing

Send pull requests against `master`; discuss larger changes on the
[developer mailing list](https://maven.apache.org/mailing-lists.html) first. Spotless enforces the
[Maven code style](https://maven.apache.org/developers/conventions/code.html) during the build; run
`mvn spotless:apply` before you push. Pull request titles become entries in the release notes.
