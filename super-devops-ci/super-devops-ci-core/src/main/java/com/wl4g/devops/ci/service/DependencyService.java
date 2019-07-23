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
package com.wl4g.devops.ci.service;

import com.wl4g.devops.common.bean.ci.Dependency;
import com.wl4g.devops.common.bean.ci.Task;

/**
 * @author vjay
 * @date 2019-05-22 11:33:00
 */
public interface DependencyService {

    public void build(Task task, Dependency dependency, String branch, Boolean success, StringBuffer result, boolean isDependency) throws Exception;

    public void rollback(Task task, Task refTask, Dependency dependency, String branch, Boolean success, StringBuffer result, boolean isDependency) throws Exception;

}