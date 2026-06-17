/*-
 * ========================LICENSE_START=================================
 * io.openslice.tmf.api
 * %%
 * Copyright (C) 2019 openslice.io
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
package io.openslice.services.api;



import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import io.openslice.tmf.OpenAPISpringBoot;
import io.openslice.tmf.common.model.Any;
import io.openslice.tmf.common.model.UserPartRoleType;
import io.openslice.tmf.common.model.service.Characteristic;
import io.openslice.tmf.common.model.service.Note;
import io.openslice.tmf.common.model.service.ServiceSpecificationRef;
import io.openslice.tmf.common.model.service.ServiceStateType;
import io.openslice.tmf.prm669.model.RelatedParty;
import io.openslice.tmf.sim638.model.Service;
import io.openslice.tmf.sim638.model.ServiceCreate;
import io.openslice.tmf.sim638.model.ServiceUpdate;
import io.openslice.tmf.sim638.service.ServiceRepoService;


@RunWith(SpringRunner.class)
@Transactional
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.MOCK , classes = OpenAPISpringBoot.class)
@AutoConfigureMockMvc 
@ActiveProfiles("testing")
public class ServiceActivationAndConfigurationIntegrationTest {


	private static final transient Log logger = LogFactory.getLog( ServiceActivationAndConfigurationIntegrationTest.class.getName());

	private static final String SAC_BASE = "/serviceActivationAndConfiguration/v4/service";

    @Autowired
    private MockMvc mvc;

	@Autowired
	ServiceRepoService serviceRepoService;

	@Autowired
	private WebApplicationContext context;

	@Before
	public void setup() {
		mvc = MockMvcBuilders
		  .webAppContextSetup(context)
		  .apply(springSecurity())
		  .build();
	}

	@WithMockUser(username = "osadmin", roles = { "USER" })
	@Test
	public void testServiceActivationLifecycle() throws UnsupportedEncodingException, IOException, Exception {

		/**
		 * create (POST) a service via the TMF640 endpoint
		 */
		ServiceCreate aService = new ServiceCreate();
		aService.setName("aNew SAC Service");
		aService.setCategory("Activation");
		aService.setDescription("Activation Descr");
		aService.setStartDate(OffsetDateTime.now(ZoneOffset.UTC).toString());
		aService.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).toString());

		Note noteItem = new Note();
		noteItem.text("activation note");
		aService.addNoteItem(noteItem);

		Characteristic serviceCharacteristicItem = new Characteristic();
		serviceCharacteristicItem.setName("ConfigStatus");
		serviceCharacteristicItem.setValue(new Any("NONE"));
		aService.addServiceCharacteristicItem(serviceCharacteristicItem);

		ServiceSpecificationRef aServiceSpecificationRef = new ServiceSpecificationRef();
		aServiceSpecificationRef.setId("specid");
		aServiceSpecificationRef.setName("specName");
		aService.setServiceSpecificationRef(aServiceSpecificationRef);

		String responseService = mvc.perform(MockMvcRequestBuilders.post(SAC_BASE)
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JsonUtils.toJson(aService)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse().getContentAsString();

		Service responseSrvc = JsonUtils.toJsonObj(responseService, Service.class);
		logger.info("created SAC service = " + JsonUtils.toJsonString(responseSrvc));

		assertThat(responseSrvc.getId()).isNotNull();
		assertThat(responseSrvc.getCategory()).isEqualTo("Activation");
		assertThat(responseSrvc.getDescription()).isEqualTo("Activation Descr");
		assertThat(responseSrvc.getServiceCharacteristic().size()).isEqualTo(1);
		assertThat(responseSrvc.getServiceSpecificationRef().getId()).isEqualTo("specid");

		boolean userPartyRoleexists = false;
		for (RelatedParty r : responseSrvc.getRelatedParty()) {
			if (r.getName().equals("osadmin") && r.getRole().equals(UserPartRoleType.REQUESTER.toString())) {
				userPartyRoleexists = true;
			}
		}
		assertThat(userPartyRoleexists).isTrue();
		assertThat(serviceRepoService.findAll().size()).isEqualTo(1);

		/**
		 * retrieve (GET by id) via the TMF640 endpoint
		 */
		String retrieved = mvc.perform(MockMvcRequestBuilders.get(SAC_BASE + "/" + responseSrvc.getId())
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse().getContentAsString();
		Service retrievedSrvc = JsonUtils.toJsonObj(retrieved, Service.class);
		assertThat(retrievedSrvc.getId()).isEqualTo(responseSrvc.getId());

		/**
		 * activate the service (PATCH state -> active) via the TMF640 endpoint
		 */
		ServiceUpdate servUpd = new ServiceUpdate();
		servUpd.setState(ServiceStateType.ACTIVE);
		for (Characteristic c : responseSrvc.getServiceCharacteristic()) {
			if (c.getName().equals("ConfigStatus")) {
				c.setValue(new Any("RUNNING"));
			}
			servUpd.addServiceCharacteristicItem(c);
		}

		String activated = mvc.perform(MockMvcRequestBuilders.patch(SAC_BASE + "/" + responseSrvc.getId())
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JsonUtils.toJson(servUpd)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse().getContentAsString();
		Service activatedSrvc = JsonUtils.toJsonObj(activated, Service.class);
		logger.info("activated SAC service = " + JsonUtils.toJsonString(activatedSrvc));

		assertThat(activatedSrvc.getState()).isEqualTo(ServiceStateType.ACTIVE);
		assertThat(activatedSrvc.getServiceCharacteristicByName("ConfigStatus").getValue().getValue()).isEqualTo("RUNNING");

		/**
		 * list (GET) via the TMF640 endpoint
		 */
		String listed = mvc.perform(MockMvcRequestBuilders.get(SAC_BASE)
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse().getContentAsString();
		Service[] listedSrvcs = JsonUtils.toJsonObj(listed, Service[].class);
		assertThat(listedSrvcs.length).isEqualTo(1);
		assertThat(listedSrvcs[0].getId()).isEqualTo(responseSrvc.getId());

		/**
		 * delete (DELETE) via the TMF640 endpoint
		 */
		mvc.perform(MockMvcRequestBuilders.delete(SAC_BASE + "/" + responseSrvc.getId())
				.with(SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().isNoContent());
		assertThat(serviceRepoService.findAll().size()).isEqualTo(0);

		/**
		 * retrieve and patch of a non-existent service return 404
		 */
		mvc.perform(MockMvcRequestBuilders.get(SAC_BASE + "/" + responseSrvc.getId())
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());

		mvc.perform(MockMvcRequestBuilders.patch(SAC_BASE + "/" + responseSrvc.getId())
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JsonUtils.toJson(servUpd)))
				.andExpect(status().isNotFound());
	}

	@WithMockUser(username = "osadmin", roles = { "USER", "ADMIN" })
	@Test
	public void testAdminListIncludesServiceWithoutOrder() throws UnsupportedEncodingException, IOException, Exception {

		/**
		 * a TMF640 service is created directly, without any service order
		 */
		ServiceCreate aService = new ServiceCreate();
		aService.setName("orderless SAC Service");
		aService.setCategory("Activation");

		String responseService = mvc.perform(MockMvcRequestBuilders.post(SAC_BASE)
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JsonUtils.toJson(aService)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Service responseSrvc = JsonUtils.toJsonObj(responseService, Service.class);
		assertThat(responseSrvc.getServiceOrder()).isEmpty();

		/**
		 * the admin list path must return the orderless service as a full Service
		 * object (admin branch uses findAll(), not the order-joined / raw-map query)
		 */
		String listed = mvc.perform(MockMvcRequestBuilders.get(SAC_BASE)
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse().getContentAsString();
		Service[] listedSrvcs = JsonUtils.toJsonObj(listed, Service[].class);
		assertThat(listedSrvcs.length).isEqualTo(1);
		assertThat(listedSrvcs[0].getId()).isEqualTo(responseSrvc.getId());
		assertThat(listedSrvcs[0].getCategory()).isEqualTo("Activation");
	}

}
