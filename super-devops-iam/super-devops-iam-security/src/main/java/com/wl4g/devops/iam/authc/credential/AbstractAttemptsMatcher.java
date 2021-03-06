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
package com.wl4g.devops.iam.authc.credential;

import com.wl4g.devops.iam.common.authc.IamAuthenticationToken;
import com.wl4g.devops.iam.common.cache.EnhancedCache;
import com.wl4g.devops.iam.common.cache.EnhancedKey;
import com.wl4g.devops.iam.config.IamProperties;
import com.wl4g.devops.iam.handler.verification.Cumulators;
import com.wl4g.devops.iam.handler.verification.Cumulators.Cumulator;
import com.wl4g.devops.iam.handler.verification.Verification;
import static com.wl4g.devops.iam.common.utils.Securitys.*;
import static com.wl4g.devops.iam.common.utils.SessionBindings.*;
import static com.wl4g.devops.common.constants.IAMDevOpsConstants.*;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.util.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract custom attempts credential matcher
 * 
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0
 * @date 2018年11月29日
 * @since
 */
abstract class AbstractAttemptsMatcher extends IamBasedMatcher implements InitializingBean {

	/**
	 * EnhancedCache
	 */
	private EnhancedCache lockCache;

	/**
	 * Attempts accumulator
	 */
	private Cumulator matchCumulator;

	/**
	 * Apply CAPTCHA attempts accumulator.(Session-based)
	 */
	private Cumulator sessionMatchCumulator;

	/**
	 * Attempts CAPTCHA accumulator
	 */
	private Cumulator applyCaptchaCumulator;

	/**
	 * Attempts SMS accumulator
	 */
	private Cumulator applySmsCumulator;

	public AbstractAttemptsMatcher(Verification verification) {
		super(verification);
	}

	@Override
	public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
		IamAuthenticationToken tk = (IamAuthenticationToken) token;
		// Get preparatory signIn principal
		String principal = (String) tk.getPrincipal();

		// Fail limiter factor keys
		List<String> factors = createFactors(tk.getHost(), principal);
		Assert.notEmpty(factors, "'factors' must not be empty");

		Long cumulatedMaxFailCount = 0L;
		try {
			// Assertion needs to be locked
			cumulatedMaxFailCount = assertAccountLocked(principal, factors);

			// Assertion verification
			assertRequestVerify(tk, principal, factors);

		} catch (RuntimeException e) {
			cumulatedMaxFailCount = postFailureProcess(principal, factors);
			throw e;
		}

		// Credentials verification
		final boolean matched = doMatching(token, info, factors);
		if (matched) { // Matched successful processing
			postSuccessProcess(principal, factors);
		} else {
			cumulatedMaxFailCount = postFailureProcess(principal, factors);
		}

		if (log.isInfoEnabled()) {
			log.info("Credentials[{}], matched[{}], principal[{}], anyFailCount[{}]", String.valueOf(token.getCredentials()),
					matched, principal, cumulatedMaxFailCount);
		}

