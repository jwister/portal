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
              --tag "$IMAGE_REPOSITORY:latest" \
              .
          '''
        }
      }
    }
    stage('Push') {
      steps {
        script {
          retry(3) {
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
              push_with_retry "$IMAGE_REPOSITORY:latest"
            '''
            }
          }
        }
      }
    }
  }
  post {
    success {
      // ================== 自动化部署与CDN刷新 ==================
      withCredentials([
        string(credentialsId: 'aliyun-ak-id', variable: 'ALIYUN_AK_ID'),
        string(credentialsId: 'aliyun-ak-secret', variable: 'ALIYUN_AK_SECRET')
      ]) {
        sh '''
          echo "触发 Watchtower 拉取新镜像并重启服务..."
          curl -H "Authorization: Bearer a3360059" http://95.41.67.11:8081/v1/update
          
          echo "等待容器重启完成 (15秒)..."
          sleep 15
          
          # 直接使用容器内已全局安装的 aliyun cli
          aliyun configure set \
            --profile jenkins-cdn \
            --mode AK \
            --region cn-hangzhou \
            --access-key-id "$ALIYUN_AK_ID" \
            --access-key-secret "$ALIYUN_AK_SECRET"
          
          echo "开始刷新 CDN 缓存..."
          aliyun cdn RefreshObjectCaches \
            --ObjectPath "https://ztoken.cc/" \
            --ObjectType "Directory"
        '''
      }

      // 部署和 CDN 刷新完成后，最后发送成功通知
      sh '''
        curl --fail --silent --show-error --get \
          --connect-timeout 5 \
          --max-time 15 \
          --data-urlencode "t=部署成功：$IMAGE_REPOSITORY:$IMAGE_VERSION" \
          --data-urlencode "m=镜像：$IMAGE_REPOSITORY:$IMAGE_VERSION，Git：$GIT_SHA，已完成容器热更与 CDN 刷新！" \
          'http://192.168.100.153:9997/n/t' \
          || echo 'Image notification failed; image publishing remains successful.'
      '''
    }
    always {
      sh 'docker image prune --force || true'
    }
  }
}
