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
package org.openmrs.module.conceptmanagementapps.api.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDescription;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptReferenceTermMap;
import org.openmrs.ConceptSource;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.conceptmanagementapps.ConceptManagementAppsConstants;
import org.openmrs.module.conceptmanagementapps.ConceptManagementAppsProperties;
import org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService;
import org.openmrs.module.conceptmanagementapps.api.ManageSnomedCTProcess;
import org.openmrs.module.conceptmanagementapps.api.db.ConceptManagementAppsDAO;
import org.openmrs.ui.framework.page.FileDownload;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * It is a default implementation of {@link ConceptManagementAppsService}.
 */
public class ConceptManagementAppsServiceImpl extends BaseOpenmrsService implements ConceptManagementAppsService {

	protected Log log = LogFactory.getLog(getClass());

    private static final CsvPreference TAB_DELIMITED = new CsvPreference.Builder('\"', '\t', "\n").build();

    private static final String RETIRED_TERM = "1";

    static final String PARENT_TERM = "sourceId";

    static final String CHILD_TERM = "destinationId";

    static final String TERM_ID = "conceptId";

    static final String IS_A_RELATIONSHIP = "116680003";

    private static final String ROW_ID = "id";

    static final String TERM_NAME = "term";

    private static final String EFFECTIVE_DATE = "effectiveDate";

    private static final String RELATIONSHIP_FILE = "sct2_Relationship_Full_INT_";

    private static final String DESCRIPTION_FILE = "sct2_Description_Full-en_INT_";

    static final String RELATIONSHIP_ID = "typeId";

    static final String DESCRIPTION_TYPE_ID = "typeId";

    ConceptManagementAppsDAO dao;

	 boolean manageSnomedCTProcessCancelled = true;

	 ManageSnomedCTProcess currentSnomedCTProcess;

    static StandardAnalyzer analyzer = new StandardAnalyzer();

    static String snomedIndexFileDirectoryLocation;

	/**
	 * @param dao the dao to set
	 */
	public void setDao(ConceptManagementAppsDAO dao) {
		this.dao = dao;
	}

	/**
	 * @return the dao
	 */
	public ConceptManagementAppsDAO getDao() {
		return dao;
	}

	/**
	 * @param currentSnomedCTProcess the currentSnomedCTProcess to set
	 */
	public void setCurrentSnomedCTProcess(ManageSnomedCTProcess currentSnomedCTProcess) {
		this.currentSnomedCTProcess = currentSnomedCTProcess;
	}

	/**
	 * @return the currentSnomedCTProcess
	 */
	public ManageSnomedCTProcess getCurrentSnomedCTProcess() {
		return currentSnomedCTProcess;
	}

	/**
	 * For canceling current running process to add names relationships or ancestors to concept
	 * reference terms
	 *
	 * @param manageSnomedCTProcessCancelled
	 */
	public void setManageSnomedCTProcessCancelled(Boolean manageSnomedCTProcessCancelled) {
		this.manageSnomedCTProcessCancelled = manageSnomedCTProcessCancelled;
	}

