package org.openmrs.module.conceptmanagementapps.page.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ConceptSource;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class BrowseReferenceTermsTablePageController {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	public void post(UiSessionContext sessionContext, HttpServletRequest request, UiUtils ui, PageModel model,
	                 PageRequest pageRequest) {
	}
	
	public void get(UiSessionContext sessionContext, PageModel model) throws Exception {
		List<ConceptSource> sourceList = Context.getConceptService().getAllConceptSources(false);
		model.addAttribute("sourceList", sourceList);
		model.addAttribute("sourceId", 0);
		
	}

}