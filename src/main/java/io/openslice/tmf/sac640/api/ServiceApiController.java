/*-
 * ========================LICENSE_START=================================
 * io.openslice.tmf.api
 * %%
 * Copyright (C) 2019 - 2021 openslice.io
 * %%
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
 * =========================LICENSE_END==================================
 */
package io.openslice.tmf.sac640.api;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import io.openslice.model.UserRoleType;
import io.openslice.tmf.common.model.UserPartRoleType;
import io.openslice.tmf.sim638.model.Service;
import io.openslice.tmf.sim638.model.ServiceCreate;
import io.openslice.tmf.sim638.model.ServiceUpdate;
import io.openslice.tmf.sim638.service.ServiceRepoService;
import io.openslice.tmf.util.AddUserAsOwnerToRelatedParties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@jakarta.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.SpringCodegen", date = "2020-04-29T12:42:32.118457300+03:00[Europe/Athens]")

@Controller("ServiceApiController640")
@RequestMapping("/serviceActivationAndConfiguration/v4/")
public class ServiceApiController implements ServiceApi {

	private static final Logger log = LoggerFactory.getLogger(ServiceApiController.class);

	private final ObjectMapper objectMapper;

	private final HttpServletRequest request;

	@Autowired
	ServiceRepoService serviceRepoService;

	@org.springframework.beans.factory.annotation.Autowired
	public ServiceApiController(ObjectMapper objectMapper, HttpServletRequest request) {
		this.objectMapper = objectMapper;
		this.request = request;
	}

	@PreAuthorize("hasAnyAuthority('ROLE_USER')" )
	@Override
	public ResponseEntity<Service> createService(Principal principal, @Valid ServiceCreate service) {
		try {
			if (SecurityContextHolder.getContext().getAuthentication() != null) {
				service.setRelatedParty(AddUserAsOwnerToRelatedParties.addUser(principal.getName(), principal.getName(),
						UserPartRoleType.REQUESTER, "", service.getRelatedParty()));

				Service c = serviceRepoService.addService(service);

				return new ResponseEntity<Service>(c, HttpStatus.CREATED);
			} else {

				return new ResponseEntity<Service>(HttpStatus.FORBIDDEN);
			}

		} catch (Exception e) {
			log.error("Couldn't serialize response for content type application/json", e);
			return new ResponseEntity<Service>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAnyAuthority('ROLE_USER')" )
	@Override
	public ResponseEntity<Void> deleteService(String id) {
		try {
			if (serviceRepoService.deleteByUuid(id)) {
				return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
			}
			return new ResponseEntity<Void>(HttpStatus.NOT_FOUND);
		} catch (Exception e) {
			log.error("Couldn't delete service", e);
			return new ResponseEntity<Void>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@PreAuthorize("hasAnyAuthority('ROLE_USER')" )
	public ResponseEntity<List<Service>> listService(Principal principal, @Valid String fields, @Valid Integer offset,
			@Valid Integer limit) {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

			if ( authentication.getAuthorities().contains( new SimpleGrantedAuthority( UserRoleType.ROLE_ADMIN.getValue()  ) ) ) {
				return new ResponseEntity<List<Service>>(serviceRepoService.findAll(null, new HashMap<>()), HttpStatus.OK);
			}else {
				return new ResponseEntity<List<Service>>(serviceRepoService.findAll( principal.getName(), UserPartRoleType.REQUESTER ), HttpStatus.OK);
			}

		} catch (Exception e) {
			log.error("Couldn't serialize response for content type application/json", e);
			return new ResponseEntity<List<Service>>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAnyAuthority('ROLE_USER')" )
	@Override
	public ResponseEntity<Service> patchService(Principal principal, String id, @Valid ServiceUpdate service) {
		try {
			Service c = serviceRepoService.updateService(id, service, true, null, null);
			if (c == null) {
				return new ResponseEntity<Service>(HttpStatus.NOT_FOUND);
			}
			return new ResponseEntity<Service>(c, HttpStatus.OK);
		} catch (Exception e) {
			log.error("Couldn't serialize response for content type application/json", e);
			return new ResponseEntity<Service>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAnyAuthority('ROLE_USER')" )
	@Override
	public ResponseEntity<Service> retrieveService(Principal principal, String id, @Valid String fields) {
		try {
			Service s = serviceRepoService.findByUuid(id);
			if (s == null) {
				return new ResponseEntity<Service>(HttpStatus.NOT_FOUND);
			}
			return new ResponseEntity<Service>(s, HttpStatus.OK);
		} catch (Exception e) {
			log.error("Couldn't serialize response for content type application/json", e);
			return new ResponseEntity<Service>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
