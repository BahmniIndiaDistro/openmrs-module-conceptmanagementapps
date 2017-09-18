package org.openmrs.module.conceptmanagementapps.page.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;

public class AddTermPageController {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	public void post(UiSessionContext sessionContext, HttpServletRequest request,
	                 @RequestParam("refTermCode") String refTermCode, @RequestParam("sourceList") Integer sourceId,
	                 UiUtils ui, PageModel model, PageRequest pageRequest) {
		
		String refTermDescription = request.getParameter("refTermDescription");
		String refTermName = request.getParameter("refTermName");
		
		ConceptService conceptService = Context.getConceptService();
		
		ConceptReferenceTerm newReferenceTerm = new ConceptReferenceTerm();
		
		newReferenceTerm.setConceptSource(conceptService.getConceptSource(sourceId));
		
		newReferenceTerm.setCode(refTermCode);
		
		if (StringUtils.isNotEmpty(refTermDescription) && StringUtils.isNotBlank(refTermDescription)) {
			
			newReferenceTerm.setDescription(refTermDescription);
			
		}
		
		if (StringUtils.isNotEmpty(refTermName) && StringUtils.isNotBlank(refTermName)) {
			
			newReferenceTerm.setName(refTermName);
		}
		
		conceptService.saveConceptReferenceTerm(newReferenceTerm);
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		model.addAttribute("sourceList", sourceList);
	}
	
	public void get(UiSessionContext sessionContext, PageModel model) throws Exception {
		
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		model.addAttribute("sourceList", sourceList);
	}
	
}
