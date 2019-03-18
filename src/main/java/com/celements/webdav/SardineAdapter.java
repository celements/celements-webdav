package com.celements.webdav;

import static com.celements.model.util.ReferenceSerializationMode.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.context.Execution;

import com.celements.auth.classes.RemoteLoginClass;
import com.celements.model.classes.ClassDefinition;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.util.ModelUtils;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.xpn.xwiki.doc.XWikiDocument;

@Component
public class SardineAdapter implements WebDavService {

  static final String EC_KEY = "WebDAV.Sardine|";

  @Requirement(RemoteLoginClass.CLASS_DEF_HINT)
  ClassDefinition remoteLoginClass;

  @Requirement
  private Execution execution;

  @Requirement
  private ModelUtils modelUtils;

  public List<DavResource> list(Path path, XWikiDocument cfgDoc) throws WebDavException {
    XWikiObjectFetcher remoteLoginCfg = getRemoteLoginCfg(cfgDoc);
    String url = remoteLoginCfg.fetchField(RemoteLoginClass.FIELD_URL).first().get();
    URI uri = UriBuilder.fromUri(url).scheme("http").replacePath(
        path.normalize().toString()).build();
    try {
      return getSardine(cfgDoc).list(uri.toString());
    } catch (IOException exc) {
      throw new WebDavException("failed accessing " + uri, exc);
    }
  }

  /**
   * Currently we instance Sardine once per request. It could be safely used in a multithreaded
   * environment since it uses the org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager.
   * See <a href="https://github.com/lookfirst/sardine/wiki/UsageGuide#threading">Sardine Docu</a>.
   */
  private Sardine getSardine(XWikiDocument cfgDoc) throws WebDavException {
    XWikiObjectFetcher remoteLoginCfg = getRemoteLoginCfg(cfgDoc);
    String key = EC_KEY + modelUtils.serializeRef(cfgDoc.getDocumentReference(), GLOBAL);
    Sardine sardine = (Sardine) execution.getContext().getProperty(key);
    if (sardine == null) {
      String username = remoteLoginCfg.fetchField(RemoteLoginClass.FIELD_USERNAME).first().get();
      String password = remoteLoginCfg.fetchField(RemoteLoginClass.FIELD_PASSWORD).first().get();
      execution.getContext().setProperty(key, sardine = SardineFactory.begin(username, password));
      sardine.enableCompression();
    }
    return sardine;
  }

  // TODO replace with RemoteLogin POJO
  private XWikiObjectFetcher getRemoteLoginCfg(XWikiDocument cfgDoc) throws WebDavException {
    XWikiObjectFetcher remoteLoginCfg = XWikiObjectFetcher.on(cfgDoc).filter(remoteLoginClass);
    if (remoteLoginCfg.exists()) {
      return remoteLoginCfg;
    } else {
      throw new WebDavException("Illegal Config Doc: " + cfgDoc);
    }
  }

}
