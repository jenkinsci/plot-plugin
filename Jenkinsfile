def platform = 'linux'

buildPlugin(platforms: [platform], tests: [skip: true])

node(platform) {
    stage("Calculate & upload coverage") {
        infra.checkout(params.containsKey('repo') ? params.repo : null)
        infra.runWithMaven("mvn -P enable-jacoco test jacoco:prepare-agent jacoco:report")
        withCredentials([string(credentialsId: 'CODECOV_TOKEN', variable: 'CODECOV_TOKEN')]) {
            sh "curl -s https://codecov.io/bash | bash -s - -K"
        }
    }
}
