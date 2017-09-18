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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptReferenceTermMap;
import org.openmrs.ConceptSource;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptNameType;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService;
import org.openmrs.module.conceptmanagementapps.api.ManageSnomedCTProcess;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * It is a default implementation of {@link ConceptManagementAppsService}.
 */
public class BahmniConceptManagementAppsServiceImpl extends ConceptManagementAppsServiceImpl {
    protected Log log = LogFactory.getLog(getClass());
    private static String FULLY_SPECIFIED_TYPE = "900000000000003001";
    private static String CONCEPT_SOURCE = "SNOMED CT";
    private static String CONCEPT_SOURCE_TYPE = "SAME-AS";
    private static String SNOMED_DESCRIPTION_ATTRIBUTE = "SNOMED CT ATTRIBUTE";

    @Override
    @Transactional
    public void startManageSnomedCTProcess(String process, String snomedFileDirectory, ConceptSource snomedSource, String conceptCode, int conceptClassId) throws APIException, FileNotFoundException {
        try {
            snomedIndexFileDirectoryLocation = OpenmrsUtil.getApplicationDataDirectory() + "/tempLucene";
            currentSnomedCTProcess = new ManageSnomedCTProcess(process);
            currentSnomedCTProcess.setCurrentManageSnomedCTProcessDirectoryLocation(snomedFileDirectory);
            snomedSource = Context.getConceptService().getConceptSourceByName("SNOMED CT");
            indexSnomedFiles(snomedFileDirectory);
            importContent(process, snomedFileDirectory, snomedSource, conceptCode, conceptClassId);
        } finally {
            try {
                FileUtils.cleanDirectory(new File(snomedIndexFileDirectoryLocation));
            } catch (IOException e) {
                log.error("Error cleaning Lucene Index Directory ", e);
            }
        }

    }

    private void importContent(String process, String snomedFileDirectory, ConceptSource snomedSource, String conceptCode, int conceptClassId) {
        if (process.contains("addSnomedCTConcepts")) {
            importSnomedCTConcepts(conceptCode, false, conceptClassId);
        } else if (process.contains("addSnomedCTAttributes")) {
            importSnomedCTConcepts(conceptCode, true, conceptClassId);
        } else if (process.contains("addSnomedCTRelationships")) {
            addRelationshipsToReferenceTerms();
        } else if (process.contains("addSnomedCTNames")) {
            addNamesToSnomedCTTerms(snomedFileDirectory, snomedSource.getUuid());
        }
    }

