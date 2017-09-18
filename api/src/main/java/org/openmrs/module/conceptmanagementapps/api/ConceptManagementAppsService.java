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
 * Copyright (C) OpenMRS, LLC. All Rights Reserved.
 */
package org.openmrs.module.conceptmanagementapps.api;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.ui.framework.page.FileDownload;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.supercsv.io.ICsvMapWriter;

/**
 * This service exposes module's core functionality. It is a Spring managed bean which is configured
 * in moduleApplicationContext.xml.
 * <p>
 * It can be accessed only via Context:<br>
 * <code>
 * Context.getService(ConceptManagementAppsService.class).someMethod();
 * </code>
 * 
 * @see org.openmrs.api.context.Context
 */

public interface ConceptManagementAppsService extends OpenmrsService {
	
	/**
	 * Set the name of the method running to either add names, ancestors or relationships
	 * 
	 * @param cancelProcess
	 */
	public void setManageSnomedCTProcessCancelled(Boolean cancelProcess);
	
	/**
	 * Gets the name of the method running to either add names, ancestors or relationships
	 * 
	 * @return
	 */
	public Boolean getManageSnomedCTProcessCancelled();
	
	/**
	 * Find out if the method to add names, ancestors or relationships is running if so return the
	 * name of the method
	 * 
	 * @return ManageSnomedCTProcess the name of the process running
	 */
	public ManageSnomedCTProcess getCurrentSnomedCTProcess();
	
	/**
	 * Gets Concepts not mapped to the given source with desired classes
	 * 
	 * @param conceptSource
	 * @param classesToInclude
	 * @return List<Concept> the list of unmapped concepts
	 */
	@Transactional(readOnly = true)
	public List<Concept> getUnmappedConcepts(ConceptSource conceptSource, List<ConceptClass> classesToInclude);
	
	/**
	 * Upload spreadsheet filled out with mappings
	 * 
	 * @param spreadsheetFile
	 * @return FileDownload (returns a file with errors if there are errors)
	 * @throws APIException
	 */
	@Transactional
	public FileDownload uploadSpreadsheet(MultipartFile spreadsheetFile) throws APIException;



    @Transactional
    public void startManageSnomedCTProcess(String process, String dirPath, ConceptSource snomedSource, String conceptCode, int conceptClassId) throws APIException,
            FileNotFoundException;

	/**
	 * Starts a method to either add names, ancestors or relationships to concept reference terms
	 * 
	 * @param process
	 * @param dirPath
	 * @param snomedSource
	 * @throws APIException
	 */
	@Transactional
	public void startManageSnomedCTProcess(String process, String dirPath, ConceptSource snomedSource) throws APIException,
	    FileNotFoundException;


	/**
	 * Gets the concept reference terms for a source if given (otherwise all) from a starting point
	 * if given (otherwise from 0) returning specified number if given (otherwise 1000) ordered
	 * ascending or descending sorted on given column
	 * 
	 * @param specifiedSource
	 * @param startIndex
	 * @param numToReturn
	 * @param sortColumn
	 * @param order
	 * @return List of ConceptReferenceTerms
	 * @throws APIException
	 */
	@Transactional(readOnly = true)
	public List<ConceptReferenceTerm> getConceptReferenceTermsWithSpecifiedSourceIfIncluded(ConceptSource specifiedSource,
	                                                                                        Integer startIndex,
	                                                                                        Integer numToReturn,
	                                                                                        String sortColumn, int order)
	    throws APIException;
	
	/**
	 * Gets the count of concept reference terms for a source if given (otherwise all)
	 * 
	 * @param specifiedSource
	 * @return Integer
	 * @throws APIException
	 */
	@Transactional(readOnly = true)
	public Integer getCountOfConceptReferenceTermsWithSpecifiedSourceIfIncluded(ConceptSource specifiedSource)
	    throws APIException;
	
	/**
	 * Queries for matching reference terms given a query string from a starting point if given
	 * (otherwise from 0) returning specified number if given (otherwise 1000) ordered ascending or
	 * descending sorted on given column
	 * 
	 * @param query
	 * @param conceptSource
	 * @param start
	 * @param length
	 * @param includeRetired
	 * @param sortColumn
	 * @param order
	 * @return List of ConceptReferenceTerms
	 * @throws APIException
	 */
	@Transactional(readOnly = true)
	public List<ConceptReferenceTerm> getConceptReferenceTermsWithQuery(String query, ConceptSource conceptSource,
	                                                                    Integer start, Integer length,
	                                                                    boolean includeRetired, String sortColumn, int order)
	    throws APIException;
	
	/**
	 * Writes the file with the missing mappings
	 * 
	 * @param conceptList
	 * @param spreadsheetWriter
	 * @param mapTypeForDefaultValue
	 * @param conceptSourceName
	 * @return ICsvMapWriter
	 * @throws Exception
	 */
	@Transactional(readOnly = true)
	public ICsvMapWriter writeFileWithMissingConceptMappings(List<Concept> conceptList, PrintWriter spreadsheetWriter,
	                                                         String mapTypeForDefaultValue, String conceptSourceName)
	    throws Exception;
	
	/**
	 * Gets count of matching reference terms given a query string
	 * 
	 * @param query
	 * @param conceptSource
	 * @param includeRetired
	 * @return Integer
	 * @throws APIException
	 */
	@Transactional(readOnly = true)
	public Integer getCountOfConceptReferenceTermsWithQuery(String query, ConceptSource conceptSource, boolean includeRetired)
	    throws APIException;
	
	/**
	 * Gets the parents for the specified concept. Uses the International Release RF2 version from
	 * Unified Medical Language System® (UMLS®) to find the parents contained in the
	 * /SnomedCT_Release_INT_20130131/RF2Release/Full/Terminology/ Relationship file
	 * 
	 * @param concept
	 * @return Set of ConceptReferenceTerms
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getConceptsParentReferenceTerms(Concept concept);
	
	/**
	 * Gets the children for the specified concept. Uses the International Release RF2 version from
	 * Unified Medical Language System® (UMLS®) to find the children contained in the
	 * /SnomedCT_Release_INT_20130131/RF2Release/Full/Terminology/ Relationship file
	 * 
	 * @param concept
	 * @return Set of ConceptReferenceTerms
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getConceptsChildReferenceTerms(Concept concept);
	
	/**
	 * Gets the parents for the specified conceptReferenceTerm using the concept reference term map
	 * termA id
	 * 
	 * @param currentTerm
	 * @return Set of ConceptReferenceTerms
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getRefTermParentReferenceTerms(ConceptReferenceTerm currentTerm, ConceptSource conceptSource);
	
	/**
	 * Gets the children for the specified conceptReferenceTerm using the concept reference term map
	 * termA id
	 * 
	 * @param currentTerm
	 * @return Set of ConceptReferenceTerms
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getRefTermChildReferenceTerms(ConceptReferenceTerm currentTerm, ConceptSource conceptSource);
	
}
