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
package com.wl4g.devops.ci.task;

import com.wl4g.devops.ci.provider.DockerBuildDeployProvider;
import com.wl4g.devops.common.bean.ci.Project;
import com.wl4g.devops.common.bean.ci.TaskDetail;
import com.wl4g.devops.common.bean.scm.AppInstance;
import org.springframework.util.Assert;

import java.util.List;

import static com.wl4g.devops.common.constants.CiDevOpsConstants.*;

/**
 * Maven assemble tar deployments task.
 *
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0 2019年5月24日
 * @since
 */
public class DockerBuildDeployTask extends AbstractDeployTask {

    private DockerBuildDeployProvider provider;
    private String path;
    private String tarPath;
    private Integer taskDetailId;

    public DockerBuildDeployTask(DockerBuildDeployProvider provider, Project project, String path, AppInstance instance,
                                 String tarPath, List<TaskDetail> taskDetails) {
        super(instance, project);
        this.provider = provider;
        this.path = path;
        this.tarPath = tarPath;
        Assert.notNull(taskDetails, "taskDetails can not be null");
        for (TaskDetail taskDetail : taskDetails) {
            if (taskDetail.getInstanceId().intValue() == instance.getId().intValue()) {
                this.taskDetailId = taskDetail.getId();
                break;
            }
        }
    }

    @Override
    public void run() {
        if (log.isInfoEnabled()) {
            log.info("Deploy task is starting ...");
        }

        Assert.notNull(taskDetailId, "taskDetailId can not be null");
        try {
            // Update status
            taskService.updateTaskDetailStatusAndResult(taskDetailId, TASK_STATUS_RUNNING, null);


            // Pull
            String s = provider.dockerPull(instance.getHost(), instance.getServerAccount(),"wl4g/"+project.getGroupName()+":master"/* TODO 要改成动态的 */, instance.getSshRsa());
            result.append(s).append("\n");
            // Restart
            String s1 = provider.dockerStop(instance.getHost(), instance.getServerAccount(),project.getGroupName(), instance.getSshRsa());
            result.append(s1).append("\n");
            // Remove Container
            String s2 = provider.dockerRemoveContainer(instance.getHost(), instance.getServerAccount(),project.getGroupName(), instance.getSshRsa());
            result.append(s2).append("\n");
            // Run
            String s3 = provider.dockerRun(instance.getHost(), instance.getServerAccount(),"docker run wl4g/"+project.getGroupName()+":master"/* TODO 要改成动态的 */, instance.getSshRsa());
            result.append(s3).append("\n");

            // Update status
            taskService.updateTaskDetailStatusAndResult(taskDetailId, TASK_STATUS_SUCCESS, result.toString());

        } catch (Exception e) {
            log.error("Deploy job failed", e);
            taskService.updateTaskDetailStatusAndResult(taskDetailId, TASK_STATUS_FAIL, result.toString() + "\n" + e.toString());
            //throw new RuntimeException(e);
        }

        if (log.isInfoEnabled()) {
            log.info("Deploy task is finished!");
        }
    }

}