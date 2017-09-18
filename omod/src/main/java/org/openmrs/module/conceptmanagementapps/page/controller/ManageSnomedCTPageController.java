package org.openmrs.module.conceptmanagementapps.page.controller;

import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ConceptSource;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.conceptmanagementapps.ConceptManagementAppsConstants;
import org.openmrs.module.conceptmanagementapps.ConceptManagementAppsProperties;
import org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService;
import org.openmrs.module.conceptmanagementapps.api.ManageSnomedCTProcess;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ManageSnomedCTPageController {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	public String post(@RequestParam("snomedDirectoryLocation") String snomedFileDirectoryLocation,
	                   @RequestParam(value = "sourceList", required = false) String[] sourceIds, UiUtils ui,
	                   PageRequest pageRequest, HttpServletRequest request, PageModel model) {
		
		ConceptService conceptService = Context.getConceptService();
		
		ConceptSource snomedSource = conceptService.getConceptSource(Integer.parseInt(sourceIds[0]));
		
		String inputType = request.getParameter("inputType");
		String manageSnomedCTError = "";
		
		ConceptManagementAppsService conceptManagementAppsService = (ConceptManagementAppsService) Context
		        .getService(ConceptManagementAppsService.class);
		
		if (StringUtils.equalsIgnoreCase("cancel", inputType)) {
			System.out.println("cancelling");
			conceptManagementAppsService.setManageSnomedCTProcessCancelled(true);
			setValuesForNoProcessRunning(model, conceptManagementAppsService, snomedFileDirectoryLocation);
			
		} else if (StringUtils.equalsIgnoreCase("saveConfiguration", inputType)) {
			
			try {
				
				conceptManagementAppsService
				        .startManageSnomedCTProcess(inputType, snomedFileDirectoryLocation, snomedSource);
				setValuesForNoProcessRunning(model, conceptManagementAppsService, snomedFileDirectoryLocation);
			}
			catch (FileNotFoundException e) {
				manageSnomedCTError = "Files not found. Please check your file path.";
				log.error("File Not Found Exception: ", e);
			}
			catch (APIException e) {
				log.error("Error generated", e);
			}
			
		} else {
			
			conceptManagementAppsService.setManageSnomedCTProcessCancelled(false);
			try {
				
				conceptManagementAppsService
				        .startManageSnomedCTProcess(inputType, snomedFileDirectoryLocation, snomedSource);
			}
			catch (FileNotFoundException e) {
				manageSnomedCTError = "Files not found. Please check your file path.";
				log.error("File Not Found Exception: ", e);
			}
			catch (APIException e) {
				log.error("Error generated", e);
			}
			
			conceptManagementAppsService.setManageSnomedCTProcessCancelled(true);
			
			setValuesForNoProcessRunning(model, conceptManagementAppsService, snomedFileDirectoryLocation);
			
		}
		
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		int sourceId = 0;
		
//		ConceptManagementAppsProperties cmap = new ConceptManagementAppsProperties();
//		String snomedSourceUuid = cmap
//		        .getSnomedCTConceptSourceUuidGlobalProperty(ConceptManagementAppsConstants.SNOMED_CT_CONCEPT_SOURCE_UUID_GP);

        AdministrationService as = Context.getAdministrationService();
        String snomedSourceUuid = as.getGlobalProperty(ConceptManagementAppsConstants.SNOMED_CT_CONCEPT_SOURCE_UUID_GP);

        for (ConceptSource source : sourceList) {
			
			if (StringUtils.equals(source.getUuid(), snomedSourceUuid)) {
				sourceId = source.getId();
			}
		}
		
		model.addAttribute("sourceList", sourceList);
		model.addAttribute("sourceId", sourceId);
		model.addAttribute("manageSnomedCTError", manageSnomedCTError);
		if (StringUtils.isNotBlank(manageSnomedCTError) && StringUtils.isNotEmpty(manageSnomedCTError)) {
			return "redirect:/conceptmanagementapps/manageSnomedCT.page?manageSnomedCTError=" + manageSnomedCTError;
		} else {
			return "redirect:/conceptmanagementapps/manageSnomedCT.page#importSnomedCtContent";
		}
	}
	
	public void get(UiSessionContext sessionContext, PageModel model, HttpServletRequest request) throws Exception {
		
		ConceptManagementAppsService conceptManagementAppsService = (ConceptManagementAppsService) Context
		        .getService(ConceptManagementAppsService.class);
		
		String snomedFileDirectoryLocation = "";
		
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		int sourceId = 0;
		
//		ConceptManagementAppsProperties cmap = new ConceptManagementAppsProperties();
//		String snomedSourceUuid = cmap
//		        .getSnomedCTConceptSourceUuidGlobalProperty(ConceptManagementAppsConstants.SNOMED_CT_CONCEPT_SOURCE_UUID_GP);

        AdministrationService as = Context.getAdministrationService();
        String snomedSourceUuid = as.getGlobalProperty(ConceptManagementAppsConstants.SNOMED_CT_CONCEPT_SOURCE_UUID_GP);

		for (ConceptSource source : sourceList) {
			
			if (StringUtils.equals(source.getUuid(), snomedSourceUuid)) {
				sourceId = source.getId();
			}
		}
		String manageSnomedCTError = "";
		if (StringUtils.isNotBlank(request.getParameter("manageSnomedCTError"))
		        && StringUtils.isNotEmpty(request.getParameter("manageSnomedCTError"))) {
			manageSnomedCTError = request.getParameter("manageSnomedCTError");
		}
		
		model.addAttribute("sourceList", sourceList);
		model.addAttribute("sourceId", sourceId);
		model.addAttribute("manageSnomedCTError", manageSnomedCTError);
		
		if (conceptManagementAppsService.getManageSnomedCTProcessCancelled()) {
			
			setValuesForNoProcessRunning(model, conceptManagementAppsService, snomedFileDirectoryLocation);
			
		} else {
			
			if (conceptManagementAppsService.getCurrentSnomedCTProcess() != null) {
				
				setValuesForCurrentProcess(model, conceptManagementAppsService);
				
			} else {
				
				setValuesForNoProcessRunning(model, conceptManagementAppsService, snomedFileDirectoryLocation);
				
			}
		}
	}
	
	private void setValuesForNoProcessRunning(PageModel model, ConceptManagementAppsService conceptManagementAppsService,
	                                          String snomedFileDirectoryLocation) {
		
		ManageSnomedCTProcess currentProcess = conceptManagementAppsService.getCurrentSnomedCTProcess();
		
		String dirLocation = snomedFileDirectoryLocation;
		if (StringUtils.isEmpty(dirLocation) || StringUtils.isBlank(dirLocation)) {
			if (currentProcess != null) {
				if (StringUtils.isEmpty(currentProcess.getCurrentManageSnomedCTProcessDirectoryLocation())
				        || StringUtils.isBlank(currentProcess.getCurrentManageSnomedCTProcessDirectoryLocation())) {
					dirLocation = "";
				} else {
					dirLocation = currentProcess.getCurrentManageSnomedCTProcessDirectoryLocation();
				}
			} else {
				dirLocation = "";
			}
		}
		if (dirLocation.length() > 0) {
			model.addAttribute("configSaved", "configSaved");
		} else {
			model.addAttribute("configSaved", "configNotSaved");
		}
		model.addAttribute("processRunning", "none");
		model.addAttribute("processStatus", "");
		model.addAttribute("dirLocation", dirLocation);
		model.addAttribute("processPercentComplete", "");
	}
	
	private void setValuesForCurrentProcess(PageModel model, ConceptManagementAppsService conceptManagementAppsService) {
		
		ManageSnomedCTProcess currentProcess = conceptManagementAppsService.getCurrentSnomedCTProcess();
		
		int numToProcess = currentProcess.getCurrentManageSnomedCTProcessNumToProcess();
		int numProcessed = currentProcess.getCurrentManageSnomedCTProcessNumProcessed();
		float percentComplete = 0;
		
		if (numToProcess > 0) {
			percentComplete = (float) numProcessed / (float) numToProcess;
			percentComplete = percentComplete * 100;
		}
		
		Date timeStarted = currentProcess.getCurrentManageSnomedCTProcessStartTime();
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String formattedDate = dateFormat.format(timeStarted);
		
		long timeStartedMillis = currentProcess.getCurrentManageSnomedCTProcessTimeStartedMilliSecs();
		long timeNowMillis = System.currentTimeMillis();
		long timePassed = timeNowMillis - timeStartedMillis;
		
		String processStatus = "Current process: " + currentProcess.getCurrentManageSnomedCTProcessName()
		        + " </br> Started on " + formattedDate + " Running for " + timePassed / 1000 + " seconds.";
		model.addAttribute("processStatus", processStatus);
		model.addAttribute("processRunning", currentProcess.getCurrentManageSnomedCTProcessName());
		model.addAttribute("dirLocation", currentProcess.getCurrentManageSnomedCTProcessDirectoryLocation());
		model.addAttribute("processPercentComplete", Math.round(percentComplete) + "% Complete Processing " + numProcessed
		        + " of " + numToProcess);
		if (currentProcess.getCurrentManageSnomedCTProcessDirectoryLocation().length() > 0) {
			model.addAttribute("configSaved", "configSaved");
		} else {
			model.addAttribute("configSaved", "configNotSaved");
		}
	}
}
