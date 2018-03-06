package eu.h2020.symbiote.subman.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import eu.h2020.symbiote.security.ComponentSecurityHandlerFactory;
import eu.h2020.symbiote.security.accesspolicies.IAccessPolicy;
import eu.h2020.symbiote.security.accesspolicies.common.AccessPolicyFactory;
import eu.h2020.symbiote.security.accesspolicies.common.AccessPolicyType;
import eu.h2020.symbiote.security.accesspolicies.common.singletoken.SingleTokenAccessPolicySpecifier;
import eu.h2020.symbiote.security.commons.SecurityConstants;
import eu.h2020.symbiote.security.commons.exceptions.custom.InvalidArgumentsException;
import eu.h2020.symbiote.security.commons.exceptions.custom.SecurityHandlerException;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.security.handler.IComponentSecurityHandler;
import io.jsonwebtoken.Claims;

/**
 * @author Petar Krivic (UniZG-FER)
 * 28/02/2018
 */
@Component
public class SecurityManager {

	private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);
	private IComponentSecurityHandler securityHandler = null;

	@Value("${platform.id}")
	private String platformId;

	private final boolean isSecurityEnabled;

	@Autowired
	public SecurityManager(@Value("${aam.deployment.owner.username") String componentOwnerUsername,
			@Value("${aam.deployment.owner.password}") String componentOwnerPassword,
			@Value("${symbIoTe.localaam.url}") String localAAMAddress,
			@Value("${symbIoTe.sm.clientId}") String clientId,
			@Value("${aam.security.KEY_STORE_FILE_NAME}") String keystorePath,
			@Value("${aam.security.KEY_STORE_PASSWORD}") String keystorePassword,
			@Value("${symbiote.sm.security.enabled}") boolean isSecurityEnabled) throws SecurityHandlerException {
		this.isSecurityEnabled = isSecurityEnabled;

		if (isSecurityEnabled) {
			securityHandler = ComponentSecurityHandlerFactory.getComponentSecurityHandler(keystorePath,
					keystorePassword, clientId, localAAMAddress, componentOwnerUsername, componentOwnerPassword);
		} else {
			logger.info("Security Request validation is disabled");
		}
	}

	public ResponseEntity generateServiceResponse() {
		if (isSecurityEnabled) {
			try {
				String serviceResponse = securityHandler.generateServiceResponse();
				return new ResponseEntity<>(serviceResponse, HttpStatus.OK);
			} catch (SecurityHandlerException e) {
				logger.info("Failed to generate a service response", e);
				return new ResponseEntity<>(e.getErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} else {
			String message = "generateServiceResponse: Security is disabled";
			logger.debug(message);
			return new ResponseEntity<>(message, HttpStatus.OK);
		}
	}

	public ResponseEntity checkListResourcesRequest(HttpHeaders httpHeaders, String serviceResponse) {
		if (isSecurityEnabled) {
			if (httpHeaders == null)
				return addSecurityService("HttpHeaders are null", new HttpHeaders(), HttpStatus.BAD_REQUEST,
						serviceResponse);

			SecurityRequest securityRequest;
			try {
				securityRequest = new SecurityRequest(httpHeaders.toSingleValueMap());
				logger.debug(
						"Received SecurityRequest of listResources request to be verified: (" + securityRequest + ")");
			} catch (InvalidArgumentsException e) {
				logger.info("Could not create the SecurityRequest", e);
				return addSecurityService(e.getErrorMessage(), new HttpHeaders(), HttpStatus.BAD_REQUEST,
						serviceResponse);
			}

			Set<String> checkedPolicies;
			try {
				checkedPolicies = checkSingleLocalHomeTokenAccessPolicy(securityRequest);
			} catch (Exception e) {
				logger.info("Could not verify the access policies", e);
				return addSecurityService(e.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR,
						serviceResponse);
			}

			if (checkedPolicies.size() >= 1) {
				return new ResponseEntity<>(HttpStatus.OK);
			} else {
				return addSecurityService("The stored resource access policy was not satisfied", new HttpHeaders(),
						HttpStatus.UNAUTHORIZED, serviceResponse);
			}
		} else {
			logger.debug("checkAccess: Security is disabled");

			// if security is disabled in properties
			return new ResponseEntity<>("Security disabled", HttpStatus.OK);
		}
	}

	public static ResponseEntity addSecurityService(Object response, HttpHeaders httpHeaders, HttpStatus httpStatus,
			String serviceResponse) {
		httpHeaders.put(SecurityConstants.SECURITY_RESPONSE_HEADER, Collections.singletonList(serviceResponse));
		return new ResponseEntity<>(response, httpHeaders, httpStatus);
	}

	//TODO adapt to federation access policies
	private Set<String> checkSingleLocalHomeTokenAccessPolicy(SecurityRequest securityRequest) throws Exception {
		Map<String, IAccessPolicy> accessPoliciesMap = new HashMap<>();
		Map<String, String> requiredClaims = new HashMap<>();

		requiredClaims.put(Claims.ISSUER, platformId);

		// Construct policy
		IAccessPolicy policy = AccessPolicyFactory
				.getAccessPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.SLHTAP, requiredClaims));
		accessPoliciesMap.put("SingleLocalHomeTokenAccessPolicy", policy);

		return securityHandler.getSatisfiedPoliciesIdentifiers(accessPoliciesMap, securityRequest);
	}
}
