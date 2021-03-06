/*
 * Copyright 2017 ~ 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.devops.ci.provider;

import com.wl4g.devops.common.bean.ci.Project;
import com.wl4g.devops.common.bean.ci.Task;
import com.wl4g.devops.common.bean.ci.TaskDetail;
import com.wl4g.devops.common.bean.scm.AppInstance;

import java.util.List;

/**
 * Spring boot executable deployments provider.
 *
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0 2019年5月25日
 * @since
 */
public class SpringbootExecutableJarDeployProvider extends BasedDeployProvider {

    public SpringbootExecutableJarDeployProvider(Project project,
                                                 String path, String url, String branch, String alias, String tarPath, List<AppInstance> instances, Task task, Task refTask,
                                                 List<TaskDetail> taskDetails) {
        super(project, path, branch, alias, instances, task, refTask, taskDetails);
    }

    @Override
    public void execute() throws Exception {

    }

}