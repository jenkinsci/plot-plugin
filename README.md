# Plot plugin

This plugin provides generic plotting (or graphing) capabilities in Jenkins.

* see [Jenkins wiki](https://wiki.jenkins.io/display/JENKINS/Plot+Plugin) for detailed feature descriptions
* use [JIRA](https://issues.jenkins-ci.org/browse/JENKINS-43708?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20plot-plugin) to report issues / feature requests

## Master Branch

The master branch is the primary development branch.

## Contributing to the Plugin

New feature proposals and bug fix proposals should be submitted as
[pull requests](https://help.github.com/articles/creating-a-pull-request).
Fork the repository, prepare your change on your forked copy, and submit a pull request.
Your pull request will be evaluated by the [Cloudbees Jenkins job](https://ci.jenkins.io/job/Plugins/job/plot-plugin/).

Before submitting your pull request, please assure that you've added
a test which verifies your change. Tests help us assure that we're delivering a reliable
plugin, and that we've communicated our intent to other developers in
a way that they can detect when they run tests.


## Building the Plugin

```bash
  $ java -version # Need Java 1.8, earlier versions are unsupported for build
  $ mvn -version # Need a modern maven version; maven 3.2.5 and 3.5.0 are known to work
  $ mvn clean install
```

```bash
  $ mvn package # produces target/plot.hpi for manual installation 
```

## Running/testing plugin locally

```bash
  $ mvn hpi:run
```

Following command will start fresh Jenkins instance with current plugin installed.
Jenkins will be started on `0.0.0.0:8080` address. Please make sure 8080 port is not in use before running the command.

Alternatively, other port can be specified by adding a parameter:
``` bash
   $ mvn hpi:run -Djetty.port=8090
```

## Code style

[Checkstyle](http://checkstyle.sourceforge.net/) plugin is used to validate code style.
Check [checkstyle/checkstyle.xml](https://github.com/jenkinsci/plot-plugin/blob/master/checkstyle/checkstyle.xml) for more details

### Few important notes about the style:
**Indentation**

- Use spaces (tabs banned)
- 1 indent = 4 spaces

**Field naming convention**

- "hungarian" notation is banned
- ALL_CAPS for static final fields
- camelCase for naming (`_` tolerable in test names)

**Imports**

- `*` imports are banned

**General**

- line length = `100`
- method declaration parameters = `Align when multiline`
- use interfaces only as types (no interface for constants)