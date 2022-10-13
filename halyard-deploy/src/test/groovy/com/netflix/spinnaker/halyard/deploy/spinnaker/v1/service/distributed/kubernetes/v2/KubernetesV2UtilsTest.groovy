package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2

import com.netflix.spinnaker.halyard.config.services.v1.FileService
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService
import spock.lang.Specification

class KubernetesV2UtilsTest extends Specification {

    private SecretSessionManager mockSecretSessionManager
    private CloudConfigResourceService mockCloudConfigResourceService
    private FileService mockFileService

    def setup() {
        mockSecretSessionManager = Stub(SecretSessionManager)
        mockCloudConfigResourceService = Stub(CloudConfigResourceService)
        mockFileService = Stub(FileService)
    }

    def "should return config file as Config Map"() {
        setup:
        String testNamespace = UUID.randomUUID().toString()
        String testClusterName = UUID.randomUUID().toString()
        String testName = UUID.randomUUID().toString()
        String testFileName = UUID.randomUUID().toString()
        byte[] testFileContent = UUID.randomUUID().toString().getBytes()
        KubernetesV2Utils.ResourceMountPair testFile = new KubernetesV2Utils.ResourceMountPair(testFileName, testFileContent)
        List<KubernetesV2Utils.ResourceMountPair> testFiles = Arrays.asList(testFile)

        KubernetesV2Utils kubernetesV2Utils = new KubernetesV2Utils(
                ConfigSource.Type.configMap,
                mockSecretSessionManager,
                mockCloudConfigResourceService,
                mockFileService
        )

        when:
        KubernetesV2Utils.ResourceSpec resourceSpec = kubernetesV2Utils.createResourceSpec(
                testNamespace,
                testClusterName,
                testName,
                testFiles
        )

        then:
        resourceSpec.type == ConfigSource.Type.configMap
        resourceSpec.resource.toString().contains("\"kind\": \"ConfigMap\"")
    }

    def "should return config file as Secret"() {
        setup:
        String testNamespace = UUID.randomUUID().toString()
        String testClusterName = UUID.randomUUID().toString()
        String testName = UUID.randomUUID().toString()
        String testFileName = UUID.randomUUID().toString()
        byte[] testFileContent = UUID.randomUUID().toString().getBytes()
        KubernetesV2Utils.ResourceMountPair testFile = new KubernetesV2Utils.ResourceMountPair(testFileName, testFileContent)
        List<KubernetesV2Utils.ResourceMountPair> testFiles = Arrays.asList(testFile)

        KubernetesV2Utils kubernetesV2Utils = new KubernetesV2Utils(
                ConfigSource.Type.secret,
                mockSecretSessionManager,
                mockCloudConfigResourceService,
                mockFileService
        )

        when:
        KubernetesV2Utils.ResourceSpec resourceSpec = kubernetesV2Utils.createResourceSpec(
                testNamespace,
                testClusterName,
                testName,
                testFiles
        )

        then:
        resourceSpec.type == ConfigSource.Type.secret
        resourceSpec.resource.toString().contains("\"kind\": \"Secret\"")
    }
}
