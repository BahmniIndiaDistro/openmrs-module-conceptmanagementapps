package org.openmrs.module.conceptmanagementapps.page.controller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptSource;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.conceptmanagementapps.api.ConceptManagementAppsService;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.supercsv.io.ICsvMapWriter;

public class DownloadSpreadsheetPageController {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	public ICsvMapWriter post(UiSessionContext sessionContext, HttpServletRequest request,
	                          @RequestParam("conceptClass") String[] classesToInclude,
	                          @RequestParam("sourceList") Integer sourceId, UiUtils ui, PageModel model,
	                          PageRequest pageRequest) throws IOException, Exception {
		
		String mapTypeDefaultValue = request.getParameter("mapTypeList");
		if (StringUtils.isNotEmpty(mapTypeDefaultValue) && StringUtils.isNotBlank(mapTypeDefaultValue)) {
			mapTypeDefaultValue = Context.getConceptService().getConceptMapType(Integer.valueOf(mapTypeDefaultValue))
			        .getName();
		} else {
			mapTypeDefaultValue = "";
		}
		
		ConceptManagementAppsService conceptManagementAppsService = (ConceptManagementAppsService) Context
		        .getService(ConceptManagementAppsService.class);
		
		ConceptService cs = Context.getConceptService();
		
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		List<ConceptClass> classList = Context.getConceptService().getAllConceptClasses();
		List<ConceptMapType> mapTypeList = Context.getConceptService().getActiveConceptMapTypes();
		model.addAttribute("sourceList", sourceList);
		model.addAttribute("classList", classList);
		model.addAttribute("mapTypeList", mapTypeList);
		
		String[] classArray = classesToInclude;
		List<ConceptClass> conceptClassesToInclude = new ArrayList<ConceptClass>();
		for (String classString : classArray) {
			conceptClassesToInclude.add(cs.getConceptClass(Integer.valueOf(classString.trim())));
		}
		
		List<Concept> conceptList = conceptManagementAppsService.getUnmappedConcepts(Context.getConceptService()
		        .getConceptSource(sourceId), conceptClassesToInclude);
		
		HttpServletResponse response = pageRequest.getResponse();
		response.setContentType("text/csv;charset=UTF-8");
		String theDate = new SimpleDateFormat("dMy_Hm").format(new Date());
		String filename = "conceptsMissingMappings" + theDate + ".csv";
		response.setHeader("Content-Disposition", "attachment; filename=" + filename);
		
		conceptManagementAppsService = (ConceptManagementAppsService) Context.getService(ConceptManagementAppsService.class);
		
		return conceptManagementAppsService.writeFileWithMissingConceptMappings(conceptList, response.getWriter(),
		    mapTypeDefaultValue, Context.getConceptService().getConceptSource(sourceId).getName());
		
	}
	
	public void get(UiSessionContext sessionContext, PageModel model, HttpServletResponse response) throws Exception {
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		List<ConceptClass> classList = Context.getConceptService().getAllConceptClasses();
		List<ConceptMapType> mapTypeList = Context.getConceptService().getActiveConceptMapTypes();
		model.addAttribute("sourceList", sourceList);
		model.addAttribute("classList", classList);
		model.addAttribute("mapTypeList", mapTypeList);
		
	}
	
}
