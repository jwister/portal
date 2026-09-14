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
        sh '''
          git submodule sync --recursive
          git submodule update --init --recursive
          test -f frontend/package.json
          echo "Frontend commit: $(git -C frontend rev-parse HEAD)"
        '''
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
          find backend/target -maxdepth 1 -type f -name 'ztoken-portal-*.jar' -print
          test -n "$(find backend/target -maxdepth 1 -type f -name 'ztoken-portal-*.jar' -print -quit)"
        '''
      }
    }
    stage('Build') {
      steps {
        retry(3) {
          sh '''
            docker build --pull \
              --tag "$IMAGE_REPOSITORY:$IMAGE_VERSION" \
              --tag "$IMAGE_REPOSITORY:$GIT_SHA" \
              .
          '''
        }
      }
    }
    stage('Push') {
      steps {
        script {
          docker.withRegistry('https://index.docker.io/v1/', DOCKERHUB_CREDENTIALS) {
            sh '''
              push_with_retry() {
                image="$1"
                attempt=1
                max_attempts=5
                while true; do
                  echo "Pushing $image (attempt $attempt/$max_attempts)"
                  if docker push "$image"; then
                    echo "Push succeeded: $image"
                    return 0
                  fi
                  if [ "$attempt" -ge "$max_attempts" ]; then
                    echo "Push failed after $max_attempts attempts: $image"
                    return 1
                  fi
                  delay=$((attempt * 15))
                  echo "Push failed; retrying in ${delay}s..."
                  sleep "$delay"
                  attempt=$((attempt + 1))
                done
              }
              push_with_retry "$IMAGE_REPOSITORY:$IMAGE_VERSION"
              push_with_retry "$IMAGE_REPOSITORY:$GIT_SHA"
            '''
          }
        }
      }
    }
  }
  post {
    success {
      sh '''
        curl --fail --silent --show-error --get \
          --connect-timeout 5 \
          --max-time 15 \
          --data-urlencode "t=推送成功：$IMAGE_REPOSITORY:$IMAGE_VERSION" \
          --data-urlencode "m=镜像名称及版本号：$IMAGE_REPOSITORY:$IMAGE_VERSION，Git：$GIT_SHA" \
          'http://192.168.100.153:9997/n/t' \
          || echo 'Image notification failed; image publishing remains successful.'
      '''
    }
    always {
      sh 'docker image prune --force || true'
    }
  }
}
