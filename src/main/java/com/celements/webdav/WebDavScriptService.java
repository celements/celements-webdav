package com.celements.webdav;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.script.service.ScriptService;

import com.celements.rights.access.IRightsAccessFacadeRole;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.xpn.xwiki.web.Utils;

@Component("webdav")
public class WebDavScriptService implements ScriptService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavScriptService.class);

  @Requirement
  private WebDavService webDavService;

  @Requirement
  private IRightsAccessFacadeRole rightsAccess;

  public List<Path> list(String path) {
    List<Path> list = new ArrayList<>();
    try {
      if (rightsAccess.isLoggedIn()) {
        list = webDavService.list(Paths.get(path));
      }
    } catch (Exception exc) {
      LOGGER.warn("list - failed for path [{}]", path, exc);
    }
    return list;
  }

  public Sardine debugSardine() throws Exception {
    Sardine sardine = null;
    if (rightsAccess.isSuperAdmin()) {
      sardine = getSardineAdapter().getSardine(webDavService.getConfiguredWebDavRemoteLogin());
    }
    return sardine;
  }

  public List<DavResource> debugSardineList(String path) throws Exception {
    List<DavResource> list = new ArrayList<>();
    if (rightsAccess.isSuperAdmin()) {
      list = getSardineAdapter().listInternal(Paths.get(path),
          webDavService.getConfiguredWebDavRemoteLogin());
    }
    return list;
  }

  private SardineAdapter getSardineAdapter() {
    return (SardineAdapter) Utils.getComponent(WebDavService.class, SardineAdapter.NAME);
  }

}
