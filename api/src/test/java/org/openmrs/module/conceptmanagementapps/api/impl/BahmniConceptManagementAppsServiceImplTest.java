package org.openmrs.module.conceptmanagementapps.api.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openmrs.ConceptSource;
import org.openmrs.api.context.Context;
import org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService;
import org.openmrs.util.OpenmrsUtil;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class BahmniConceptManagementAppsServiceImplTest {
    @Before
    public void setUp() throws Exception {
        ConceptManagementAppsService conceptManagementAppsService = Mockito.spy(new ConceptManagementAppsServiceImpl());
        when(OpenmrsUtil.getApplicationDataDirectory()).thenReturn("/tmp");
        when(Context.getConceptService().getConceptSourceByName("SNOMED CT")).thenReturn(new ConceptSource(1));

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void startManageSnomedCTProcess() throws Exception {
    }

}