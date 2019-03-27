package com.celements.webdav;

import static com.celements.model.util.ReferenceSerializationMode.*;
import static com.google.common.base.Preconditions.*;
import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;

import com.celements.auth.RemoteLogin;
import com.celements.auth.classes.RemoteLoginClass;
import com.celements.configuration.CelementsFromWikiConfigurationSource;
import com.celements.configuration.ConfigSourceUtils;
import com.celements.convert.bean.XDocBeanLoader;
import com.celements.convert.bean.XDocBeanLoader.BeanLoadException;
import com.celements.model.classes.ClassDefinition;
import com.celements.model.context.ModelContext;
import com.celements.model.reference.RefBuilder;
import com.celements.model.util.ModelUtils;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;

@Component(SardineAdapter.NAME)
public class SardineAdapter implements WebDavService, Initializable {

  public static final String NAME = "sardine";

  static final String EC_KEY = "WebDAV.Sardine";

  @Requirement(RemoteLoginClass.CLASS_DEF_HINT)
  ClassDefinition remoteLoginClass;

  @Requirement
  private Execution execution;

  @Requirement(CelementsFromWikiConfigurationSource.NAME)
  private ConfigurationSource cfgSrc;

  @Requirement
  private ModelUtils modelUtils;

  @Requirement
  private ModelContext context;

  @Requirement
  private XDocBeanLoader<RemoteLogin> remoteLoginLoader;

  @Override
  public void initialize() throws InitializationException {
    remoteLoginLoader.initialize(RemoteLogin.class, remoteLoginClass);
  }

  @Override
  public List<Path> list(Path path) throws WebDavException {
    return list(path, getConfiguredWebDavRemoteLogin());
  }

  @Override
  public List<Path> list(Path path, RemoteLogin remoteLogin) throws WebDavException {
    List<Path> ret = new ArrayList<>();
    for (DavResource resource : listInternal(path, remoteLogin)) {
      ret.add(Paths.get(resource.getPath()));
    }
    return ret;
  }

  List<DavResource> listInternal(Path path, RemoteLogin remoteLogin) throws WebDavException {
    URL url = buildCompleteUrl(remoteLogin, path);
    try {
      return getSardine(remoteLogin).list(url.toExternalForm());
    } catch (IOException | GeneralSecurityException exc) {
      throw new WebDavException(format("failed for url [{0}] with login [{1}] ", url, remoteLogin),
          exc);
    }
  }

  private URL buildCompleteUrl(RemoteLogin remoteLogin, Path path) throws WebDavException {
    checkNotNull(remoteLogin);
    checkNotNull(path);
    try {
      return UriBuilder.fromUri(remoteLogin.getUrl()).path(
          path.normalize().toString()).build().toURL();
    } catch (MalformedURLException | IllegalArgumentException | UriBuilderException exc) {
      throw new WebDavException(format("unable to build url with base [{0}] and path [{1}]",
          remoteLogin.getUrl(), path), exc);
    }
  }

  /**
   * Currently we instance Sardine once per request. It could be safely used in a multithreaded
   * environment since it uses the org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager.
   * See <a href="https://github.com/lookfirst/sardine/wiki/UsageGuide#threading">Sardine Docu</a>.
   */
  Sardine getSardine(RemoteLogin remoteLogin) throws IOException, GeneralSecurityException {
    checkNotNull(remoteLogin);
    String key = EC_KEY;
    if (remoteLogin.getDocumentReference() != null) {
      key += "|" + modelUtils.serializeRef(remoteLogin.getDocumentReference(), GLOBAL);
    }
    Sardine sardine = (Sardine) execution.getContext().getProperty(key);
    if (sardine == null) {
      execution.getContext().setProperty(key, sardine = newSardine(remoteLogin));
    }
    return sardine;
  }

  private Sardine newSardine(RemoteLogin remoteLogin) throws IOException, GeneralSecurityException {
    String cacertsPath = cfgSrc.getProperty("celements.security.cacerts");
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(context.getXWikiContext().getWiki().getResourceAsStream(cacertsPath), null);
    final ConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
        SSLContexts.custom().loadTrustMaterial(trustStore, new TrustSelfSignedStrategy()).build());
    Sardine sardine = new SardineImpl(remoteLogin.getUsername(), remoteLogin.getPassword()) {

      @Override
      protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
        return checkNotNull(socketFactory);
      }
    };
    sardine.enableCompression();
    return sardine;
  }

  @Override
  public RemoteLogin getConfiguredWebDavRemoteLogin() throws WebDavException {
    DocumentReference webDavConfigDocRef = ConfigSourceUtils.getReferenceProperty(
        "webdav.configdoc", DocumentReference.class).or(getDefaultWebDavConfigDocRef());
    try {
      return remoteLoginLoader.load(webDavConfigDocRef);
    } catch (BeanLoadException exc) {
      throw new WebDavException("illegal WebDAV config doc: " + webDavConfigDocRef, exc);
    }
  }

  private DocumentReference getDefaultWebDavConfigDocRef() {
    return new RefBuilder().with(context.getWikiRef()).space("WebDAV").doc("Config").build(
        DocumentReference.class);
  }

}
