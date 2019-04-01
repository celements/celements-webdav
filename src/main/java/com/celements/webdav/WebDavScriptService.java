package com.celements.webdav;

import static com.google.common.base.Strings.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.script.service.ScriptService;

import com.celements.model.context.ModelContext;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.celements.webdav.WebDavService.WebDavConnection;
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
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(path)) {
        WebDavConnection webDav = webDavService.connect();
        list = webDav.list(Paths.get(path));
      }
    } catch (Exception exc) {
      LOGGER.warn("list - failed for path [{}]", path, exc);
    }
    return list;
  }

  public DavResource get(String path) {
    DavResource resource = null;
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(path)) {
        WebDavConnection webDav = webDavService.connect();
        resource = webDav.get(Paths.get(path)).orNull();
      }
    } catch (Exception exc) {
      LOGGER.warn("list - failed for path [{}]", path, exc);
    }
    return resource;
  }

  public String loadAsString(String filePath) {
    String content = "";
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(filePath)) {
        WebDavConnection webDav = webDavService.connect();
        content = new String(webDav.load(Paths.get(filePath)));
      }
    } catch (Exception exc) {
      LOGGER.warn("load - failed for path [{}]", filePath, exc);
    }
    return content;
  }

  public void download(String filePath) {
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(filePath)) {
        Path path = Paths.get(filePath);
        WebDavConnection webDav = webDavService.connect();
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
      }
    } catch (Exception exc) {
      LOGGER.warn("download - failed for path [{}]", filePath, exc);
    }
  }

  public boolean create(String filePath, Attachment attachment) {
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(filePath) && (attachment != null)) {
        WebDavConnection webDav = webDavService.connect();
        webDav.create(Paths.get(filePath), attachment.getContent());
        return true;
      }
    } catch (Exception exc) {
      LOGGER.warn("create - failed for path [{}]", filePath, exc);
    }
    return false;
  }

  public boolean update(String filePath, Attachment attachment) {
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(filePath) && (attachment != null)) {
        WebDavConnection webDav = webDavService.connect();
        webDav.update(Paths.get(filePath), attachment.getContent());
        return true;
      }
    } catch (Exception exc) {
      LOGGER.warn("update - failed for path [{}]", filePath, exc);
    }
    return false;
  }

  public boolean createOrUpdate(String filePath, Attachment attachment) {
    try {
      if (rightsAccess.isLoggedIn() && !isNullOrEmpty(filePath) && (attachment != null)) {
        WebDavConnection webDav = webDavService.connect();
        webDav.createOrUpdate(Paths.get(filePath), attachment.getContent());
        return true;
      }
    } catch (Exception exc) {
      LOGGER.warn("createOrUpdate - failed for path [{}]", filePath, exc);
    }
    return false;
  }

  public boolean delete(final String path) {
    try {
      if (rightsAccess.isAdmin() && !isNullOrEmpty(path)) {
        WebDavConnection webDav = webDavService.connect();
        webDav.delete(Paths.get(path));
        return true;
      }
    } catch (Exception exc) {
      LOGGER.warn("delete - failed for path [{}]", path, exc);
    }
    return false;
  }

  public WebDavConnection debug() throws Exception {
    if (rightsAccess.isSuperAdmin()) {
      return webDavService.connect();
    }
    return null;
  }

}
