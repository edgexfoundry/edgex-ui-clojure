//
// Copyright (c) 2019 Intel Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

loadGlobalLibrary()

def image_amd64
def image_arm64

pipeline {
    agent {
        label 'centos7-docker-4c-2g'
    }

    stages {
        stage('LF Prep') {
            steps {
                edgeXSetupEnvironment()
                edgeXDockerLogin(settingsFile: env.MVN_SETTINGS)
                edgeXSemver 'init'
                script {
                    def semverVersion = edgeXSemver()
                    env.setProperty('VERSION', semverVersion)
                    sh 'echo $VERSION > VERSION'}
            }
        }

        stage('Build Docker') {
            parallel {
                stage('amd64') {
                    agent {
                        label 'centos7-docker-4c-2g'
                    }
                    steps {
                        script {
                            image_amd64 = docker.build('edgex-ui-clojure', '-f Dockerfile .')
                        }
                    }
                }

                stage('arm64') {
                    agent {
                        label 'ubuntu18.04-docker-arm64-4c-2g'
                    }
                    steps {
                        script {
                            image_arm64 = docker.build('edgex-ui-clojure', '-f Dockerfile .')
                        }
                    }
                }
            }
        }

        stage('SemVer Tag') {
            when { expression { edgex.isReleaseStream() } }
            steps {
                sh 'echo v${VERSION}'
                edgeXSemver('tag')
                edgeXInfraLFToolsSign(command: 'git-tag', version: 'v${VERSION}')
                edgeXSemver('push')
            }
        }

        stage('Docker Push') {
            when { expression { edgex.isReleaseStream() } }
            steps {
                script {
                    docker.withRegistry("https://${env.DOCKER_REGISTRY}:10004") {
                        //amd64
                        image_amd64.push("amd64")
                        image_amd64.push(env.GIT_COMMIT)
                        image_amd64.push(env.VERSION)
                        //arm64
                        image_arm64.push("arm64")
                        image_arm64.push("${env.GIT_COMMIT}-arm64")
                        image_arm64.push("${env.VERSION}-arm64")
                    }
                }
            }
        }
        
        stage('SemVer Bump') {
            when { expression { edgex.isReleaseStream() } }
            steps {
                edgeXSemver('bump patch')
                edgeXSemver('push')
            }
        }

    }

    post {
        always {
            edgeXInfraPublish()
        }
    }
}

def loadGlobalLibrary(branch = '*/master') {
    library(identifier: 'edgex-global-pipelines@master', 
        retriever: legacySCM([
            $class: 'GitSCM',
            userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-global-pipelines.git']],
            branches: [[name: branch]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[
                $class: 'SubmoduleOption',
                recursiveSubmodules: true,
            ]]]
        )
    ) _
}