	/**
	 * For canceling current running process to add names relationships or ancestors to concept
	 * reference terms
	 *
	 * @return boolean for if it needs to be cancelled
	 */
	public Boolean getManageSnomedCTProcessCancelled() {
		return manageSnomedCTProcessCancelled;
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getUnmappedConcepts(org.openmrs.ConceptSource,
	 *      java.util.List)
	 */
	@Transactional(readOnly = true)
	public List<Concept> getUnmappedConcepts(ConceptSource conceptSource, List<ConceptClass> classesToInclude) {

		return this.dao.getUnmappedConcepts(conceptSource, classesToInclude);
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getConceptReferenceTerms(org.openmrs.ConceptSource,
	 *      java.lang.Integer, java.lang.Integer, java.lang.String, int)
	 */
	@Transactional(readOnly = true)
	public List<ConceptReferenceTerm> getConceptReferenceTermsWithSpecifiedSourceIfIncluded(ConceptSource specifiedSource,
	                                                                                        Integer startIndex,
	                                                                                        Integer numToReturn,
	                                                                                        String sortColumn, int order)
	    throws DAOException {

		return this.dao.getConceptReferenceTermsWithSpecifiedSourceIfIncluded(specifiedSource, startIndex, numToReturn,
		    sortColumn, order);
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getCountOfConceptReferenceTerms(org.openmrs.ConceptSource)
	 */
	@Transactional(readOnly = true)
	public Integer getCountOfConceptReferenceTermsWithSpecifiedSourceIfIncluded(ConceptSource specifiedSource)
	    throws DAOException {

		return this.dao.getCountOfConceptReferenceTermsWithSpecifiedSourceIfIncluded(specifiedSource);
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getConceptReferenceTermsWithQuery(java.lang.String,
	 *      org.openmrs.ConceptSource, java.lang.Integer, java.lang.Integer, boolean,
	 *      java.lang.String, int)
	 */
	@Transactional(readOnly = true)
	public List<ConceptReferenceTerm> getConceptReferenceTermsWithQuery(String query, ConceptSource conceptSource,
	                                                                    Integer start, Integer length,
	                                                                    boolean includeRetired, String sortColumn, int order)
	    throws DAOException {

		return this.dao.getConceptReferenceTermsWithQuery(query, conceptSource, start, length, includeRetired, sortColumn,
		    order);
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getCountOfConceptReferenceTermsWithQuery(java.lang.String,
	 *      org.openmrs.ConceptSource, boolean)
	 */
	@Transactional(readOnly = true)
	public Integer getCountOfConceptReferenceTermsWithQuery(String query, ConceptSource conceptSource, boolean includeRetired)
	    throws DAOException {

		return this.dao.getCountOfConceptReferenceTermsWithQuery(query, conceptSource, includeRetired);

	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getConceptsParentReferenceTerms(org.openmrs.Concept)
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getConceptsParentReferenceTerms(Concept concept) {

		Collection<ConceptMap> conceptMappings = concept.getConceptMappings();
		Set<ConceptReferenceTerm> parentTerms = new HashSet<ConceptReferenceTerm>();

		for (ConceptMap map : conceptMappings) {
			if (StringUtils.equals(map.getConceptMapType().getUuid(),

			ConceptManagementAppsConstants.SAME_AS_CONCEPT_MAP_TYPE_UUID)) {
				Set<ConceptReferenceTermMap> termMaps = map.getConceptReferenceTerm().getConceptReferenceTermMaps();

				for (ConceptReferenceTermMap termMap : termMaps) {

					parentTerms.add(termMap.getTermB());
				}
			}
		}
		return parentTerms;
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getConceptsChildReferenceTerms(org.openmrs.Concept)
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getConceptsChildReferenceTerms(Concept concept) {

		Collection<ConceptMap> conceptMappings = concept.getConceptMappings();
		Set<ConceptReferenceTerm> childTerms = new HashSet<ConceptReferenceTerm>();

		for (ConceptMap map : conceptMappings) {
			if (StringUtils.equals(map.getConceptMapType().getUuid(),

			ConceptManagementAppsConstants.SAME_AS_CONCEPT_MAP_TYPE_UUID)) {
				Set<ConceptReferenceTermMap> termMaps = map.getConceptReferenceTerm().getConceptReferenceTermMaps();

				for (ConceptReferenceTermMap termMap : termMaps) {

					childTerms.add(termMap.getTermA());
				}
			}
		}
		return childTerms;
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getRefTermChildReferenceTerms(org.openmrs.ConceptReferenceTerm)
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getRefTermChildReferenceTerms(ConceptReferenceTerm currentTerm,
	                                                               ConceptSource conceptSource) {

		ConceptService conceptService = (ConceptService) Context.getService(ConceptService.class);

		Set<ConceptReferenceTerm> childTerms = new HashSet<ConceptReferenceTerm>();

		List<ConceptReferenceTermMap> parentMapTerms = this.dao.getReferenceTermsParents(conceptService
		        .getConceptReferenceTerm(currentTerm.getId()));

		for (ConceptReferenceTermMap termMap : parentMapTerms) {

			if (termMap.getTermB().getId() == currentTerm.getId()
			        && StringUtils.equals(termMap.getConceptMapType().getUuid(),
			            ConceptManagementAppsConstants.SAME_AS_CONCEPT_MAP_TYPE_UUID)
			        && termMap.getTermB().getConceptSource() == conceptSource) {

				childTerms.add(conceptService.getConceptReferenceTerm(termMap.getTermA().getId()));
			}
		}
		return childTerms;
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#getRefTermParentReferenceTerms(org.openmrs.ConceptReferenceTerm)
	 */
	@Transactional(readOnly = true)
	public Set<ConceptReferenceTerm> getRefTermParentReferenceTerms(ConceptReferenceTerm currentTerm,
	                                                                ConceptSource conceptSource) {

		ConceptService conceptService = (ConceptService) Context.getService(ConceptService.class);

		Set<ConceptReferenceTerm> parentTerms = new HashSet<ConceptReferenceTerm>();

		List<ConceptReferenceTermMap> childMapTerms = this.dao.getReferenceTermsChildren(conceptService
		        .getConceptReferenceTerm(currentTerm.getId()));

		for (ConceptReferenceTermMap termMap : childMapTerms) {

			if (termMap.getTermA().getId() == currentTerm.getId()
			        && StringUtils.equals(termMap.getConceptMapType().getUuid(),
			            ConceptManagementAppsConstants.SAME_AS_CONCEPT_MAP_TYPE_UUID)
			        && termMap.getTermB().getConceptSource() == conceptSource) {

				parentTerms.add(conceptService.getConceptReferenceTerm(termMap.getTermB().getId()));
			}
		}

		return parentTerms;
	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#uploadSpreadsheet(org.springframework.web.multipart.MultipartFile)
	 */
	@Transactional
	public FileDownload uploadSpreadsheet(MultipartFile spreadsheetFile) throws APIException {

		List<String> fileLines = new ArrayList<String>();
		ICsvMapReader mapReader = null;
		FileDownload fileShowingErrors = null;
		boolean hasErrors = false;
		String errorReason = null;

		try {

			mapReader = new CsvMapReader(new InputStreamReader(spreadsheetFile.getInputStream()),
			        CsvPreference.STANDARD_PREFERENCE);

			final String[] header = mapReader.getHeader(true);
			final CellProcessor[] processors = getSpreadsheetProcessors();

			String delimiter = ",";
			fileLines.add("errors - delete this column to resubmit" + delimiter + "map type" + delimiter + "source name"
			        + delimiter + "source code" + delimiter + "concept Id" + delimiter + "concept uuid" + delimiter
			        + "preferred name" + delimiter + "description" + delimiter + "class" + delimiter + "datatype"
			        + delimiter + "all existing mappings");

			for (Map<String, Object> mapList = mapReader.read(header, processors); mapList != null; mapList = mapReader
			        .read(header, processors)) {

				errorReason = " ";
				errorReason = getInitialErrorsBeforeTryingToSaveConcept(mapList);

				String line = mapReader.getUntokenizedRow();
				fileLines.add(errorReason + "," + line);

				if (StringUtils.isNotEmpty(errorReason) && StringUtils.isNotBlank(errorReason)) {
					hasErrors = true;
				}
			}
			fileShowingErrors = writeToFile(fileLines);

		}

		catch (APIException e) {
			log.error(e);
			throw new APIException("error on row " + mapReader.getRowNumber() + "," + mapReader.getUntokenizedRow() + e);
		}
		catch (FileNotFoundException e) {
			log.error(e);
		}
		catch (IOException e) {
			log.error(e);
		}

		finally {

			if (mapReader != null) {

				try {
					mapReader.close();
				}
				catch (IOException e) {
					log.error(e);
				}
			}
		}

		//if there are no errors then go ahead and pass through again and save
		if (!hasErrors) {

			setMapAndSaveConcept(spreadsheetFile);
			return null;
		}

		return fileShowingErrors;

	}

    @Override
    public void startManageSnomedCTProcess(String process, String dirPath, ConceptSource snomedSource, String conceptCode, int conceptClassId, String snomedConceptFilePath) throws APIException, FileNotFoundException {
            startManageSnomedCTProcess(process, dirPath, snomedSource);
    }

    /**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#startManageSnomedCTProcess(java.lang.String,
	 *      java.lang.String, org.openmrs.ConceptSource)
	 */
	@Transactional
	public void startManageSnomedCTProcess(String process, String snomedFileDirectory, ConceptSource snomedSource)
	    throws APIException, FileNotFoundException {

		try {

			snomedIndexFileDirectoryLocation = OpenmrsUtil.getApplicationDataDirectory() + "/tempLucene";

			currentSnomedCTProcess = new ManageSnomedCTProcess(process);
			currentSnomedCTProcess.setCurrentManageSnomedCTProcessDirectoryLocation(snomedFileDirectory);

			ConceptManagementAppsProperties cmap = new ConceptManagementAppsProperties();
			String snomedSourceUuid;
			if (snomedSource != null) {
				snomedSourceUuid = snomedSource.getUuid();
			} else {
				snomedSourceUuid = cmap
				        .getSnomedCTConceptSourceUuidGlobalProperty(ConceptManagementAppsConstants.SNOMED_CT_CONCEPT_SOURCE_UUID_GP);
			}

			indexSnomedFiles(snomedFileDirectory);

			if (process.contains("addSnomedCTNames")) {

				addNamesToSnomedCTTerms(snomedFileDirectory, snomedSourceUuid);
			}
			if (process.contains("addSnomedCTAncestors")) {

				addAncestorsToSnomedCTTerms(snomedFileDirectory, snomedSourceUuid);
			}
			if (process.contains("addSnomedCTRelationships")) {

				addRelationshipsToSnomedCTTerms(snomedFileDirectory, snomedSourceUuid);
			}
		}
		finally {
			try {
				FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));

			}
			catch (IOException e) {
				log.error("Error cleaning Lucene Index Directory ", e);
			}
		}

	}

	/**
	 * @see org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService#writeFileWithMissingConceptMappings(java.util.List,
	 *      java.io.PrintWriter, java.lang.String, java.lang.String)
	 */
	@Transactional(readOnly = true)
	public ICsvMapWriter writeFileWithMissingConceptMappings(List<Concept> conceptList, PrintWriter spreadsheetWriter,
	                                                         String mapTypeDefaultValue, String conceptSourceName)
	    throws Exception {
		Locale locale = Context.getLocale();

		final String[] header = new String[] { "map type", "source name", "source code", "concept Id", "concept uuid",
		        "preferred name", "description", "class", "datatype", "all existing mappings" };

		final Map<String, Object> conceptsMissingMappings = new HashMap<String, Object>();

		ICsvMapWriter mapWriter = null;

		try {

			mapWriter = new CsvMapWriter(spreadsheetWriter, CsvPreference.STANDARD_PREFERENCE);

			final CellProcessor[] downloadProcessors = getSpreadsheetProcessors();

			mapWriter.writeHeader(header);
			String mapTypeValue;
			if (StringUtils.isNotEmpty(mapTypeDefaultValue) && StringUtils.isNotBlank(mapTypeDefaultValue)) {
				mapTypeValue = mapTypeDefaultValue;

			} else {
				mapTypeValue = " ";
			}

			for (Concept concept : conceptList) {

				conceptsMissingMappings.put(header[0], mapTypeValue);
				conceptsMissingMappings.put(header[1], conceptSourceName);
				conceptsMissingMappings.put(header[2], " ");
				conceptsMissingMappings.put(header[3], concept.getConceptId());
				conceptsMissingMappings.put(header[4], concept.getUuid());

				if (concept.getPreferredName(locale) != null) {
					conceptsMissingMappings.put(header[5], concept.getPreferredName(locale));
				} else if (concept.getName() != null) {
					conceptsMissingMappings.put(header[5], concept.getName());
				} else {
					conceptsMissingMappings.put(header[5], " ");
				}

				if (concept.getDescription(locale) != null) {
					ConceptDescription cd = concept.getDescription(locale);
					conceptsMissingMappings.put(header[6], cd.getDescription());
				} else {
					conceptsMissingMappings.put(header[6], " ");
				}

				conceptsMissingMappings.put(header[7], concept.getConceptClass().getName());
				conceptsMissingMappings.put(header[8], concept.getDatatype().getName());

				String mappingsName = "  ";
				for (ConceptMap cm : concept.getConceptMappings()) {
					if (cm.getConceptMapType() != null) {
						mappingsName += cm.getConceptMapType().getName() + " ";
					}
					if (cm.getConceptReferenceTerm() != null && cm.getConceptReferenceTerm().getConceptSource() != null) {
						mappingsName += cm.getConceptReferenceTerm().getConceptSource().getName() + " "
						        + cm.getConceptReferenceTerm().getCode() + "\n";
					}
				}

				//strip new line off of last entry
				mappingsName = mappingsName.substring(0, mappingsName.length() - 1);
				conceptsMissingMappings.put(header[9], mappingsName);

				// write the conceptsMissingMappings maps
				mapWriter.write(conceptsMissingMappings, header, downloadProcessors);
			}
		}
		finally {
			if (mapWriter != null) {
				mapWriter.close();
			}
			if (spreadsheetWriter != null) {
				spreadsheetWriter.close();
			}

		}

		return mapWriter;

	}

	private FileDownload writeToFile(List<String> lines) {
		String linesShowingIfThereAreErrors = "";

		for (String aline : lines) {

			linesShowingIfThereAreErrors += aline + "\n";
		}

		String theDate = new SimpleDateFormat("dMy_Hm").format(new Date());
		String contentType = "text/csv;charset=UTF-8";
		String errorFilename = "conceptsMissingMappingsErrors" + theDate + ".csv";

		return new FileDownload(errorFilename, contentType, linesShowingIfThereAreErrors.getBytes());
	}

	/**
	 * Adds the ancestors to the SNOMED CT terms. Uses the International Release RF2 version from
	 * Unified Medical Language System® (UMLS®) to find the children contained in the
	 * /SnomedCT_Release_INT_20130131/RF2Release/Full/Terminology/ Relationship file
	 *
	 * @param snomedFileDirectory
	 * @param snomedSourceUuid
	 * @throws APIException
	 */
	private void addAncestorsToSnomedCTTerms(String snomedFileDirectory, String snomedSourceUuid) throws APIException {

		ConceptService cs = Context.getConceptService();

		ConceptSource snomedSource = cs.getConceptSourceByUuid(snomedSourceUuid);

		List<ConceptReferenceTerm> sourceRefTerms = getConceptReferenceTermsWithSpecifiedSourceIfIncluded(snomedSource, 0,
		    -1, "code", 1);
		List<ConceptReferenceTerm> listOfTermsToSave = new ArrayList<ConceptReferenceTerm>();

		Set<Long> listOfNewTermIds = new HashSet<Long>();
		Set<Integer> listOfDocIds = new HashSet<Integer>();
		Set<Integer> listOfExistingIds = new HashSet<Integer>();
		Set<ConceptReferenceTerm> listOfNewTerms = new HashSet<ConceptReferenceTerm>();

		IndexReader reader = null;

		try {
			reader = DirectoryReader.open(FSDirectory.open(new File(snomedIndexFileDirectoryLocation)));
			IndexSearcher searcher = new IndexSearcher(reader);

			for (ConceptReferenceTerm term : sourceRefTerms) {
				if (!getManageSnomedCTProcessCancelled()) {
					Set<Integer> tmpListOfDocIds = new HashSet<Integer>();

					tmpListOfDocIds = searchIndexesGetAncestorTermIds(term.getCode(), listOfNewTermIds, listOfDocIds,
					    snomedSource, searcher);

					listOfExistingIds.add(term.getCode().hashCode());

					listOfDocIds.addAll(tmpListOfDocIds);
				} else {
					return;
				}

			}

			listOfNewTerms = createNewTerms(listOfDocIds, searcher, snomedSource, listOfExistingIds);
			listOfTermsToSave.addAll(listOfNewTerms);

			if (listOfTermsToSave != null) {
				saveNewOrUpdatedRefTerms(listOfTermsToSave, cs);
			}

		}
		catch (IOException e) {
			log.error("Error Adding Ancestors ", e);
		}
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
				FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));

			}
			catch (IOException e) {
				log.error("Error Adding Ancestors ", e);
			}
		}

	}

	/**
	 * Adds the relationships to the SNOMED CT terms. Uses the International Release RF2 version
	 * from Unified Medical Language System® (UMLS®) to find the children contained in the
	 * /SnomedCT_Release_INT_20130131/RF2Release/Full/Terminology/ Relationship file
	 *
	 * @param snomedFileDirectory
	 * @param snomedSourceUuid
	 * @throws APIException
	 */
	private void addRelationshipsToSnomedCTTerms(String snomedFileDirectory, String snomedSourceUuid) throws APIException {

		ConceptService cs = Context.getConceptService();

		ConceptSource snomedSource = cs.getConceptSourceByUuid(snomedSourceUuid);
		ConceptMapType snomedMapType = cs
		        .getConceptMapTypeByUuid(ConceptManagementAppsConstants.SAME_AS_CONCEPT_MAP_TYPE_UUID);

		List<ConceptReferenceTerm> listOfMappedTerms = new ArrayList<ConceptReferenceTerm>();

		IndexReader reader = null;

		try {
			reader = DirectoryReader.open(FSDirectory.open(new File(snomedIndexFileDirectoryLocation)));
			IndexSearcher searcher = new IndexSearcher(reader);

			List<ConceptReferenceTerm> sourceRefTermsNew = getConceptReferenceTermsWithSpecifiedSourceIfIncluded(
			    snomedSource, 0, -1, "code", 1);
			listOfMappedTerms = createNewMappings(sourceRefTermsNew, searcher, snomedMapType);
			if (listOfMappedTerms != null) {
				saveNewOrUpdatedRefTerms(listOfMappedTerms, cs);
			}

		}
		catch (IOException e) {

		}
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
				FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));

			}
			catch (IOException e) {
				log.error("Error Adding Parents ", e);
			}
		}

	}

