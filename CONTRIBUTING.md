# Contributing

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

Following command will start fresh Jenkins instance with current plugin installed.

```bash
  $ mvn hpi:run
```

Jenkins will be started on `0.0.0.0:8080` address. Please make sure 8080 port is not in use before running the command.

Alternatively, other port can be specified by adding a parameter:
``` bash
   $ mvn hpi:run -Djetty.port=8090
```
