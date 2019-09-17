node {
    try {
        def commit_hash = sh(
            script: 'git rev-parse --short HEAD',
            returnStdout: true
        ).trim()
        def docker_image_unique = 'docker.io/armory/halyard-armory:operator-${commit_hash}'
        def docker_image_latest = 'docker.io/armory/halyard-armory:operator-latest'

        stage('Checking out code') {
            checkout scm
        }
        stage("Build") {
            sh './gradlew build && ./gradlew installDist'
        }
        stage("Build docker image ${docker_image_unique}") {
            sh 'docker build -t ${docker_image_unique} -f Dockerfile.slim .'
        }
        def branch = sh(
            script: 'git symbolic-ref --short HEAD',
            returnStdout: true
        ).trim()

        if (branch == 'gen-manifests') {
            stage("Push image") {
                sh 'docker tag ${docker_image_unique} ${docker_image_latest}'
                sh 'docker push ${docker_image_unique}'
                sh 'docker push ${docker_image_latest}'
            }
        }
        def props = [ docker_image: docker_image_unique ]
        writeFile file: 'build.properties', text: props.collect { k, v -> "${k}=${v}" }.join("\n")
        archiveArtifacts artifacts: 'build.properties'
    } catch (e) {
        slackSend color: 'danger', message: "Build of halyard (operator) failed: ${env.JOB_NAME} - ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
    }
}
