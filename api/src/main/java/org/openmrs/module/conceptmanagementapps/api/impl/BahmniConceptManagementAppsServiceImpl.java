/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p/>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p/>
 * Copyright (C) OpenMRS, LLC. All Rights Reserved.
 */
package org.openmrs.module.conceptmanagementapps.api.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.SimpleLayout;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.openmrs.*;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptNameType;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService;
import org.openmrs.module.conceptmanagementapps.api.ManageSnomedCTProcess;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * It is a default implementation of {@link ConceptManagementAppsService}.
 */
public class BahmniConceptManagementAppsServiceImpl extends ConceptManagementAppsServiceImpl {
    protected Log log = LogFactory.getLog(getClass());
    private static String FULLY_SPECIFIED_TYPE = "900000000000003001";
    private static String SYNONYM_TYPE = "900000000000013009";
    private static String CONCEPT_SOURCE = "SNOMED CT";
    private static String CONCEPT_SOURCE_TYPE = "SAME-AS";
    private static String SNOMED_DESCRIPTION_ATTRIBUTE = "SNOMED CT ATTRIBUTE";
    private static final Logger logger = Logger.getLogger(BahmniConceptManagementAppsServiceImpl.class);

    @Override
    @Transactional
    public void startManageSnomedCTProcess(String process, String snomedFileDirectory, ConceptSource snomedSource, String conceptCode, int conceptClassId, String snomedConceptsFilePath) throws APIException, FileNotFoundException {
        try {
            initLogging();
            snomedIndexFileDirectoryLocation = OpenmrsUtil.getApplicationDataDirectory() + "/tempLucene";
            currentSnomedCTProcess = new ManageSnomedCTProcess(process);
            currentSnomedCTProcess.setCurrentManageSnomedCTProcessDirectoryLocation(snomedFileDirectory);
            snomedSource = Context.getConceptService().getConceptSourceByName("SNOMED CT");
            indexSnomedFiles(snomedFileDirectory);
            importContent(process, snomedFileDirectory, snomedSource, conceptCode, conceptClassId, snomedConceptsFilePath);
            Logger.getLogger(BahmniConceptManagementAppsServiceImpl.class).getLoggerRepository().resetConfiguration();
        } finally {
            try {
                FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));
            } catch (IOException e) {
                log.error("Error cleaning Lucene Index Directory ", e);
            }
        }

    }

    private void indexRefsetFile(String snomedConceptsFilePath) {
        if (!getManageSnomedCTProcessCancelled()) {
            BufferedReader bfr = null;
            try {
                File file = new File(snomedConceptsFilePath);
                bfr = new BufferedReader(new FileReader(file));

                for (String line = bfr.readLine(); line != null; line = bfr.readLine()) {
                    String[] fileFields = line.split("\t");
                    ConceptClass conceptClass = Context.getConceptService().getConceptClassByName(fileFields[1]);
                    if (conceptClass == null) {
                        logger.info("Cannot create concept, concept class is invalid" + fileFields[1]);
                    } else {
                        importSnomedCTConcepts(fileFields[0], false, conceptClass.getId());
                    }
                }
            } catch (FileNotFoundException e) {
                log.error("Error Indexing Snomed Files: File Not Found", e);
            } catch (Exception e) {
                log.error("Error Indexing Snomed Files ", e);
            } finally {
                try {
                    if (bfr != null) {
                        bfr.close();
                    }
                } catch (IOException e) {
                    log.error("Error Indexing Snomed Files: trying to close buffered reader ", e);
                }
            }
        }
    }


    private void importContent(String process, String snomedFileDirectory, ConceptSource snomedSource, String conceptCode, int conceptClassId, String snomedConceptsFilePath) {
        if (process.contains("addSnomedCTConcepts")) {
            if (StringUtils.isNotBlank(snomedConceptsFilePath)) {
                indexRefsetFile(snomedConceptsFilePath);
            } else {
                importSnomedCTConcepts(conceptCode, false, conceptClassId);
            }
        } else if (process.contains("addSnomedCTAttributes")) {
            importSnomedCTConcepts(conceptCode, true, conceptClassId);
        } else if (process.contains("addSnomedCTRelationships")) {
            addRelationshipsToReferenceTerms();
        } else if (process.contains("addSnomedCTNames")) {
            addNamesToSnomedCTTerms(snomedFileDirectory, snomedSource.getUuid());
        }
    }

    private void saveMappingType(String conceptCode, String fullySpecifiedName) throws APIException {
        if (Context.getConceptService().getConceptMapTypeByName(fullySpecifiedName) == null) {
            ConceptMapType conceptMapType = new ConceptMapType();
            conceptMapType.setName(fullySpecifiedName);
            conceptMapType.setIsHidden(false);
            conceptMapType.setDescription(SNOMED_DESCRIPTION_ATTRIBUTE);
            Context.getConceptService().saveConceptMapType(conceptMapType);
            logger.info("Created concept Map Type " + fullySpecifiedName);
        }
        ConceptSource conceptSource = Context.getConceptService().getConceptSourceByName(CONCEPT_SOURCE);
        saveConceptReferenceTerm(conceptCode, conceptSource, fullySpecifiedName);
    }

    private void addRelationshipsToReferenceTerms() throws APIException {
        ConceptService cs = Context.getConceptService();
        ConceptSource snomedSource = cs.getConceptSourceByName(CONCEPT_SOURCE);
        IndexReader reader = null;

        try {
            reader = DirectoryReader.open(FSDirectory.open(new File(snomedIndexFileDirectoryLocation)));
            IndexSearcher searcher = new IndexSearcher(reader);
            List<ConceptReferenceTerm> sourceRefTermsNew = getConceptReferenceTermsWithSpecifiedSourceIfIncluded(
                    snomedSource, 0, -1, "code", 1);
            for (ConceptReferenceTerm conceptReferenceTerm : sourceRefTermsNew) {
                TopScoreDocCollector sourceIdCollector = TopScoreDocCollector.create(1000, true);
                Query sourceIdQuery = new QueryParser(CHILD_TERM, analyzer).parse(conceptReferenceTerm.getCode());
                searcher.search(sourceIdQuery, sourceIdCollector);
                ScoreDoc[] hits = sourceIdCollector.topDocs().scoreDocs;
                addReferenceTermMaps(cs, snomedSource, searcher, conceptReferenceTerm, hits);
                cs.saveConceptReferenceTerm(conceptReferenceTerm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));
            } catch (IOException e) {
                log.error("Error Adding relationships", e);
            }
        }
    }

    private void addReferenceTermMaps(ConceptService cs, ConceptSource snomedSource, IndexSearcher searcher, ConceptReferenceTerm conceptReferenceTerm, ScoreDoc[] hits) throws IOException {
        for (ScoreDoc hit : hits) {
            if (!getManageSnomedCTProcessCancelled()) {
                Document currentDoc = searcher.doc(hit.doc);
                ConceptReferenceTerm conceptReferenceTermB = cs.getConceptReferenceTermByCode(currentDoc.get(PARENT_TERM), snomedSource);

                if (conceptReferenceTermB != null) {
                    String code = currentDoc.get(RELATIONSHIP_ID);
                    ConceptReferenceTerm relationshipTerm = cs.getConceptReferenceTermByCode(code, snomedSource);
                    ConceptMapType conceptMapType = relationshipTerm == null ? null : cs.getConceptMapTypeByName(relationshipTerm.getName());
                    if (conceptMapType != null) {
                        saveReferenceTermMap(conceptReferenceTerm, conceptReferenceTermB, conceptMapType);
                    }else {
                        logger.error(String.format("Cannot find a map type for relationship id %s", code));
                    }
                }
            }
        }
    }

    private void saveReferenceTermMap(ConceptReferenceTerm conceptReferenceTerm, ConceptReferenceTerm conceptReferenceTermB, ConceptMapType conceptMapType) {
        ConceptReferenceTermMap conceptReferenceTermMap = new ConceptReferenceTermMap();
        conceptReferenceTermMap.setTermA(conceptReferenceTerm);
        conceptReferenceTermMap.setTermB(conceptReferenceTermB);
        conceptReferenceTermMap.setConceptMapType(conceptMapType);
        if (!isConceptReferenceTermMapPresent(conceptReferenceTerm, conceptReferenceTermMap)) {
            conceptReferenceTerm.addConceptReferenceTermMap(conceptReferenceTermMap);
        }
    }

    private boolean isConceptReferenceTermMapPresent(ConceptReferenceTerm conceptReferenceTerm, ConceptReferenceTermMap conceptReferenceTermMap) {
        for (ConceptReferenceTermMap referenceTermMap : conceptReferenceTerm.getConceptReferenceTermMaps()) {
            if (referenceTermMap.getTermB().equals(conceptReferenceTermMap.getTermB())) {
                return true;
            }
        }
        return false;
    }

    private void importSnomedCTConcepts(String conceptCode, Boolean isAttribute, int conceptClassId) {
        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(snomedIndexFileDirectoryLocation)));
            IndexSearcher searcher = new IndexSearcher(reader);
            saveSnomedCTConcept(conceptCode, null, searcher, false, isAttribute, conceptClassId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveChildConcepts(String conceptCode, IndexSearcher searcher, ArrayList<String> parentConcepts, Boolean isAttribute, int conceptClassId) {
        try {
            TopScoreDocCollector sourceIdCollectorOne = TopScoreDocCollector.create(1000, true);
            Query sourceIdQuery = new QueryParser(PARENT_TERM, analyzer).parse(conceptCode);
            searcher.search(sourceIdQuery, sourceIdCollectorOne);
            ScoreDoc[] hits = sourceIdCollectorOne.topDocs().scoreDocs;
            if (hits.length == 0) {
                return;
            }

            for (ScoreDoc hit : hits) {
                if (!getManageSnomedCTProcessCancelled()) {
                    int docId = hit.doc;
                    Document d = searcher.doc(docId);
                    if (StringUtils.equalsIgnoreCase(d.get(RELATIONSHIP_ID), IS_A_RELATIONSHIP)) {
                        String childIdString = d.get(CHILD_TERM);
                        parentConcepts.add(conceptCode);
                        if (!parentConcepts.contains(childIdString)) {
                            saveSnomedCTConcept(childIdString, conceptCode, searcher, true, isAttribute, conceptClassId);
                            saveChildConcepts(childIdString, searcher, parentConcepts, isAttribute, conceptClassId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void saveSnomedCTConcept(String conceptCode, String parentConceptCode, IndexSearcher searcher, Boolean isMember, Boolean isAttribute, int conceptClassId) throws APIException {

        if (!getManageSnomedCTProcessCancelled()) {
            try {
                TopScoreDocCollector termCollector = TopScoreDocCollector.create(1000, true);
                Query termQuery = new QueryParser(TERM_ID, analyzer).parse(conceptCode);
                if (termQuery != null) {
                    searcher.search(termQuery, termCollector);
                    ScoreDoc[] termHits = termCollector.topDocs().scoreDocs;

                    if (termHits.length > 0) {
                        String fullySpecifiedName = null;
                        List<String> synonyms = new ArrayList<>();
                        for (ScoreDoc termHit : termHits) {
                            int docId = termHit.doc;
                            Document currentTermDoc = searcher.doc(docId);
                            String descriptionType = currentTermDoc.get(DESCRIPTION_TYPE_ID);

                            String name = currentTermDoc.get(TERM_NAME);
                            if (descriptionType.equals(FULLY_SPECIFIED_TYPE)) {
                                fullySpecifiedName = name;
                            } else if (descriptionType.equals(SYNONYM_TYPE)) {
                                if (synonyms.stream().noneMatch(name::equalsIgnoreCase)) {
                                    /* The if block is to remove duplicate synonyms.
                                       For a few snomed codes(e.g. 39925003) there are duplicate synonyms &
                                       openmrs throws an error while saving the concept.
                                    */
                                    synonyms.add(name);
                                }
                            }
                        }
                        if (isAttribute) {
                            saveMappingType(conceptCode, fullySpecifiedName);
                        } else {
                            saveConcept(conceptCode, parentConceptCode, isMember, conceptClassId, fullySpecifiedName, synonyms);
                        }

                    }
                }
            } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                log.error("Parse error while adding concepts ", e);
            } catch (IOException e) {
                log.error("IO Exception while adding concepts", e);
            }
        }
    }

    private void saveConcept(String conceptCode, String parentConceptCode, Boolean isChildConcept, int conceptClassId, String fullySpecifiedName, List<String> synonyms) {
        ConceptSource conceptSource = Context.getConceptService().getConceptSourceByName(CONCEPT_SOURCE);
        ConceptMapType conceptMapType = Context.getConceptService().getConceptMapTypeByName(CONCEPT_SOURCE_TYPE);
        Concept concept = Context.getConceptService().getConceptByName(fullySpecifiedName);
        List<Concept> mappedConcepts = Context.getConceptService().getConceptsByMapping(conceptCode, CONCEPT_SOURCE);
        Concept mappedConcept = getMappedConcept(conceptMapType, mappedConcepts);

        if (concept == null && mappedConcept == null) {
            concept = createConcept(conceptCode, conceptSource, conceptMapType, conceptClassId, fullySpecifiedName, synonyms);
            logger.info("Created concept " + fullySpecifiedName + "(" + conceptCode + ")");
        }
        concept = getConcept(concept, mappedConcept);
        if (isChildConcept && concept != null) {
            Concept parentConcept = Context.getConceptService().getConceptByMapping(parentConceptCode, CONCEPT_SOURCE);
            if (parentConcept != null && !parentConcept.getSetMembers().contains(concept)) {
                parentConcept.setSet(true);
                parentConcept.addSetMember(concept);
                Context.getConceptService().saveConcept(parentConcept);
                logger.info("Added " + fullySpecifiedName + " as child to " + parentConcept.getName());
            }
        }
    }

    private Concept getConcept(Concept concept, Concept mappedConcept) {
        return concept != null ? concept : mappedConcept;
    }

    private Concept createConcept(String conceptCode, ConceptSource conceptSource, ConceptMapType conceptMapType, int conceptClassId, String fullySpecifiedName, List<String> synonyms) {
        ConceptService conceptService = Context.getConceptService();
        Concept concept = new Concept();
        ConceptName conceptName = new ConceptName();

        conceptName.setName(fullySpecifiedName);
        conceptName.setLocale(Locale.ENGLISH);
        conceptName.setConceptNameType(ConceptNameType.FULLY_SPECIFIED);
        concept.setFullySpecifiedName(conceptName);

        int semanticTagStart = StringUtils.lastIndexOf(fullySpecifiedName, "(");
        String nameWithoutSemanticTag = StringUtils.substring(fullySpecifiedName, 0, semanticTagStart);
        ConceptName shortName = new ConceptName();
        shortName.setName(StringUtils.trim(nameWithoutSemanticTag));
        shortName.setLocale(Locale.ENGLISH);
        shortName.setConceptNameType(ConceptNameType.SHORT);
        concept.setShortName(shortName);

        ConceptDatatype conceptDatatype = conceptService.getConceptDatatype(4);
        concept.setDatatype(conceptDatatype);

        ConceptClass conceptClass = conceptService.getConceptClass(conceptClassId);
        concept.setConceptClass(conceptClass);

        ConceptReferenceTerm conceptReferenceTerm = saveConceptReferenceTerm(conceptCode, conceptSource, fullySpecifiedName);

        ConceptMap conceptMap = new ConceptMap();
        conceptMap.setConcept(concept);
        conceptMap.setConceptReferenceTerm(conceptReferenceTerm);
        conceptMap.setConceptMapType(conceptMapType);
        concept.addConceptMapping(conceptMap);

        if (CollectionUtils.isNotEmpty(synonyms)) {
            for (String synonym : synonyms) {
                concept.addName(new ConceptName(synonym, Locale.ENGLISH));
            }
        }

        conceptService.saveConcept(concept);
        return concept;
    }

    private Concept getMappedConcept(ConceptMapType conceptMapType, List<Concept> mappedConcepts) {
        for (Concept concept : mappedConcepts) {
            for (ConceptMap conceptMap : concept.getConceptMappings()) {
                if (conceptMap.getConceptMapType().equals((conceptMapType))) {
                    return concept;
                }
            }
        }
        return null;
    }

    private ConceptReferenceTerm saveConceptReferenceTerm(String conceptCode, ConceptSource conceptSource, String fullySpecifiedName) {
        ConceptReferenceTerm conceptReferenceTerm = Context.getConceptService().getConceptReferenceTermByCode(conceptCode, conceptSource);
        if (conceptReferenceTerm == null) {
            conceptReferenceTerm = new ConceptReferenceTerm();
            conceptReferenceTerm.setCode(conceptCode);
            conceptReferenceTerm.setConceptSource(conceptSource);
            conceptReferenceTerm.setName(fullySpecifiedName);
            Context.getConceptService().saveConceptReferenceTerm(conceptReferenceTerm);
        }
        return conceptReferenceTerm;
    }

    private static void initLogging() {
        try {
            RollingFileAppender appender = new RollingFileAppender(new SimpleLayout(), "/var/log/openmrs/snomedConcepts.log", true);
            appender.setMaxFileSize("20MB");
            logger.addAppender(appender);

            logger.setAdditivity(false);
            logger.setLevel((Level) Level.DEBUG);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