    private void saveMappingType(Document currentTermDoc) throws APIException {
        if (Context.getConceptService().getConceptMapTypeByName(currentTermDoc.get(TERM_NAME)) == null) {
            ConceptMapType conceptMapType = new ConceptMapType();
            conceptMapType.setName(currentTermDoc.get(TERM_NAME));
            conceptMapType.setIsHidden(false);
            conceptMapType.setDescription(SNOMED_DESCRIPTION_ATTRIBUTE);
            Context.getConceptService().saveConceptMapType(conceptMapType);
            log.info("Created concept Map Type" + currentTermDoc.get(TERM_NAME));
        }
        ConceptSource conceptSource = Context.getConceptService().getConceptSourceByName(CONCEPT_SOURCE);
        saveConceptReferenceTerm(currentTermDoc.get(TERM_ID), currentTermDoc, conceptSource);
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
                    ConceptReferenceTerm relationshipTerm = cs.getConceptReferenceTermByCode(currentDoc.get(RELATIONSHIP_ID), snomedSource);
                    ConceptMapType conceptMapType = relationshipTerm == null ? null : cs.getConceptMapTypeByName(relationshipTerm.getName());
                    if (conceptMapType != null) {
                        saveReferenceTermMap(conceptReferenceTerm, conceptReferenceTermB, conceptMapType);
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
            ArrayList<String> parentConcepts = new ArrayList<String>();
            saveSnomedCTConcept(conceptCode, null, searcher, false, isAttribute, conceptClassId);
            saveChildConcepts(conceptCode, searcher, parentConcepts, isAttribute, conceptClassId);
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
                            log.warn(childIdString);
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
                        for (ScoreDoc termHit : termHits) {
                            int docId = termHit.doc;
                            Document currentTermDoc = searcher.doc(docId);
                            String descriptionType = currentTermDoc.get(DESCRIPTION_TYPE_ID);

                            if (descriptionType.equals(FULLY_SPECIFIED_TYPE)) {
                                if (isAttribute) {
                                    saveMappingType(currentTermDoc);
                                } else {
                                    saveConcept(conceptCode, parentConceptCode, isMember, currentTermDoc, conceptClassId);
                                }
                            }
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

    private void saveConcept(String conceptCode, String parentConceptCode, Boolean isChildConcept, Document currentTermDoc, int conceptClassId) {
        ConceptSource conceptSource = Context.getConceptService().getConceptSourceByName(CONCEPT_SOURCE);
        ConceptMapType conceptMapType = Context.getConceptService().getConceptMapTypeByName(CONCEPT_SOURCE_TYPE);
        Concept concept = Context.getConceptService().getConceptByName(currentTermDoc.get(TERM_NAME));
        List<Concept> mappedConcepts = Context.getConceptService().getConceptsByMapping(conceptCode, CONCEPT_SOURCE);
        Concept mappedConcept = getMappedConcept(conceptMapType, mappedConcepts);

        if (concept == null && mappedConcept == null) {
            concept = createConcept(conceptCode, currentTermDoc, conceptSource, conceptMapType, conceptClassId);
            log.info("Created concept" + currentTermDoc.get(TERM_NAME));
        }
        concept = getConcept(concept, mappedConcept);
        if (isChildConcept && concept != null) {
            Concept parentConcept = Context.getConceptService().getConceptByMapping(parentConceptCode, CONCEPT_SOURCE);
            if (parentConcept != null && !parentConcept.getSetMembers().contains(concept)) {
                parentConcept.setSet(true);
                parentConcept.addSetMember(concept);
                Context.getConceptService().saveConcept(parentConcept);
                log.info("Added" + currentTermDoc.get(TERM_NAME) + "as child to" + parentConcept.getName());
            }
        }
    }

    private Concept getConcept(Concept concept, Concept mappedConcept) {
        return concept != null ? concept : mappedConcept;
    }

    private Concept createConcept(String conceptCode, Document currentTermDoc, ConceptSource conceptSource, ConceptMapType conceptMapType, int conceptClassId) {
        Concept concept = new Concept();
        ConceptName conceptName = new ConceptName();
        conceptName.setName(currentTermDoc.get(TERM_NAME));
        conceptName.setLocale(Locale.ENGLISH);
        conceptName.setConceptNameType(ConceptNameType.FULLY_SPECIFIED);
        concept.setFullySpecifiedName(conceptName);
        ConceptDatatype conceptDatatype = new ConceptDatatype();
        conceptDatatype.setConceptDatatypeId(4);
        concept.setDatatype(conceptDatatype);
        ConceptClass conceptClass = new ConceptClass();
        conceptClass.setConceptClassId(conceptClassId);
        concept.setConceptClass(conceptClass);

        ConceptReferenceTerm conceptReferenceTerm = saveConceptReferenceTerm(conceptCode, currentTermDoc, conceptSource);

        ConceptMap conceptMap = new ConceptMap();
        conceptMap.setConcept(concept);
        conceptMap.setConceptReferenceTerm(conceptReferenceTerm);
        conceptMap.setConceptMapType(conceptMapType);
        concept.addConceptMapping(conceptMap);

        Context.getConceptService().saveConcept(concept);
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

    private ConceptReferenceTerm saveConceptReferenceTerm(String conceptCode, Document currentTermDoc, ConceptSource conceptSource) {
        ConceptReferenceTerm conceptReferenceTerm = Context.getConceptService().getConceptReferenceTermByCode(conceptCode, conceptSource);
        if (conceptReferenceTerm == null) {
            conceptReferenceTerm = new ConceptReferenceTerm();
            conceptReferenceTerm.setCode(conceptCode);
            conceptReferenceTerm.setConceptSource(conceptSource);
            conceptReferenceTerm.setName(currentTermDoc.get(TERM_NAME));
            Context.getConceptService().saveConceptReferenceTerm(conceptReferenceTerm);
        }
        return conceptReferenceTerm;
    }

}