	/**
	 * Adds the names to the SNOMED CT terms. Uses the International Release RF2 version from
	 * Unified Medical Language System® (UMLS®) to find the names contained in the
	 * /SnomedCT_Release_INT_20130131/RF2Release/Full/Terminology/ Description file
	 *
	 * @param snomedFileDirectory
	 * @param snomedSourceUuid
	 * @throws APIException
	 */
    void addNamesToSnomedCTTerms(String snomedFileDirectory, String snomedSourceUuid) throws APIException {

		ConceptService cs = Context.getConceptService();

		ConceptSource snomedSource = cs.getConceptSourceByUuid(snomedSourceUuid);

		List<ConceptReferenceTerm> sourceRefTerms = getConceptReferenceTermsWithSpecifiedSourceIfIncluded(snomedSource, 0,
		    -1, "code", 1);
		List<ConceptReferenceTerm> listOfUpdatedTerms = new ArrayList<ConceptReferenceTerm>();

		IndexReader reader = null;

		try {

			reader = DirectoryReader.open(FSDirectory.open(new File(snomedIndexFileDirectoryLocation)));
			IndexSearcher searcher = new IndexSearcher(reader);

			listOfUpdatedTerms = addNamesToAllReferenceTerms(sourceRefTerms, searcher);
			if (listOfUpdatedTerms != null) {
				saveNewOrUpdatedRefTerms(listOfUpdatedTerms, cs);
			}

		}
		catch (IOException e) {

			log.error("Error Adding Names ", e);
		}
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
				FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));

			}
			catch (IOException e) {
				log.error("Error Adding Names ", e);
			}
		}

	}

	private void saveNewOrUpdatedRefTerms(List<ConceptReferenceTerm> listOfTerms, ConceptService cs) {

		int batchSize = 0;

		currentSnomedCTProcess.setCurrentManageSnomedCTProcessNumToProcess(listOfTerms.size());

		for (ConceptReferenceTerm termToSave : listOfTerms) {
			if (!getManageSnomedCTProcessCancelled()) {
				cs.saveConceptReferenceTerm(termToSave);

				batchSize++;

				currentSnomedCTProcess.setCurrentManageSnomedCTProcessNumProcessed(batchSize);

				if (batchSize % 20 == 0) {

					Context.flushSession();

				}
			} else {
				return;
			}
		}

	}

    void indexSnomedFiles(String snomedFiles) throws FileNotFoundException {
        if (!getManageSnomedCTProcessCancelled()) {

            BufferedReader br = null;
            IndexWriter writer = null;

            try {

                File file = new File(snomedFiles);

                //check to make sure other processes have had time to clear the directory before starting to index if it takes too long something may be wrong and we should throw an exception
                int tries = 0;
                while (FSDirectory.listAll(new File(snomedIndexFileDirectoryLocation)).length > 0) {
                    if (tries > 5) {
                        throw new Exception("index directory is not empty or is locked");
                    }
                    Thread.sleep(5000);
                    tries++;
                }

                FSDirectory dir = FSDirectory.open(new File(snomedIndexFileDirectoryLocation));

                if (file.listFiles() == null) {
                    throw new FileNotFoundException("Error finding SNOMED CT files. Please check the path.");
                }

                for (File f : file.listFiles()) {

                    dir.clearLock(IndexWriter.WRITE_LOCK_NAME);

                    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_4_10_4, analyzer);

                    writer = new IndexWriter(dir, config);

                    if (StringUtils.contains(f.getName(), RELATIONSHIP_FILE)) {

                        br = new BufferedReader(new FileReader(f));

                        for (String line = br.readLine(); line != null; line = br.readLine()) {

                            String[] fileFields = line.split("\t");

                            if (fileFields[0].length() > 0) {

                            }
                            //is term active and an IS-A relationship
                            if (StringUtils.equalsIgnoreCase(fileFields[2], RETIRED_TERM)) {

                                Document doc = new Document();

                                //get the row id, the parent term, and the child term
                                doc.add(new StringField(ROW_ID, fileFields[0], Field.Store.YES));
                                doc.add(new StringField(PARENT_TERM, fileFields[5], Field.Store.YES));
                                doc.add(new StringField(CHILD_TERM, fileFields[4], Field.Store.YES));
                                doc.add(new StringField(RELATIONSHIP_ID, fileFields[7], Field.Store.YES));
                                writer.addDocument(doc);

                            }
                        }
                    }
                    if (StringUtils.contains(f.getName(), DESCRIPTION_FILE)) {

                        br = new BufferedReader(new FileReader(f));

                        for (String line = br.readLine(); line != null; line = br.readLine()) {

                            String[] fileFields = line.split("\t");

                            //is the term active
                            if (fileFields[0].length() > 0) {
                                if (StringUtils.equalsIgnoreCase(fileFields[2], RETIRED_TERM)) {

                                    Document doc = new Document();

                                    //get the term id, the name, and the effective date(effective date for finding out if it is the newest)
                                    doc.add(new StringField(TERM_ID, fileFields[4], Field.Store.YES));
                                    doc.add(new StringField(TERM_NAME, fileFields[7], Field.Store.YES));
                                    doc.add(new StringField(DESCRIPTION_TYPE_ID, fileFields[6], Field.Store.YES));
                                    doc.add(new StringField(EFFECTIVE_DATE, fileFields[1], Field.Store.YES));
                                    writer.addDocument(doc);

                                }
                            }

                        }
                    }

                    writer.close();
                }
            } catch (FileNotFoundException e) {
                log.error("Error Indexing Snomed Files: File Not Found", e);
                throw new FileNotFoundException("Error finding SNOMED CT files. Please check the path. " + e);

            } catch (IOException e) {
                log.error("Error Indexing Snomed Files ", e);
            } catch (Exception e) {
                log.error("Error Indexing Snomed Files ", e);
            } finally {
                try {
                    if (br != null) {
                        br.close();
                    }
                    if (writer != null) {
                        writer.close();
                    }
                } catch (IOException e) {
                    log.error("Error Indexing Snomed Files: trying to close buffered reader ", e);
                }
            }
        } else {
            return;
        }
    }

	private Set<Integer> searchIndexesGetAncestorTermIds(String termId, Set<Long> listOfNewTermIds,
	                                                     Set<Integer> listOfDocIds, ConceptSource conceptSource,
	                                                     IndexSearcher searcher) {
		try {

			TopScoreDocCollector sourceIdCollector = TopScoreDocCollector.create(1000, true);
			Query sourceIdQuery = new QueryParser(Version.LUCENE_44, PARENT_TERM, analyzer).parse(termId);
			searcher.search(sourceIdQuery, sourceIdCollector);
			ScoreDoc[] hits = sourceIdCollector.topDocs().scoreDocs;

			for (int i = 0; i < hits.length; ++i) {
				if (!getManageSnomedCTProcessCancelled()) {
					int docId = hits[i].doc;
					Document d = searcher.doc(docId);

					Long id = Long.valueOf(d.get(ROW_ID)).longValue();
					String childIdString = d.get(CHILD_TERM);

					int listSizeBefore = listOfNewTermIds.size();
					listOfNewTermIds.add(id);
					listOfDocIds.add(docId);
					int listSizeAfter = listOfNewTermIds.size();
					if (listSizeAfter > listSizeBefore) {

						searchIndexesGetAncestorTermIds(childIdString, listOfNewTermIds, listOfDocIds, conceptSource,
						    searcher);

					}
				} else {
					return null;
				}
			}

		}
		catch (Exception e) {
			log.error(e);
		}

		return listOfDocIds;

	}

	private Set<ConceptReferenceTerm> createNewTerms(Set<Integer> listOfDocIds, IndexSearcher searcher,
	                                                 ConceptSource conceptSource, Set<Integer> listOfExistingIds) {

		Set<ConceptReferenceTerm> listOfNewTerms = new HashSet<ConceptReferenceTerm>();
		Set<String> listOfAlreadyAddedTerms = new HashSet<String>();

		for (Integer docId : listOfDocIds) {
			if (!getManageSnomedCTProcessCancelled()) {
				Document termIds;
				try {
					termIds = searcher.doc(docId.intValue());

					String termCode = termIds.get(PARENT_TERM);

					int beforeSize = listOfAlreadyAddedTerms.size();
					listOfAlreadyAddedTerms.add(termIds.get(PARENT_TERM));
					int afterSize = listOfAlreadyAddedTerms.size();

					if (!listOfExistingIds.contains(termIds.get(PARENT_TERM).hashCode()) && beforeSize < afterSize) {
						ConceptReferenceTerm newChildTerm = new ConceptReferenceTerm();

						newChildTerm.setCode(termCode);
						newChildTerm.setConceptSource(conceptSource);
						newChildTerm = addNameToReferenceTerm(newChildTerm, searcher);

						listOfNewTerms.add(newChildTerm);

					}

					String childTermCode = termIds.get(CHILD_TERM);

					beforeSize = listOfAlreadyAddedTerms.size();
					listOfAlreadyAddedTerms.add(termIds.get(CHILD_TERM));
					afterSize = listOfAlreadyAddedTerms.size();

					if (!listOfExistingIds.contains(termIds.get(CHILD_TERM).hashCode()) && beforeSize < afterSize) {
						ConceptReferenceTerm newChildTerm = new ConceptReferenceTerm();

						newChildTerm.setCode(childTermCode);
						newChildTerm.setConceptSource(conceptSource);
						newChildTerm = addNameToReferenceTerm(newChildTerm, searcher);

						listOfNewTerms.add(newChildTerm);
					}

				}
				catch (IOException e) {
					log.error("Error Creating New Terms ", e);
				}
			} else {
				return null;
			}
		}
		return listOfNewTerms;

	}

	private Map<String, ConceptReferenceTerm> createConceptReferenceTermCodeHashMap(List<ConceptReferenceTerm> listOfExistingTerms) {

		Map<String, ConceptReferenceTerm> hashMapOfExistingTerms = new HashMap<String, ConceptReferenceTerm>();
		for (ConceptReferenceTerm term : listOfExistingTerms) {

			hashMapOfExistingTerms.put(term.getCode(), term);

		}

		return hashMapOfExistingTerms;

	}

	private List<ConceptReferenceTerm> createNewMappings(List<ConceptReferenceTerm> listOfExistingTerms,
	                                                     IndexSearcher searcher, ConceptMapType mapType) {

		List<ConceptReferenceTerm> listOfTermsWithNewMappings = new ArrayList<ConceptReferenceTerm>();
		Map<String, ConceptReferenceTerm> termHashMap = createConceptReferenceTermCodeHashMap(listOfExistingTerms);

		try {

			for (ConceptReferenceTerm term : listOfExistingTerms) {
				if (!getManageSnomedCTProcessCancelled()) {
					boolean mapAdded = false;
					Set<Long> listOfTermIdsAlreadyMapped = new HashSet<Long>();

					TopScoreDocCollector sourceIdCollector = TopScoreDocCollector.create(1000, true);
					Query sourceIdQuery = new QueryParser(Version.LUCENE_44, PARENT_TERM, analyzer).parse(term.getCode());
					searcher.search(sourceIdQuery, sourceIdCollector);
					ScoreDoc[] hits = sourceIdCollector.topDocs().scoreDocs;

					for (int i = 0; i < hits.length; ++i) {

						int docId = hits[i].doc;
						Document termIds = searcher.doc(docId);

						int beforeSize = listOfTermIdsAlreadyMapped.size();
						listOfTermIdsAlreadyMapped.add(Long.parseLong(termIds.get(CHILD_TERM)));
						int afterSize = listOfTermIdsAlreadyMapped.size();

						if (beforeSize < afterSize) {
							ConceptReferenceTerm childTerm = termHashMap.get(termIds.get(CHILD_TERM));
							ConceptReferenceTermMap newMap = new ConceptReferenceTermMap();

							newMap.setConceptMapType(mapType);
							newMap.setTermA(term);
							newMap.setTermB(childTerm);

							term.addConceptReferenceTermMap(newMap);
							mapAdded = true;
						}
					}
					if (mapAdded) {
						listOfTermsWithNewMappings.add(term);
					}

				} else {
					return null;
				}
			}
		}

		catch (Exception e) {
			log.error("Error Creating New Mappings ", e);
		}
		return listOfTermsWithNewMappings;

	}

	private ConceptReferenceTerm addNameToReferenceTerm(ConceptReferenceTerm term, IndexSearcher searcher) {

		if (!getManageSnomedCTProcessCancelled()) {
			TopScoreDocCollector termCollector = TopScoreDocCollector.create(1000, true);
			String currentTermWithName = null;
			Document currentTermDoc = null;

			try {
				Query termQuery = new QueryParser(Version.LUCENE_44, TERM_ID, analyzer).parse(term.getCode());

				if (termQuery != null) {

					searcher.search(termQuery, termCollector);
					ScoreDoc[] termHits = termCollector.topDocs().scoreDocs;

					if (searcher != null && termHits.length > 0) {
						for (int i = 0; i < termHits.length; ++i) {

							int docId = termHits[i].doc;
							Document d = searcher.doc(docId);

							if (currentTermDoc == null) {

								currentTermDoc = searcher.doc(docId);
								currentTermWithName = currentTermDoc.get(TERM_NAME);

							} else {

								if (Integer.parseInt(d.get(EFFECTIVE_DATE)) > Integer.parseInt(currentTermDoc
								        .get(EFFECTIVE_DATE))) {

									currentTermDoc = d;
									currentTermWithName = d.get(TERM_NAME);

								}
							}

						}
						term.setName(currentTermWithName);
					}
				}
			}
			catch (org.apache.lucene.queryparser.classic.ParseException e) {
				log.error("Lucene Error Adding Names To Reference Term ", e);
			}
			catch (IOException e) {
				log.error("Error Adding Names To Reference Term ", e);
			}
			return term;

		} else {
			return null;
		}
	}

	private List<ConceptReferenceTerm> addNamesToAllReferenceTerms(List<ConceptReferenceTerm> terms, IndexSearcher searcher) {
		List<ConceptReferenceTerm> namedTerms = new ArrayList<ConceptReferenceTerm>();

		try {
			for (ConceptReferenceTerm term : terms) {
				if (!getManageSnomedCTProcessCancelled()) {
					String currentTermWithName = null;
					Document currentTermDoc = null;

					Query termQuery = new QueryParser(Version.LUCENE_44, TERM_ID, analyzer).parse(term.getCode());

					if (termQuery != null) {
						TopScoreDocCollector termCollector = TopScoreDocCollector.create(1000, true);
						searcher.search(termQuery, termCollector);
						ScoreDoc[] termHits = termCollector.topDocs().scoreDocs;

						if (searcher != null && termHits.length > 0) {
							for (int i = 0; i < termHits.length; ++i) {

								int docId = termHits[i].doc;
								Document d = searcher.doc(docId);

								if (currentTermDoc == null) {

									currentTermDoc = searcher.doc(docId);
									currentTermWithName = currentTermDoc.get(TERM_NAME);

								} else {

									if (Integer.parseInt(d.get(EFFECTIVE_DATE)) > Integer.parseInt(currentTermDoc
									        .get(EFFECTIVE_DATE))) {

										currentTermDoc = d;
										currentTermWithName = d.get(TERM_NAME);

									}
								}

							}

							term.setName(currentTermWithName);
							namedTerms.add(term);
						}
					}

				} else {
					return null;
				}
			}

		}
		catch (org.apache.lucene.queryparser.classic.ParseException e) {
			log.error("Adding Names To All Reference Terms ", e);
		}
		catch (IOException e) {
			log.error("Error Adding Names To All Reference Terms ", e);
		}
		return namedTerms;

	}

	/**
	 * Sets up the processors used for the spreadsheet to download.
	 *
	 * @return the cell processors
	 */
	private static CellProcessor[] getSpreadsheetProcessors() {

		final CellProcessor[] processors = new CellProcessor[] { new Optional(), // map type
		        new Optional(), // source name
		        new Optional(), // source code
		        new Optional(), // concept id
		        new Optional(), // concept uuid
		        new Optional(), // preferred name
		        new Optional(), // description
		        new Optional(), // class
		        new Optional(), // datatype
		        new Optional() // all existing mappings
		};

		return processors;
	}

	/**
	 * Goes through and checks for blanks and other validations that can be done before sending to
	 * the database to be saved
	 *
	 * @param mapList
	 * @return String of error message
	 */
	private String getInitialErrorsBeforeTryingToSaveConcept(Map<String, Object> mapList) {
		String errorString = "";
		ConceptService cs = Context.getConceptService();
		if (isMapTypeNull(mapList)) {
			errorString = " " + errorString
			        + Context.getMessageSourceService().getMessage("conceptmanagementapps.file.maptype.error") + " ";
		}

		if (isSourceNameNull(mapList)) {
			errorString = " " + errorString
			        + Context.getMessageSourceService().getMessage("conceptmanagementapps.file.sourcename.error") + " ";

		}

		if (isSourceCodeNull(mapList)) {
			errorString = " " + errorString
			        + Context.getMessageSourceService().getMessage("conceptmanagementapps.file.sourcecode.error") + " ";

		}

		if (!isSourceNameNull(mapList)
		        && !isSourceCodeNull(mapList)
		        && cs.getConceptReferenceTermByCode((String) mapList.get("source code"),
		            cs.getConceptSourceByName((String) mapList.get("source name"))) == null) {
			errorString = " "
			        + errorString
			        + Context.getMessageSourceService().getMessage(
			            "conceptmanagementapps.file.getConceptReferenceTermByCode.error") + " ";

		}
		if (!isSourceNameNull(mapList) && cs.getConceptSourceByName((String) mapList.get("source name")) == null) {

			errorString = " "
			        + errorString
			        + Context.getMessageSourceService()
			                .getMessage("conceptmanagementapps.file.getConceptSourceByName.error") + " ";
		}

		if (!isMapTypeNull(mapList) && cs.getConceptMapTypeByName(((String) mapList.get("map type"))) == null) {
			errorString = " "
			        + errorString
			        + Context.getMessageSourceService().getMessage(
			            "conceptmanagementapps.file.getConceptMapTypeByName.error") + " ";

		}

		if (cs.getConcept(((String) mapList.get("concept Id"))) == null) {
			errorString = " " + errorString
			        + Context.getMessageSourceService().getMessage("conceptmanagementapps.file.getConcept.error") + " ";

		}

		return errorString;
	}

	private void setMapAndSaveConcept(MultipartFile spreadsheetFile) {
		ConceptService cs = Context.getConceptService();
		ICsvMapReader mapReader = null;
		try {

			mapReader = new CsvMapReader(new InputStreamReader(spreadsheetFile.getInputStream()),
			        CsvPreference.STANDARD_PREFERENCE);

			final String[] header = mapReader.getHeader(true);
			final CellProcessor[] processors = getSpreadsheetProcessors();

			for (Map<String, Object> mapList = mapReader.read(header, processors); mapList != null; mapList = mapReader
			        .read(header, processors)) {

				ConceptMap conceptMap = new ConceptMap();
				Collection<ConceptMap> conceptMappings;

				ConceptReferenceTerm refTerm = cs.getConceptReferenceTermByCode((String) mapList.get("source code"),
				    cs.getConceptSourceByName((String) mapList.get("source name")));

				conceptMap.setConceptReferenceTerm(refTerm);

				conceptMap.setConceptMapType(cs.getConceptMapTypeByName((String) mapList.get("map type")));

				if (cs.getConcept(((String) mapList.get("concept Id"))) != null) {

					Concept concept = cs.getConcept(((String) mapList.get("concept Id")));

					//see if concept has mappings we need to add to
					if (concept.getConceptMappings() == null) {

						List<ConceptMap> conceptMappingsList = new ArrayList<ConceptMap>();
						conceptMappingsList.add(conceptMap);
						concept.setConceptMappings(conceptMappingsList);

					} else {

						conceptMappings = concept.getConceptMappings();
						conceptMappings.add(conceptMap);
						concept.setConceptMappings(conceptMappings);

					}

					cs.saveConcept(concept);

				}
			}
		}

		catch (APIException e) {
			log.error(e);
			throw new APIException("error on row " + mapReader.getRowNumber() + "," + mapReader.getUntokenizedRow() + e);
		}
		catch (FileNotFoundException e) {
			log.error(e);
		}
		catch (IOException e) {
			log.error(e);
		}

		finally {

			if (mapReader != null) {

				try {
					mapReader.close();
				}
				catch (IOException e) {
					log.error(e);
				}
			}
		}

	}

	/**
	 * @return boolean for if sourcName is null or empty
	 */
	private boolean isSourceNameNull(Map<String, Object> mapList) {
		String sourceName = "";
		if (mapList.get("source name") == null) {
			return true;
		} else {
			sourceName = (String) mapList.get("source name");
		}
		if (StringUtils.isNotEmpty(sourceName) && StringUtils.isNotBlank(sourceName)) {
			return false;
		}
		return true;
	}

	/**
	 * @return boolean for if sourceCode is null or empty
	 */
	private boolean isSourceCodeNull(Map<String, Object> mapList) {
		String sourceCode = "";
		if (mapList.get("source code") == null) {
			return true;
		} else {
			sourceCode = (String) mapList.get("source code");
		}
		if (StringUtils.isNotEmpty(sourceCode) && StringUtils.isNotBlank(sourceCode)) {
			return false;
		}
		return true;
	}

	/**
	 * @return boolean for if mapType is null or empty
	 */
	private boolean isMapTypeNull(Map<String, Object> mapList) {
		String mapType = "";
		if (mapList.get("map type") == null) {
			return true;
		} else {
			mapType = (String) mapList.get("map type");
		}
		if (StringUtils.isNotEmpty(mapType) && StringUtils.isNotBlank(mapType)) {
			return false;
		}
		return true;
	}

}