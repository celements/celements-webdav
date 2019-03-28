package com.celements.webdav;

import static com.google.common.base.Preconditions.*;
import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;

@Component(SardineAdapter.NAME)
public class SardineAdapter implements WebDavService, Initializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SardineAdapter.class);

  public static final String NAME = "sardine";

  static final String EC_KEY = "WebDAV.Sardine";

  @Requirement(RemoteLoginClass.CLASS_DEF_HINT)
  ClassDefinition remoteLoginClass;

  @Requirement
  private Execution execution;

  @Requirement(CelementsFromWikiConfigurationSource.NAME)
  private ConfigurationSource cfgSrc;

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
  Sardine getSardine(RemoteLogin remoteLogin) throws IOException, GeneralSecurityException,
      WebDavException {
    checkNotNull(remoteLogin);
    // TODO implement hashcode with username and url
    String key = EC_KEY + "|" + remoteLogin.hashCode();
    Sardine sardine = (Sardine) execution.getContext().getProperty(key);
    if (sardine == null) {
      sardine = newSecureSardineInstance(remoteLogin);
      if (sardine.exists(remoteLogin.getUrl())) {
        execution.getContext().setProperty(key, sardine);
      } else {
        throw new WebDavException("illegal remote login definition: " + remoteLogin);
      }
    }
    return sardine;
  }

  private Sardine newSecureSardineInstance(final RemoteLogin remoteLogin) throws IOException,
      GeneralSecurityException {
    final SSLContext sslCtx = SSLContexts.custom().loadTrustMaterial(getTrustStoreUrl(), null,
        new TrustSelfSignedStrategy()).build();
    Sardine sardine = new SardineImpl(remoteLogin.getUsername(), remoteLogin.getPassword()) {

      @Override
      protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
        return new SSLConnectionSocketFactory(sslCtx);
      }
    };
    sardine.disablePreemptiveAuthentication();
    sardine.enableCompression();
    return sardine;
  }

  private URL getTrustStoreUrl() throws IOException {
    String cacertsPath = cfgSrc.getProperty("celements.security.cacerts");
    LOGGER.info("getTrustStoreUrl - cacertsPath [{}]", cacertsPath);
    return context.getXWikiContext().getWiki().getResource(cacertsPath);
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
