package com.celements.webdav;

import static com.google.common.base.Strings.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;

import com.celements.model.context.ModelContext;
import com.celements.rights.access.EAccessLevel;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.celements.webdav.WebDavService.WebDavConnection;
import com.celements.webdav.exception.DavResourceAccessException;
import com.github.sardine.DavResource;
import com.google.common.base.Optional;
import com.xpn.xwiki.api.Attachment;
import com.xpn.xwiki.web.XWikiResponse;

@Component("webdav")
public class WebDavScriptService implements ScriptService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavScriptService.class);

  @Requirement
  private WebDavService webDavService;

  @Requirement
  private IRightsAccessFacadeRole rightsAccess;

  @Requirement
  private ModelContext context;

  public List<DavResource> list(String path) {
    List<DavResource> list = new ArrayList<>();
    if (checkWebDavRights() && !isNullOrEmpty(path)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        list = webDav.list(Paths.get(path));
      } catch (DavResourceAccessException exc) {
        LOGGER.info("list - inaccessible resource [{}]", path, exc);
      } catch (Exception exc) {
        LOGGER.warn("list - failed for path [{}]", path, exc);
      }
    }
    return list;
  }

  public DavResource get(String path) {
    DavResource resource = null;
    if (checkWebDavRights() && !isNullOrEmpty(path)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        resource = webDav.get(Paths.get(path)).orNull();
      } catch (Exception exc) {
        LOGGER.warn("get - failed for path [{}]", path, exc);
      }
    }
    return resource;
  }

  public String loadAsString(String filePath) {
    String content = "";
    if (checkWebDavRights() && !isNullOrEmpty(filePath)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        content = new String(webDav.load(Paths.get(filePath)));
      } catch (DavResourceAccessException exc) {
        LOGGER.info("load - inaccessible resource [{}]", filePath, exc);
      } catch (Exception exc) {
        LOGGER.warn("load - failed for path [{}]", filePath, exc);
      }
    }
    return content;
  }

  public void download(String filePath) {
    if (checkWebDavRights() && !isNullOrEmpty(filePath)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        Path path = Paths.get(filePath);
        Optional<DavResource> resource = webDav.get(path);
        if (resource.isPresent() && !resource.get().isDirectory()) {
          byte[] content = webDav.load(path);
          XWikiResponse response = context.getResponse().get();
          response.setCharacterEncoding("");
          response.setContentType(resource.get().getContentType());
          response.addHeader("Content-disposition", "inline; filename=\"" + URLEncoder.encode(
              resource.get().getName(), StandardCharsets.UTF_8.name()) + "\"");
          response.setDateHeader("Last-Modified", new Date().getTime());
          response.setContentLength(content.length);
          response.getOutputStream().write(content);
        }
      } catch (DavResourceAccessException exc) {
        LOGGER.info("download - inaccessible resource [{}]", filePath, exc);
      } catch (Exception exc) {
        LOGGER.warn("download - failed for path [{}]", filePath, exc);
      }
    }
  }

  public boolean createDirectory(String dirPath) {
    if (checkWebDavRights() && !isNullOrEmpty(dirPath)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        webDav.createDirectory(Paths.get(dirPath));
        return true;
      } catch (DavResourceAccessException exc) {
        LOGGER.info("createDirectory - inaccessible resource [{}]", dirPath, exc);
      } catch (Exception exc) {
        LOGGER.warn("createDirectory - failed for path [{}]", dirPath, exc);
      }
    }
    return false;
  }

  public boolean create(String filePath, Attachment attachment) {
    if (checkWebDavRights() && !isNullOrEmpty(filePath) && (attachment != null)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        webDav.create(Paths.get(filePath), attachment.getContent());
        return true;
      } catch (DavResourceAccessException exc) {
        LOGGER.info("create - inaccessible resource [{}]", filePath, exc);
      } catch (Exception exc) {
        LOGGER.warn("create - failed for path [{}]", filePath, exc);
      }
    }
    return false;
  }

  public boolean update(String filePath, Attachment attachment) {
    if (checkWebDavRights() && !isNullOrEmpty(filePath) && (attachment != null)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        webDav.update(Paths.get(filePath), attachment.getContent());
        return true;
      } catch (DavResourceAccessException exc) {
        LOGGER.info("update - inaccessible resource [{}]", filePath, exc);
      } catch (Exception exc) {
        LOGGER.warn("update - failed for path [{}]", filePath, exc);
      }
    }
    return false;
  }

  public boolean createOrUpdate(String filePath, Attachment attachment) {
    if (checkWebDavRights() && !isNullOrEmpty(filePath) && (attachment != null)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        webDav.createOrUpdate(Paths.get(filePath), attachment.getContent());
        return true;
      } catch (DavResourceAccessException exc) {
        LOGGER.info("createOrUpdate - inaccessible resource [{}]", filePath, exc);
      } catch (Exception exc) {
        LOGGER.warn("createOrUpdate - failed for path [{}]", filePath, exc);
      }
    }
    return false;
  }

  public boolean delete(final String path) {
    if (checkWebDavRights() && !isNullOrEmpty(path)) {
      try {
        WebDavConnection webDav = webDavService.connect();
        webDav.delete(Paths.get(path));
        return true;
      } catch (DavResourceAccessException exc) {
        LOGGER.info("delete - inaccessible resource [{}]", path, exc);
      } catch (Exception exc) {
        LOGGER.warn("delete - failed for path [{}]", path, exc);
      }
    }
    return false;
  }

  public WebDavConnection debug() throws Exception {
    if (rightsAccess.isSuperAdmin()) {
      return webDavService.connect();
    }
    return null;
  }

  private boolean checkWebDavRights() {
    try {
      DocumentReference cfgDocRef = webDavService.getConfiguredRemoteLogin().getDocumentReference();
      return rightsAccess.hasAccessLevel(cfgDocRef, EAccessLevel.VIEW);
    } catch (ConfigurationException exc) {
      LOGGER.warn("checkWebDavRights failed", exc);
      return false;
    }
  }

}
