package com.celements.webdav;

import static com.google.common.base.Strings.*;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.script.service.ScriptService;

import com.celements.rights.access.IRightsAccessFacadeRole;
import com.github.sardine.DavResource;
import com.xpn.xwiki.api.Attachment;
import com.xpn.xwiki.web.Utils;

@Component("webdav")
public class WebDavScriptService implements ScriptService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavScriptService.class);

  @Requirement
  private WebDavService webDavService;

  @Requirement
  private IRightsAccessFacadeRole rightsAccess;

  public List<DavResource> list(String path) {
    List<DavResource> list = new ArrayList<>();
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(path)) {
        list = webDavService.list(Paths.get(path));
      }
    } catch (Exception exc) {
      LOGGER.warn("list - failed for path [{}]", path, exc);
    }
    return list;
  }

  @NotNull
  byte[] load(String filePath) {
    byte[] content = new byte[0];
    try {
      if (rightsAccess.isAdmin() && !isNullOrEmpty(filePath)) {
        content = webDavService.load(Paths.get(filePath));
      }
    } catch (Exception exc) {
      LOGGER.warn("load - failed for path [{}]", filePath, exc);
    }
    return content;
  }

  boolean store(String filePath, Attachment attachment) {
    try {
      if (rightsAccess.isAdmin() && !isNullOrEmpty(filePath) && (attachment != null)) {
        return webDavService.store(Paths.get(filePath), attachment.getContent());
      }
    } catch (Exception exc) {
      LOGGER.warn("store - failed for path [{}]", filePath, exc);
    }
    return false;
  }

  boolean delete(@NotNull final String path) {
    try {
      if (rightsAccess.isAdmin() && !isNullOrEmpty(path)) {
        return webDavService.delete(Paths.get(path));
      }
    } catch (Exception exc) {
      LOGGER.warn("delete - failed for path [{}]", path, exc);
    }
    return false;
  }

  public SardineAdapter debugSardine() throws Exception {
    SardineAdapter sardine = null;
    if (rightsAccess.isSuperAdmin()) {
      sardine = (SardineAdapter) Utils.getComponent(WebDavService.class, SardineAdapter.NAME);
    }
    return sardine;
  }

}
