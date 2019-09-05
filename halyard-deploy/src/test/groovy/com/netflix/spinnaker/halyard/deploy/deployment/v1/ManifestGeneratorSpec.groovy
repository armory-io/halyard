package com.netflix.spinnaker.halyard.deploy.deployment.v1

import com.fasterxml.jackson.databind.ObjectMapper
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class ManifestGeneratorSpec extends Specification {

    def "should generate a manifest list"() {
        given:
        def gen = new ManifestGenerator()
        gen.objectMapper = new ObjectMapper()
        gen.yamlParser = new Yaml()
        def manifestListMap = new HashMap()
        def list = new ManifestList()
        list.setDeploymentManifest("dep:\n  enabled: true")
        list.setServiceManifest("svc")
        list.getResourceManifests().add("rsc1")
        list.getResourceManifests().add("rsc2")
        manifestListMap.put("orca", list)
        when:
        def str = gen.manifestMapAsString(manifestListMap)
        then:
        str.contains("enabled: true")
    }
}
