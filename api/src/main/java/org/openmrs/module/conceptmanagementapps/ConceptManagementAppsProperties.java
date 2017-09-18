package org.openmrs.module.conceptmanagementapps;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.metadatamapping.util.ModuleProperties;


public class  ConceptManagementAppsProperties extends ModuleProperties{

	public String getSnomedCTConceptSourceUuidGlobalProperty(String globalProperty) {
		AdministrationService as = Context.getAdministrationService();
        return as.getGlobalProperty(globalProperty);
    }

    @Override
    public String getMetadataSourceName() {
        return "org.openmrs.module.emrapi";
    }
}