#!groovy
@Library('pipeline@3.x') _

def MS_TEAMS_WEBHOOKS_FAILED_BUILD_CHANNEL = ['https://outlook.office.com/webhook/716a7819-17a2-494a-9f12-0008a588fe93@a918f95e-e7f2-4d57-9955-ebc85b9929d2/IncomingWebhook/12d8c4631dc24b8ab665d8b41e16af26/7d8fe77c-ec8a-4e04-8b45-c2df8572ad21']
def MS_TEAMS_WEBHOOKS_SUCCESSFUL_BUILD_CHANNEL = ['https://outlook.office.com/webhook/716a7819-17a2-494a-9f12-0008a588fe93@a918f95e-e7f2-4d57-9955-ebc85b9929d2/IncomingWebhook/12d8c4631dc24b8ab665d8b41e16af26/7d8fe77c-ec8a-4e04-8b45-c2df8572ad21']


def isDeployBranch(environmentForceDeploy) {
    return (
        env.BRANCH_NAME == "master" 
        || environmentForceDeploy 
        || params.FORCE_DEPLOY_ALL
    ).toBoolean()
}

class ElasticsearchClusters {
    final static String INTEG_MAIN = "elasticsearch_search_integ"
    final static String STAGING_MAIN = "elasticsearch_search_staging"
    final static String STAGING_BACKUP = "elasticsearch_search_staging2"
    final static String PRODUCTION_MAIN = "elasticsearch_search_production"
    final static String PRODUCTION_BACKUP = "elasticsearch_search_production2"
}

class SearchEnvironmentClassNames {
    final static String STAGING = "streamate_apiserver_staging"
    final static String PRODUCTION = "streamate_apiserver_production"
}

class BuildState {
    // TODO: typo.. wtf?
    final static String PROJECT_NAME = "ml-grcp-rescoring-esplugin"
    final static String PACKAGE_TO_CHANGE_ES_SERVERS = "search.webservice"
}

def getSearchHostToReleaseTo(String searchClassName) {
    echo("pulling first host associated with search class: ${searchClassName}")
    def doggyCommandOutput = sh(
            script: "doggy -- sinfo -c ${searchClassName} --unique-hostnames",
            returnStdout: true
    )?.trim()
    echo("doggy output:\n ${doggyCommandOutput}")
    String firstSearchHost = doggyCommandOutput?.split("\n")[0]?.trim()
    assert firstSearchHost : "unexpectedly empty search host!"
    echo("found host! class: ${searchClassName} host: ${firstSearchHost}")
    return firstSearchHost
}

def migrateServerAndReleasePlugin(String searchClassName, String esClusterClassName) {
    String searchHost = getSearchHostToReleaseTo(searchClassName)
    String doggyCommandOutput = sh(
            script: "doggy -- sinfo -c ${esClusterClassName} --unique-hostnames",
            returnStdout: true
    )?.trim()
    echo("doggy output:\n ${doggyCommandOutput}")
    String esClusterServer = doggyCommandOutput?.split("\n")[0]?.trim()
    assert esClusterServer : "unexpected empty result from doggy for es cluster: ${esClusterClassName}"
    String esClusterNameInSearch = esClusterServer?.trim()?.split(/\d/)[0]?.trim()
    assert esClusterNameInSearch : "unexpected empty esClusterName for search: $esClusterNameInSearch"

    echo("setting up release for esClusterClassName ${esClusterClassName} using cluster name in search ${esClusterNameInSearch}")
    def reloadCommandOutput = sh(
            script: "doggy -- reload --hostname=${searchHost} ${BuildState.PACKAGE_TO_CHANGE_ES_SERVERS} -- ${esClusterNameInSearch}",
            returnStdout: true
    )?.trim()
    echo("doggy output:\n ${reloadCommandOutput}")
    assert reloadCommandOutput?.replace('\n', ' ')?.toLowerCase() ==~ /.*reload\s+success.*/ : "failed to switch search to es cluster: ${esClusterNameInSearch}"
    sleep(10L)
    doggyInstall systemClass: "${esClusterClassName}", doggyPackage: "${BuildState.PROJECT_NAME}"
}

