buildPlugin(platforms: ['linux'])

node {
    stage("Upload coverage") {
        // no need to execute tests as they were executed already
        infra.runWithMaven("mvn -P enable-jacoco verify jacoco:prepare-agent jacoco:report")
        sh "curl -s https://codecov.io/bash | bash -s - -K"
    }
}
