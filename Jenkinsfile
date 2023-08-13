/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(
  // Container agents start faster and are easier to administer
  useContainerAgent: true,
  // Show failures on all configurations
  failFast: false,
  // Test Java 11, 17, and 21
  configurations: [
    [platform: 'linux',   jdk: '17', jenkins: '2.375'],
    [platform: 'linux',   jdk: '11', jenkins: '2.361.4'],
    [platform: 'linux',   jdk: '21', jenkins: '2.414'],
    [platform: 'windows', jdk: '11']
  ]
)
