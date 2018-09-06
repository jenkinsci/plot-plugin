buildPlugin(platforms: ['linux'])

node {
    stage ('post-build') {
        // no need to execute tests as they were executed already
        sh "mvn -P enable-jacoco verify jacoco:prepare-agent jacoco:report"
        sh "curl -s https://codecov.io/bash | bash -s - -K"
    }
}