def resetSearchEsClusterToDefaultState(String searchClassName) {
    String searchHost = getSearchHostToReleaseTo(searchClassName)
    echo("resetting search cluster on host ${searchHost}")
    def reloadCommandOutput = sh(
            script: "doggy -- reload --hostname=${searchHost} ${BuildState.PACKAGE_TO_CHANGE_ES_SERVERS} -- reset",
            returnStdout: true
    )?.trim()
    echo("doggy output:\n ${reloadCommandOutput}")
    assert reloadCommandOutput?.replace("\n", ' ')?.toLowerCase() ==~ /.*reload\s+success.*/ : "failed to reset search's ES cluster"
}

pipeline {
    agent { label "dockerbuildkit" }

    // only keep 20 builds per branch (and their artifacts)
    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20'))
        ansiColor('xterm')
    }

    parameters {
        booleanParam(
            defaultValue: false,
            description: "Force deploy to integ",
            name: 'FORCE_DEPLOY_INTEG'
        )

        booleanParam(
            defaultValue: false,
            description: "Force deploy to staging",
            name: 'FORCE_DEPLOY_STAGING'
        )

        booleanParam(
            defaultValue: false,
            description: "Force Deploy to production",
            name: 'FORCE_DEPLOY_PRODUCTION'
        )

        booleanParam(
            defaultValue: false,
            description: "Force deploy to all environments",
            name: 'FORCE_DEPLOY_ALL'
        )
    }

    stages {
        stage('tests') {
            options {
                lock("${BuildState.PROJECT_NAME}_DockerLock")
            }
            steps {
                script {
                    sh "env"
                    sh "docker-compose --version"
                    sh 'rm -rf docker-stats-data || true'
                    sh 'docker stats > docker-stats-data & jobs -p %1 > please_kill_me.pid'
                    sh "docker-compose rm -fsv"
                    sh "DOCKER_BUILDKIT=1 docker-compose build"
                    sh "docker-compose up -d --force-recreate es"
                    sh "sleep 10"
                    sh "docker-compose ps"
                    retry(3) {
                        sh "docker-compose run tests"
                    }
                }
            }
            post {
                failure {
                    sh "docker-compose logs contextual-item-ranker"
                    sh "docker-compose logs item-ranker"
                    sh "docker-compose logs es"
                }
                cleanup {
                    sh 'kill $(cat please_kill_me.pid) || true'
                    sh "cat docker-stats-data"
                    sh "rm -f docker-stats-data"
                    sh "docker-compose rm -fsv || true"
                }
            }
        }
        stage('benchmarking') {
            options {
                lock("${BuildState.PROJECT_NAME}_DockerLock")
            }
            steps {
                script {
                    sh 'rm -rf docker-stats-data || true'
                    sh 'docker stats > docker-stats-data & jobs -p %1 > please_kill_me.pid'
                    sh "docker-compose rm -fsv"
                    sh "DOCKER_BUILDKIT=1 docker-compose build"
                    sh "docker-compose up -d --force-recreate es"
                    sh "sleep 10"
                    sh "docker-compose ps"
                    sh "docker-compose run benchmarking"
                }
            }
            post {
                failure {
                    sh "docker-compose logs contextual-item-ranker"
                    sh "docker-compose logs item-ranker"
                    sh "docker-compose logs es"
                }
                cleanup {
                    sh 'kill $(cat please_kill_me.pid) || true'
                    sh "cat docker-stats-data"
                    sh "rm -f docker-stats-data"
                    sh "docker-compose rm -fsv || true"
                }
            }
        }
        stage('build image files') {
            steps {
                script {
                    (DOCKER_IMAGE, DOCKER_BRANCH_IMAGE) = buildDockerImage(
                        target: "plugin_files",
                        dockerContext: "./es/",
                        dockerFile: "./es/Dockerfile",
                        additionalTags: [env.BRANCH_NAME == 'master' ? "latest" : env.BRANCH_NAME]
                    )

                    String containerName = sh(
                            script: "docker create ${DOCKER_IMAGE} -- bash",
                            returnStdout: true
                    ).trim()
                    sh "docker cp ${containerName}:/mlrescore-v2.zip ."

                    // Publish the release docker image to the docker repository
                    pushToDockerRegistry(
                        dockerImage: DOCKER_IMAGE
                    )

                    pushToDockerRegistry(
                        dockerImage: DOCKER_BRANCH_IMAGE
                    )

                    pushToTarpit(
                        osVersionOverride: 'any',
                        doggyPackage: BuildState.PROJECT_NAME,
                        filesToCopy: [
                            "mlrescore-v2.zip",
                            "doggy-hooks"
                        ]
                    )
                }
            }
        }
        stage('Deploy Integ') {
            options {
                lock("${BuildState.PROJECT_NAME}_Integ")
            }
             when {
                 expression {
                     return isDeployBranch(params.FORCE_DEPLOY_INTEG)
                 }
             }
            steps {
                doggyInstall(
                        systemClass: ElasticsearchClusters.INTEG_MAIN,
                        doggyPackage: BuildState.PROJECT_NAME
                )
            }
        }
        stage('Deploy Staging') {
            options {
                lock("${BuildState.PROJECT_NAME}_Staging")
            }
             when {
                 expression {
                     return isDeployBranch(params.FORCE_DEPLOY_STAGING)
                 }
             }
            steps {
                script {
                    migrateServerAndReleasePlugin(
                            SearchEnvironmentClassNames.STAGING,
                            ElasticsearchClusters.STAGING_MAIN
                    )
                    migrateServerAndReleasePlugin(
                            SearchEnvironmentClassNames.STAGING,
                            ElasticsearchClusters.STAGING_BACKUP
                    )
                    resetSearchEsClusterToDefaultState(SearchEnvironmentClassNames.STAGING)
                }
            }
        }
        stage('Deploy Production') {
            options {
                lock("${BuildState.PROJECT_NAME}_Production")
            }
            when {
                expression {
                    return isDeployBranch(params.FORCE_DEPLOY_PRODUCTION)
                }
            }
            steps {
                script {
                    migrateServerAndReleasePlugin(
                            SearchEnvironmentClassNames.PRODUCTION,
                            ElasticsearchClusters.PRODUCTION_MAIN
                    )
                    migrateServerAndReleasePlugin(
                            SearchEnvironmentClassNames.PRODUCTION,
                            ElasticsearchClusters.PRODUCTION_BACKUP
                    )
                    resetSearchEsClusterToDefaultState(SearchEnvironmentClassNames.PRODUCTION)
                }
            }
        }
    }

    post {
        // The cleanup step gets ran regardless of the success or failure of the above steps.  Do all cleanup tasks here
        cleanup {
            cleanDockerImages()
            cleanWs()
        }

        // If we have successfully deployed, send a Microsoft teams message to the successful build deploys channel
        success {
            script {
                msTeamsMessage(
                    webhookUrls: MS_TEAMS_WEBHOOKS_SUCCESSFUL_BUILD_CHANNEL,
                    color: "GREEN",
                    message: "Build SUCCESS<br/><a href=\"${env.RUN_DISPLAY_URL}\">${env.RUN_DISPLAY_URL}</a>",
                    status: "Success"
                )
            }
        }

        // If any of the above steps failed send a notification email and send a message to the build failures room
        failure {
            script {
                String status = "FAILED: Job ${BuildState.PROJECT_NAME} [${currentBuild.externalizableId}]"
                String sendTo = ""
                String message = """
                    <p>FAILED: Job ${BuildState.PROJECT_NAME} [${currentBuild.externalizableId}] has failed its build</p>
                    <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${BuildState.PROJECT_NAME} [${currentBuild.externalizableId}]</a>&QUOT;</p>
                """.stripIndent()

                emailext(
                    subject: status,
                    mimeType: "text/html",
                    body: message,
                    to: sendTo,
                    recipientProviders: [[$class: 'DevelopersRecipientProvider']]

                )
                msTeamsMessage(
                    webhookUrls: MS_TEAMS_WEBHOOKS_FAILED_BUILD_CHANNEL,
                    color: "RED",
                    message: message,
                    status: status
                )
            }
        }
    }
}
