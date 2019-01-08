/*
 * Copyright 2017 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.openstack;

import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OpenstackBakeryDefaults extends BakeryDefaults<OpenstackBaseImage> {

    private String authUrl;
    private String domainName;
    private String networkId;
    private String floatingIpPool;
    private String securityGroups;
    private String projectName;
    private String username;
    // TODO: probably not? this is "Set the default password your images will be baked with"
    private String password;
    private Boolean insecure;

}

