/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.conceptmanagementapps.api;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.conceptmanagementapps.ConceptManagementAppsConstants;
import org.openmrs.module.conceptmanagementapps.ConceptManagementAppsProperties;
import org.openmrs.test.BaseContextSensitiveTest;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Tests {@link $ ConceptManagementAppsService} .
 */
public class ConceptManagementAppsServiceTest extends BaseModuleContextSensitiveTest {
	
	protected ConceptManagementAppsService conceptManagementAppsService = null;
	
	/**
	 * Run this before each unit test in this class. The "@Before" method in
	 * {@link BaseContextSensitiveTest} is run right before this method.
	 * 
	 * @throws Exception
	 */
	@Before
	public void runBeforeAllTests() throws Exception {
		conceptManagementAppsService = Context.getService(ConceptManagementAppsService.class);
	}
	
	@Test
	public void shouldSetupContext() {
		assertNotNull(Context.getService(ConceptManagementAppsService.class));
	}
	
	@Test
	public void getConceptsParentReferenceTerms_returnsConceptsParentReferenceTerms() throws Exception {
		executeDataSet("concepts.xml");
		ConceptService cs = Context.getConceptService();
		
		Set<ConceptReferenceTerm> refTermList = conceptManagementAppsService.getConceptsParentReferenceTerms(cs
		        .getConcept(225));
		
		Assert.assertEquals(0, refTermList.size());
	}
	
	@Test
	public void getRefTermParentReferenceTerms_returnsRefTermParentReferenceTerms() throws Exception {
		executeDataSet("concepts.xml");
		ConceptService cs = Context.getConceptService();
		ConceptManagementAppsProperties cmap = new ConceptManagementAppsProperties();		
		ConceptSource conceptSource = cs
		        .getConceptSourceByUuid("1ADDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
		Set<ConceptReferenceTerm> refTermList = conceptManagementAppsService.getRefTermParentReferenceTerms(cs
		        .getConceptReferenceTerm(30),conceptSource);
		
		Assert.assertEquals(1, refTermList.size());
		
	}
	
	@Test
	public void getUnmappedConcepts_getsCorrectNumberOfRows() throws Exception {
		executeDataSet("concepts.xml");
		ConceptService cs = Context.getConceptService();
		List<ConceptClass> classesToInclude = new ArrayList<ConceptClass>();
		Integer sourceId = 6;
		classesToInclude.add(cs.getConceptClass(2));
		classesToInclude.add(cs.getConceptClass(4));
		classesToInclude.add(cs.getConceptClass(1));
		
		conceptManagementAppsService = (ConceptManagementAppsService) Context.getService(ConceptManagementAppsService.class);
		List<Concept> conceptList = conceptManagementAppsService.getUnmappedConcepts(new ConceptSource(sourceId),
		    classesToInclude);
		
		Assert.assertEquals(6, conceptList.size());
	}
	
	@Test
	public void getConceptReferenceTerms_getsCorrectNumberOfRows() throws Exception {
		executeDataSet("concepts.xml");
		conceptManagementAppsService = (ConceptManagementAppsService) Context.getService(ConceptManagementAppsService.class);
		List<ConceptReferenceTerm> refTermList = conceptManagementAppsService
		        .getConceptReferenceTermsWithSpecifiedSourceIfIncluded(null, 0, 5, "conceptSource", 1);
		
		Assert.assertEquals(5, refTermList.size());
	}
	
	@Test
	public void getConceptReferenceTermsWithQuery_getsCorrectNumberOfRows() throws Exception {
		executeDataSet("concepts.xml");
		ConceptService cs = Context.getConceptService();
		Integer sourceId = 6;
		
		conceptManagementAppsService = (ConceptManagementAppsService) Context.getService(ConceptManagementAppsService.class);
		List<ConceptReferenceTerm> refTermList = conceptManagementAppsService.getConceptReferenceTermsWithQuery("1",
		    cs.getConceptSource(sourceId), 0, 2, false, "code", 1);
		Assert.assertEquals(2, refTermList.size());
	}
	
	@Test
	public void uploadSpreadsheet_shouldPassWithoutErrors() throws Exception {
		conceptManagementAppsService = (ConceptManagementAppsService) Context.getService(ConceptManagementAppsService.class);
		final String fileName = "test.csv";
		String line = "\"map type\",\"source name\",\"source code\",\"concept Id\",\"concept uuid\",\"preferred name\",\"description\",\"class\",\"datatype\",\"all existing mappings\"\n";
		line += "\"same-as\",\"\",12345,225,\"432AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"MEDICAL EXAMINATION, ROUTINE\",\"Routine examination, without signs of problems.\",\"Procedure\",\"Boolean\",\"SAME-AS AMPATH \n SAME-AS SNOMED MVP\n SAME-AS PIH \n SAME-AS AMPATH \n NARROWER-THAN SNOMED NP\"\n";
		line += "\"same-as\",\"SNOMED CT\",12345,225,\"432AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"MEDICAL EXAMINATION, ROUTINE\",\"Routine examination, without signs of problems.\",\"Procedure\",\"Boolean\",\"SAME-AS AMPATH \n SAME-AS SNOMED MVP\n SAME-AS PIH \n SAME-AS AMPATH \n NARROWER-THAN SNOMED NP\"\n";
		line += "\"same-as\",\"SNOMED CT\",12345,225,\"1148AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"TOTAL MATERNAL TO CHILD TRANSMISSION PROPHYLAXIS\",\"Describes the use of drugs to prevent the maternal to child transmission of HIV during pregnancy.\",\"Procedure\",\"Coded\",\"SAME-AS AMPATH \n NARROWER-THAN SNOMED NP \n SAME-AS SNOMED MVP\"\n";
		final byte[] content = line.getBytes();
		MockMultipartFile mockMultipartFile = new MockMultipartFile("content", fileName, "text/plain", content);
		conceptManagementAppsService.uploadSpreadsheet(mockMultipartFile);
		
	}
	
	@Test
	public void downloadFileWithMissingConceptMappings_shouldPassWithoutErrors() throws Exception {
		executeDataSet("concepts.xml");
		PrintWriter pw = mock(PrintWriter.class);
		
		conceptManagementAppsService = (ConceptManagementAppsService) Context.getService(ConceptManagementAppsService.class);
		List<Concept> conceptList = new ArrayList<Concept>();
		conceptList.add(Context.getConceptService().getConcept("300"));
		conceptManagementAppsService.writeFileWithMissingConceptMappings(conceptList, pw, "same-as", "SNOMED CT");
	}
	
}