		// This is an accident.
		return matched;
	}

	/**
	 * Execution custom match
	 * 
	 * @param token
	 * @param info
	 * @param factors
	 * @return
	 */
	protected abstract boolean doMatching(AuthenticationToken token, AuthenticationInfo info, List<String> factors);

	/**
	 * After matched failure processing
	 * 
	 * @param principal
	 * @param factors
	 * @return
	 */
	protected Long postFailureProcess(String principal, List<String> factors) {

		// Cumulative increment of cache matching count by 1
		long matchCountMax = matchCumulator.accumulate(factors, 1);

		// Cumulative increase of session matching count by 1
		long sessioinMatchCountMax = sessionMatchCumulator.accumulate(factors, 1);

		if (log.isInfoEnabled()) {
			log.info("Principal {} matched failure accumulative limiter matchCountMax: {}, sessioinMatchCountMax: {}, factor: {}",
					principal, matchCountMax, sessioinMatchCountMax, factors);
		}

		// Record all accounts that have failed to log in in this session.
		List<String> failPrincipalFactors = getBindValue(KEY_FAIL_PRINCIPAL_FACTORS);
		if (null == failPrincipalFactors) {
			failPrincipalFactors = new ArrayList<>();
		}
		failPrincipalFactors.add(createPrincipalFactor(principal));
		bind(KEY_FAIL_PRINCIPAL_FACTORS, failPrincipalFactors);

		return matchCountMax;
	}

	/**
	 * After matched success processing
	 * 
	 * @param principal
	 * @param factors
	 */
	protected void postSuccessProcess(String principal, List<String> factors) {
		// Destroy all cumulators
		destroyCumulators(factors);

		if (log.isDebugEnabled()) {
			log.debug("Principal {} matched success, cleaning factors: {}", principal, factors);
		}

		// Clean all locker(if exists)
		factors.forEach(f -> {
			try {
				if (log.isInfoEnabled()) {
					log.info("Remove lock factor: {}", f);
				}
				lockCache.remove(new EnhancedKey(f));
			} catch (Exception e) {
				log.error("", e);
			}
		});
	}

	/**
	 * Assertion check if the account has been locked
	 * 
	 * @param principal
	 * @param factors
	 * @return
	 */
	protected Long assertAccountLocked(String principal, List<String> factors) {
		// Match failure lock max attempts
		int matchLockMaxAttempts = config.getMatcher().getFailFastMatchMaxAttempts();
		// Match failure delay time
		long matchLockDelay = config.getMatcher().getFailFastMatchDelay();
		// Cumulative max fail count
		long cumulatedMax = 0;
		// Whether the tag is locked or not
		boolean lock = false;

		for (String factor : factors) {
			// Present factor need locks
			boolean factorLock = false;

			// Got count of failures by factor.
			Long cumulated = matchCumulator.getCumulative(factor);

			// Stored accumulated max
			cumulatedMax = Math.max(cumulatedMax, cumulated);

			// Check last locked remain time(if exist)
			String lockedPrincipal = (String) lockCache.get(new EnhancedKey(factor, String.class));

			// Previous locks have not expired
			if (StringUtils.hasText(lockedPrincipal)) {
				factorLock = true;
			}

			/*
			 * No previous locks, If the number of failures is exceeded, no
			 * login is allowed.
			 */
			if (cumulatedMax > matchLockMaxAttempts) {
				factorLock = true;
			}
			if (log.isTraceEnabled()) {
				log.trace(
						"assertAccountLocked()=> factor:{}, cumulated: {}, matchLockMaxAttempts: {}, cumulatedMax: {}, lock: {}, factorLock: {}",
						factor, cumulated, matchLockMaxAttempts, cumulatedMax, lock, factorLock);
			}

			/*
			 * If lockout is required at present, Update decay counter time
			 */
			if (factorLock) {
				Long remainTime = lockCache.timeToLive(new EnhancedKey(factor, matchLockDelay), principal);
				log.warn(String.format(
						"Matching failed, limiter factor [%s] attempts have been made to exceed the maximum limit [%s], remain time [%s Sec] [%s]",
						factor, matchLockMaxAttempts, remainTime, factor));
			}

			// The whole is marked as needing to be locked
			lock = factorLock;
		}

		if (lock) { // Any factor matched
			log.warn("Client that has been locked. factors: {}", factors);
			throw new LockedAccountException(bundle.getMessage("AbstractAttemptsMatcher.ipAccessReject"));
		}

		return cumulatedMax;
	}

	/**
	 * Assertion some verifications before requesting authentication (e.g, graph
	 * verification code when password is logged in)
	 * 
	 * @param token
	 * @param principal
	 * @param factors
	 */
	protected abstract void assertRequestVerify(AuthenticationToken token, String principal, List<String> factors);

	/**
	 * Initializing
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		IamProperties.MatcherProperties matcher = config.getMatcher();
		this.lockCache = cacheManager.getEnhancedCache(CACHE_MATCH_LOCK);

		this.matchCumulator = Cumulators.newCumulator(cacheManager.getEnhancedCache(CACHE_FAILFAST_MATCH_COUNTER),
				matcher.getFailFastMatchDelay());

		this.applyCaptchaCumulator = Cumulators.newCumulator(cacheManager.getEnhancedCache(CACHE_FAILFAST_CAPTCHA_COUNTER),
				matcher.getFailFastCaptchaDelay());

		this.applySmsCumulator = Cumulators.newCumulator(cacheManager.getEnhancedCache(CACHE_FAILFAST_SMS_COUNTER),
				matcher.getFailFastSmsMaxDelay());

		this.sessionMatchCumulator = Cumulators.newSessionCumulator(CACHE_FAILFAST_MATCH_COUNTER,
				matcher.getFailFastMatchDelay());

		Assert.notNull(lockCache, "lockCache is null, please check configure");
		Assert.notNull(matchCumulator, "matchCumulator is null, please check configure");
		Assert.notNull(applyCaptchaCumulator, "applyCaptchaCumulator is null, please check configure");
		Assert.notNull(applySmsCumulator, "applySmsCumulator is null, please check configure");
		Assert.notNull(sessionMatchCumulator, "sessionMatchCumulator is null, please check configure");
	}

	/**
	 * Destroy verification accumulators all.
	 * 
	 * @param factors
	 */
	private void destroyCumulators(@NotNull List<String> factors) {
		matchCumulator.destroy(factors);
		applyCaptchaCumulator.destroy(factors);
		applySmsCumulator.destroy(factors);
		sessionMatchCumulator.destroy(factors);

		// Unlock all accounts that have failed to log in this session.
		List<String> failPrincipalFactors = getBindValue(KEY_FAIL_PRINCIPAL_FACTORS);
		if (null != failPrincipalFactors) {
			matchCumulator.destroy(failPrincipalFactors);
			applyCaptchaCumulator.destroy(failPrincipalFactors);
			applySmsCumulator.destroy(failPrincipalFactors);

			// Cleanup lock factors.
			failPrincipalFactors.forEach(f -> {
				try {
					if (log.isInfoEnabled()) {
						log.info("Remove past.failure principal factor: {}", f);
					}
					lockCache.remove(new EnhancedKey(f));
				} catch (Exception e) {
					log.error("", e);
				}
			});
		}
	}

}