def label = "mypod-${UUID.randomUUID().toString()}"
podTemplate(label: label, 
  properties([
    parameters([
      string(name: 'VERSION', defaultValue: 'ci', description: 'The target VERSION' )
    ])
  ]),
  envVars: [
    secretEnvVar(key: 'DOCKER_USR', secretName: 'docker-store-cred', secretKey: 'username'),
    secretEnvVar(key: 'DOCKER_PSW', secretName: 'docker-store-cred', secretKey: 'password'),
    secretEnvVar(key: 'NEXUS_USR', secretName: 'docker-nexus-cred', secretKey: 'username'),
    secretEnvVar(key: 'NEXUS_PSW', secretName: 'docker-nexus-cred', secretKey: 'password')
  ],
  yaml: """
  kind: Pod
  metadata:
    labels:
      app: test
  spec:
    securityContext:
      runAsUser: 1724
      fsGroup: 1724
    containers:
    - name: productmg
      image: productmg:$VERSION
      command:
      - cat
      tty: true
    - name: productservicems
      image: productservice:$VERSION
      command:
      - cat
      tty: true
    - name: mg-jenkins
      image: docker.devopsinitiative.com/mg-jenkins:10.5.0.4
      command:
      - cat
      tty: true
      volumeMounts:
      - mountPath: /var/run/docker.sock
        name: docker-sock-volume
"""
) {
  node(label) {
    def commitId
		stage ('test-docker') {
            container('docker') {
                sh "echo $commitId"
                sh "id"
                sh "ls -l /var/run/docker.sock"
			}
		}
    stage ('Extract') {
        checkout scm
        commitId = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }
    def repository

    stage('Test Operational') {
        failFast true
        parallel {
            stage('Test MicroGW Operational') {
                steps {
                    container('mg-jenkins') {
                        sh '''
#Is MicroGW Operational
timeout 60 bash -c \'while [[ "$(curl -s -o /dev/null -w \'\'%{http_code}\'\' localhost:9090/gateway/Product/1.0/product)" != "200" ]]; do sleep 5; done\' || false
'''
                    }
                }
            }
            stage('Test MicroSvc Operational') {
                steps {
                    container('mg-jenkins') {
                        sh '''
#Are services operational
timeout 60 bash -c \'while [[ "$(curl -s -o /dev/null -w \'\'%{http_code}\'\' localhost:8090/product)" != "200" ]]; do sleep 5; done\' || false
'''
                    }
                }
            }
        }
    }
  }
}