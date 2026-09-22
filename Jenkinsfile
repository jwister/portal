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
      // ================== 自动化部署与CDN刷新 ==================
      // 1. 在 Jenkins 凭证中配置好 aliyun-ak-id 和 aliyun-ak-secret
      // 2. 将 WATCHTOWER_TOKEN 替换为您在服务器端配置的真实 Token (或者配在 Jenkins 凭证里)
      // 3. 将 your-cdn-domain.com 替换为真实的 CDN 域名
      withCredentials([
        string(credentialsId: 'aliyun-ak-id', variable: 'ALIYUN_AK_ID'),
        string(credentialsId: 'aliyun-ak-secret', variable: 'ALIYUN_AK_SECRET')
      ]) {
        sh '''
          echo "触发 Watchtower 拉取新镜像并重启服务..."
          # 请替换这里的 watchtower_token 为您服务器上的真实 TOKEN
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
    }
    always {
      sh 'docker image prune --force || true'
    }
  }
}
