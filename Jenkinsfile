buildPlugin(platforms: ['linux'])

node {
    stage("Upload coverage") {
        infra.checkout(params.containsKey('repo') ? params.repo : null)
        infra.runWithMaven("mvn -P enable-jacoco test jacoco:prepare-agent jacoco:report")
        sh "curl -s https://codecov.io/bash | bash -s - -K"
    }
}
