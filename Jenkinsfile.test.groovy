pipeline {
  agent {
    kubernetes {
      label 'myPod'
      yaml """
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
    }
  }
  stages {
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