package com.netflix.spinnaker.halyard.deploy.services.v1

import org.springframework.mock.web.MockMultipartFile
import spock.lang.Specification

import java.nio.charset.Charset

class RequestGenerateServiceSpec extends Specification {
    def "Parse valid deployment configuration"() {
        given:
        def generate = new RequestGenerateService(null, null, null, null)
        def content = """
name: default
version: 2.5.2
providers:
  appengine:
    enabled: false
    accounts: []
  aws:
    enabled: false
    accounts: []
    bakeryDefaults:
      baseImages: []
    defaultKeyPairTemplate: '{{name}}-keypair'
    defaultRegions:
    - name: us-west-2
    defaults:
      iamRole: BaseIAMRole
"""
        def file = new MockMultipartFile("config", content.getBytes(Charset.defaultCharset()))


        when:
        def deploymentConfig = generate.parseDeploymentConfiguration(file)

        then:
        deploymentConfig != null
    }

//    def "Generate valid config from deployment configuration"() {
//        given:
//        def generate = new RequestGenerateService(null, null, null, null)
//        def deploymentConfig = new DeploymentConfiguration()
//                .setName("default")
//                .setVersion("1.14.2")
//        when:
//        generate.writeHalConfig()
//    }
}
