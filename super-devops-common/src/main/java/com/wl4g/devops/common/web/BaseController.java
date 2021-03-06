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
package com.wl4g.devops.common.web;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.wl4g.devops.common.utils.web.WebUtils2;

/**
 * Based abstract controller
 * 
 * @author wangl.sir
 * @version v1.0 2019年1月10日
 * @since
 */
public abstract class BaseController {

	/**
	 * SpringMVC controller redirection prefix.
	 */
	final public static String REDIRECT_PREFIX = "redirect:";

	final protected Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	protected Validator validator;

	/**
	 * Write response JSON message
	 * 
	 * @param response
	 * @param json
	 * @throws IOException
	 */
	protected void writeJson(HttpServletResponse response, String json) throws IOException {
		WebUtils2.writeJson(response, json);
	}

	/**
	 * Output message
	 * 
	 * @param response
	 * @param status
	 * @param contentType
	 * @param body
	 * @throws IOException
	 */
	protected void write(HttpServletResponse response, int status, String contentType, byte[] body) throws IOException {
		WebUtils2.write(response, status, contentType, body);
	}

}