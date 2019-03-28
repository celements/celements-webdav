package com.celements.webdav;

import static com.google.common.base.Preconditions.*;
import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.apache.commons.io.IOUtils;
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
import com.github.sardine.impl.SardineException;
import com.github.sardine.impl.SardineImpl;

@Component(SardineAdapter.NAME)
public class SardineAdapter implements WebDavService, Initializable {

  public static final String NAME = "sardine";

  private static final Logger LOGGER = LoggerFactory.getLogger(SardineAdapter.class);
  private static final String EC_KEY = "WebDAV.Sardine";

  @Requirement(RemoteLoginClass.CLASS_DEF_HINT)
  private ClassDefinition remoteLoginClass;

  @Requirement
  private Execution execution;

  @Requirement
  private ModelContext context;

  @Requirement(CelementsFromWikiConfigurationSource.NAME)
  private ConfigurationSource cfgSrc;

  @Requirement
  private XDocBeanLoader<RemoteLogin> remoteLoginLoader;

  @Override
  public void initialize() throws InitializationException {
    remoteLoginLoader.initialize(RemoteLogin.class, remoteLoginClass);
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

  @Override
  public List<DavResource> list(Path path) throws WebDavException {
    return list(path, getConfiguredWebDavRemoteLogin());
  }

  @Override
  public List<DavResource> list(Path path, RemoteLogin remoteLogin) throws WebDavException {
    URL url = buildCompleteUrl(remoteLogin, path);
    try {
      return getSardine(remoteLogin).list(url.toExternalForm());
    } catch (IOException exc) {
      throw new WebDavException(url, remoteLogin, exc);
    }
  }

  @Override
  public byte[] load(Path filePath) throws WebDavException {
    return load(filePath, getConfiguredWebDavRemoteLogin());
  }

  @Override
  public byte[] load(Path filePath, RemoteLogin remoteLogin) throws WebDavException {
    URL url = buildCompleteUrl(remoteLogin, filePath);
    try (InputStream is = getSardine(remoteLogin).get(url.toExternalForm())) {
      return IOUtils.toByteArray(is);
    } catch (IOException exc) {
      throw new WebDavException(url, remoteLogin, exc);
    }
  }

  @Override
  public boolean store(Path filePath, byte[] content) throws WebDavException {
    return store(filePath, content, getConfiguredWebDavRemoteLogin());
  }

  @Override
  public boolean store(Path filePath, byte[] content, RemoteLogin remoteLogin)
      throws WebDavException {
    URL url = buildCompleteUrl(remoteLogin, filePath);
    try {
      Sardine sardine = getSardine(remoteLogin);
      if (!sardine.exists(url.toExternalForm())) {
        getSardine(remoteLogin).put(url.toExternalForm(), content);
        return true;
      } else {
        LOGGER.debug("store - tried to overwrite [{}]", url);
      }
    } catch (SardineException exc) {
      LOGGER.warn("store - failed on [{}] with login [{}]", url, remoteLogin, exc);
    } catch (IOException exc) {
      throw new WebDavException(url, remoteLogin, exc);
    }
    return false;
  }

  @Override
  public boolean delete(Path path) throws WebDavException {
    return delete(path, getConfiguredWebDavRemoteLogin());
  }

  @Override
  public boolean delete(Path path, RemoteLogin remoteLogin) throws WebDavException {
    URL url = buildCompleteUrl(remoteLogin, path);
    try {
      Sardine sardine = getSardine(remoteLogin);
      if (sardine.exists(url.toExternalForm())) {
        sardine.delete(url.toExternalForm());
        return true;
      } else {
        LOGGER.debug("delete - tried to delete inexistent path [{}]", url);
      }
      return true;
    } catch (SardineException exc) {
      LOGGER.warn("delete - failed on [{}] with login [{}]", url, remoteLogin, exc);
      return false;
    } catch (IOException exc) {
      throw new WebDavException(url, remoteLogin, exc);
    }
  }

  private static URL buildCompleteUrl(RemoteLogin remoteLogin, Path path) throws WebDavException {
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
  public Sardine getSardine(RemoteLogin remoteLogin) throws WebDavException {
    checkNotNull(remoteLogin);
    String key = EC_KEY + "|" + Objects.hash(remoteLogin.getUrl(), remoteLogin.getUsername());
    Sardine sardine = (Sardine) execution.getContext().getProperty(key);
    if (sardine == null) {
      execution.getContext().setProperty(key, sardine = newSecureSardineInstance(remoteLogin));
    } else {
      LOGGER.debug("getSardine - returning cached instance [{}]", sardine.hashCode());
    }
    return sardine;
  }

  private Sardine newSecureSardineInstance(final RemoteLogin remoteLogin) throws WebDavException {
    try {
      final SSLContext sslCtx = SSLContexts.custom().loadTrustMaterial(getTrustStoreUrl(), null,
          new TrustSelfSignedStrategy()).build();
      Sardine sardine = new SardineImpl(remoteLogin.getUsername(), remoteLogin.getPassword()) {

        @Override
        protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
          return new SSLConnectionSocketFactory(sslCtx);
        }
      };
      // PE not needed because of following exists check already handles intial authentication
      sardine.disablePreemptiveAuthentication();
      sardine.enableCompression();
      if (sardine.exists(remoteLogin.getUrl())) {
        LOGGER.info("newSecureSardineInstance - [{}] for [{}]", sardine.hashCode(), remoteLogin);
        return sardine;
      } else {
        throw new WebDavException("illegal remote login definition: " + remoteLogin);
      }
    } catch (IOException | GeneralSecurityException exc) {
      throw new WebDavException("sardine instantiation failed for login: " + remoteLogin, exc);
    }
  }

  private URL getTrustStoreUrl() throws IOException {
    String cacertsPath = cfgSrc.getProperty("celements.security.cacerts");
    LOGGER.info("getTrustStoreUrl - cacertsPath [{}]", cacertsPath);
    return context.getXWikiContext().getWiki().getResource(cacertsPath);
  }

}
