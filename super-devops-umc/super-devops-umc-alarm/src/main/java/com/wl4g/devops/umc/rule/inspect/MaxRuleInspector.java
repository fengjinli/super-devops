/*
 * Copyright 2017 ~ 2025 the original author or authors. <wanglsir@gmail.com, 983708408@qq.com>
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
package com.wl4g.devops.umc.rule.inspect;

import com.wl4g.devops.umc.rule.AggregatorType;

/**
 * Max rule inspector
 * 
 * @author vjay
 * @date 2019-07-05 10:02:00
 */
public class MaxRuleInspector extends AbstractRuleInspector {

	@Override
	public AggregatorType aggregateType() {
		return AggregatorType.MAX;
	}

	@Override
	public boolean verify(InspectWrapper wrap) {
		if (wrap.getValues() == null || wrap.getValues().length <= 0) {
			return false;
		}

		double operatorResult = 0;
		for (double value : wrap.getValues()) {
			if (value > operatorResult) {
				operatorResult = value;
			}
		}
		return super.operate(wrap.getOperator(), operatorResult, wrap.getBaseline());
	}
}