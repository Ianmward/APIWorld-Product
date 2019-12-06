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
    - name: mg-jenkins
      image: docker.devopsinitiative.com/mg-jenkins:10.5.0.3
      command:
      - cat
      tty: true
      volumeMounts:
      - mountPath: /var/run/docker.sock
        name: docker-sock-volume
    - name: maven
      image: maven:3.3.9-jdk-8-alpine
      command:
      - cat
      tty: true
    - name: docker
      image: docker.devopsinitiative.com/mg-jenkins:10.5.0.3-root
      securityContext:
        runAsUser: 0
        fsGroup: 0
      command:
      - cat
      tty: true
      volumeMounts:
      - mountPath: /var/run/docker.sock
        name: docker-sock-volume
    volumes:
    - name: docker-sock-volume
      hostPath:
        # location on host
        path: /var/run/docker.sock
        # this field is optional
        type: File
    alwaysPullImage: true
    imagePullSecrets:
    - name: regcred
  """
    }
  }
  stages {
        stage('Startup') {
            failFast true
            parallel {
                stage('Get Version Number') {
                    steps {
                        echo 'Get Version Number'
                        load 'versionInput.groovy'
                    }
                }
                stage('Setup') {
                    steps {
                        container("docker") {
                            echo "Cleanup"
                            sh '''
docker ps
docker stop productservicems || true
docker rm productservicems || true
docker stop productmg || true
docker rm productmg || true
'''
                        }
                    }
                }
            }
        }
        stage('Build') {
            failFast true
            parallel {
                stage('Microservice') {
                    steps {
                        container('maven') {
                            echo 'Build Project'
                            sh '''
if [[ -z "$VERSION" ]]; then
  VERSION=ci
fi
echo Version is: $VERSION
'''
                            echo "Compile Microservice"
                            sh 'mvn compile'
                            echo "Package the Microservice"
                            sh 'mvn package'
                            sh '''
echo "Move Package for Docker Build"
cp $WORKSPACE/target/product-service-0.0.1.jar $WORKSPACE/service.jar
'''
                        }
                    }
                }
                stage('Microgateway') {
                    steps {
                        container('mg-jenkins') {
                          echo 'Build Project'
                          sh '''
#Modify Alias depending on stage

if [ $GIT_BRANCH = "staging" ]; then
  sed -i \'s/\\[gateway\\]/apiworldref\\:5555/g\' microgateway/config.yml
  sed -i \'s/\\[microservice\\]/localhost\\:8090/g\' microgateway/config.yml
  exit
fi

if [ $GIT_BRANCH = "master" ]; then
  sed -i \'s/\\[gateway\\]/apiworldref\\:5555/g\' microgateway/config.yml
  sed -i \'s/\\[microservice\\]/localhost\\:8090/g\' microgateway/config.yml   
  exit
fi

#Else assume its a development branch and set accordingly

sed -i \'s/\\[gateway\\]/apiworldbuild\\:5555/g\' microgateway/config.yml
sed -i \'s/\\[microservice\\]/apiworldbuild\\:8090/g\' microgateway/config.yml
'''
                          sh '''
WORKSPACE=`pwd`
cd /opt/softwareag/Microgateway
./microgateway.sh createDockerFile --docker_dir . -p 9090 -a $WORKSPACE/microgateway/Product.zip -dof ./Dockerfile -c $WORKSPACE/microgateway/config.yml
cp Dockerfile $WORKSPACE/microgateway/Dockerfile
mkdir $WORKSPACE/microgateway/tmp-docker
cp tmp-docker/* $WORKSPACE/microgateway/tmp-docker
'''
                        }
                    }
                }

            }
        }
        stage('Containerise') {
            steps {
                container('docker') {
                    echo "Microservice"
                    sh '''
#Containerize Microservice

docker build -t productservice:$VERSION --build-arg PORT=8090 --build-arg JAR_FILE=service.jar .
#docker tag productservice:$VERSION productservice:$VERSION
'''
                    echo "Microgateway"
                    sh '''
MICROGW_DIR=/opt/softwareag/Microgateway
cd /opt/softwareag/Microgateway
cp $WORKSPACE/microgateway/Dockerfile ./Dockerfile
mkdir tmp-docker
cp $WORKSPACE/microgateway/tmp-docker/* tmp-docker
docker build -t productmg:$VERSION .
'''
                }
            }
        }
        stage('Deployment') {
            failFast true
            parallel {
                stage('Start MicroGW') {
                    steps {
                        container('docker') {
                            sh '''
#Run MicroGateway Container
docker run --rm --name productmg -d -p 9090:9090 --net=host productmg:$VERSION
'''
                        }
                    }
                }
                stage('Start MicroSvc') {
                    steps {
                        container('docker') {
                            sh '''
#Run the container read for testing
docker run --rm --name productservicems -d -p 8090:8090 productservice:$VERSION
'''
                        }
                    }
                }
            }
        }
        stage('Test Operational') {
            failFast true
            parallel {
                stage('Test MicroGW Operational') {
                    steps {
                        container('docker') {
                            sh '''
#Run the container read for testing
docker run --rm --name productservicems -d -p 8090:8090 productservice:$VERSION
'''
                        }
                    }
                }
                stage('Test MicroSvc Operational') {
                    steps {
                        container('docker') {
                            sh '''
#Run MicroGateway Container
docker run --rm --name productmg -d -p 9090:9090 --net=host productmg:$VERSION
'''
                        }
                    }
                }
            }
        }
    }
}