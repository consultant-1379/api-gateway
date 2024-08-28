pipeline {
    agent {
        node {
            label params.SLAVE
        }
    }

    parameters {
        string(name: 'SETTINGS_CONFIG_FILE_NAME', defaultValue: 'maven.settings.eso')
        string(name: 'ARMDOCKER_CONFIG_FILE_NAME', defaultValue: 'armdocker.login.config')
        string(name: 'KUBERNETES_CONFIG_FILE_NAME', defaultValue: 'kube.config.hoff007')
        string(name: 'BOB2_VERSION', defaultValue: '1.7.0-5')
    }

    environment {
        bob2 = "docker run --rm " +
                '--workdir "`pwd`" ' +
                '--env RELEASE=${RELEASE} ' +
                '--env API_TOKEN=${ARTIFACTORY_CREDS_PSW} ' +
                "-v ${env.WORKSPACE}/.docker/dockerconfig.json:/root/.docker/config.json " +
                "-w `pwd` " +
                "-v \"`pwd`:`pwd`\" " +
                "-v /var/run/docker.sock:/var/run/docker.sock " +
                "armdocker.rnd.ericsson.se/sandbox/adp-staging/adp-cicd/bob.2.0:${BOB2_VERSION}"

        DOCKER_CREDS = credentials('armdocker-so-login')
        ARTIFACTORY_CREDS = credentials('artifactory-esoadm-login')
        HELM_CREDS = credentials('armhelm.so.login')
        HELM_CMD = "docker run --rm -v ${env.WORKSPACE}/.kube/config:/root/.kube/config -v ${env.WORKSPACE}/helm-target:/root/.helm -v ${env.WORKSPACE}:${env.WORKSPACE} linkyard/docker-helm:2.10.0"
        CHART_REPO = 'https://arm.rnd.ki.sw.ericsson.se/artifactory/proj-so-gs-all-helm'
        CHART_NAME = 'eric-eo-api-gateway'

    }

    stages {
        stage('Inject Settings.xml File') {
            steps {
                configFileProvider([configFile(fileId: "${env.SETTINGS_CONFIG_FILE_NAME}", targetLocation: "${env.WORKSPACE}")]) {
                }
                // Inject docker config json
                configFileProvider([configFile(fileId: "${env.ARMDOCKER_CONFIG_FILE_NAME}", targetLocation: "${env.WORKSPACE}/.docker/")]) {
                }
                // Inject Kubernetes configuration file
                configFileProvider([configFile(fileId: "${env.KUBERNETES_CONFIG_FILE_NAME}", targetLocation: "${env.WORKSPACE}/.kube/")]) {
                }
            }
        }

        stage('Clean') {
            steps {
                sh "${bob2} clean"
                sh 'git clean -xdff --exclude=.m2 --exclude=.sonar --exclude=settings.xml --exclude=.docker --exclude=.kube'
            }
        }

        stage('Init') {
            steps {
                sh "${bob2} init"
                archiveArtifacts 'artifact.properties'
            }
        }

        stage('Lint') {
            steps {
                sh "${bob2} lint"
            }
        }
        stage('Build package and execute tests') {
            steps {
                sh "${bob2} build"
            }
        }
        stage('ADP Helm Design Rule Check') {
            steps {
                sh "${bob2} adp-helm-dr-check"
                archiveArtifacts 'design-rule-check-report.*'
            }
        }
        stage('Build image and chart') {
            steps {
                sh "${bob2} image"
                sh "${bob2} package"
            }
        }

        stage('SonarQube full analysis') {
            when {
                expression { params.RELEASE == "true" }
            }
            steps {
                sh "${bob2} sonar"
            }
        }

        stage('Push image and chart (Using bob)') {
            when {
                expression { params.RELEASE == "true" }
            }
            steps {
                sh "${bob2} package"
                echo "bob package was successful"
                sh "${bob2} publish"
                echo "bob publish was successful"
            }
        }
    }
    post {
        always {
            archive "**/target/surefire-reports/*"
            junit '**/target/surefire-reports/*.xml'
            step([$class: 'JacocoPublisher'])
        }
    }
}
