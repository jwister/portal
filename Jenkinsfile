pipeline {
  agent any

  options {
    disableConcurrentBuilds()
  }

  environment {
    IMAGE_REPOSITORY = 'wenyou7/portal'
    DOCKERHUB_CREDENTIALS = 'dockerhub-token'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm

        script {
          env.GIT_SHA = sh(
            script: 'git rev-parse --short=12 HEAD',
            returnStdout: true
          ).trim()

          def buildTime = sh(
            script: 'TZ=Asia/Shanghai date +%Y%m%d%H%M%S',
            returnStdout: true
          ).trim()

          env.IMAGE_VERSION = "${buildTime}-b${env.BUILD_NUMBER}"
        }

        echo "Image version: ${env.IMAGE_VERSION}"
        echo "Git commit: ${env.GIT_SHA}"
      }
    }

    stage('Package Backend') {
      steps {
        sh '''
          mvn -f backend/pom.xml -B clean package -DskipTests

          echo 'Generated backend JAR files:'
          find backend/target -maxdepth 1 -type f \
            -name 'ztoken-portal-*.jar' -print

          test -n "$(find backend/target -maxdepth 1 -type f \
            -name 'ztoken-portal-*.jar' -print -quit)"
        '''
      }
    }

    stage('Build Image') {
      steps {
        sh '''
          docker build --pull \
            --tag "$IMAGE_REPOSITORY:$IMAGE_VERSION" \
            --tag "$IMAGE_REPOSITORY:$GIT_SHA" \
            .
        '''
      }
    }

    stage('Push Image') {
      steps {
        script {
          docker.withRegistry(
            'https://index.docker.io/v1/',
            DOCKERHUB_CREDENTIALS
          ) {
            sh '''
              docker push "$IMAGE_REPOSITORY:$IMAGE_VERSION"
              docker push "$IMAGE_REPOSITORY:$GIT_SHA"
            '''
          }
        }
      }
    }
  }

  post {
    always {
      sh 'docker image prune --force || true'
    }
  }
}
