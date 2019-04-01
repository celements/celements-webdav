package com.celements.webdav;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.apache.commons.configuration.ConfigurationException;
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
import com.celements.webdav.exception.DavConnectionException;
import com.celements.webdav.exception.DavResourceAccessException;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineException;
import com.github.sardine.impl.SardineImpl;
import com.google.common.base.Optional;

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
  public RemoteLogin getConfiguredRemoteLogin() throws ConfigurationException {
    DocumentReference webDavConfigDocRef = ConfigSourceUtils.getReferenceProperty(
        "webdav.configdoc", DocumentReference.class).or(getDefaultConfigDocRef());
    LOGGER.info("getConfiguredWebDavRemoteLogin - {}", webDavConfigDocRef);
    try {
      return remoteLoginLoader.load(webDavConfigDocRef);
    } catch (BeanLoadException exc) {
      throw new ConfigurationException("illegal WebDAV config doc: " + webDavConfigDocRef, exc);
    }
  }

  private DocumentReference getDefaultConfigDocRef() {
    return new RefBuilder().with(context.getWikiRef()).space("WebDAV").doc("Config").build(
        DocumentReference.class);
  }

  @Override
  public SardineConnection connect() throws DavConnectionException, MalformedURLException,
      ConfigurationException {
    return connect(getConfiguredRemoteLogin());
  }

  @Override
  public SardineConnection connect(RemoteLogin remoteLogin) throws DavConnectionException,
      MalformedURLException {
    URL baseUrl = new URL(remoteLogin.getUrl());
    return new SardineConnection(getSardine(remoteLogin), baseUrl);
  }

  /**
   * Currently we instance Sardine once per request. It could be safely used in a multithreaded
   * environment since it uses the org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager.
   * See <a href="https://github.com/lookfirst/sardine/wiki/UsageGuide#threading">Sardine Docu</a>.
   */
  public Sardine getSardine(RemoteLogin remoteLogin) throws DavConnectionException {
    checkNotNull(remoteLogin);
    String key = EC_KEY + "|" + Objects.hash(remoteLogin.getUrl(), remoteLogin.getUsername());
    Sardine sardine = (Sardine) execution.getContext().getProperty(key);
    if (sardine == null) {
      execution.getContext().setProperty(key, sardine = newSecureSardineInstance(remoteLogin));
    } else {
      LOGGER.trace("getSardine - returning cached instance [{}]", sardine.hashCode());
    }
    return sardine;
  }

  private Sardine newSecureSardineInstance(final RemoteLogin remoteLogin)
      throws DavConnectionException {
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
        LOGGER.debug("newSecureSardineInstance - [{}] for [{}]", sardine.hashCode(), remoteLogin);
        return sardine;
      } else {
        throw new DavConnectionException("illegal remote login definition: " + remoteLogin);
      }
    } catch (IOException | GeneralSecurityException exc) {
      throw new DavConnectionException("sardine instantiation failed for login: " + remoteLogin,
          exc);
    }
  }

  private URL getTrustStoreUrl() throws IOException {
    String cacertsPath = cfgSrc.getProperty("celements.security.cacerts");
    LOGGER.debug("getTrustStoreUrl - cacertsPath [{}]", cacertsPath);
    return context.getXWikiContext().getWiki().getResource(cacertsPath);
  }

  public class SardineConnection implements WebDavConnection, AutoCloseable {

    private final Sardine sardine;
    private final URL baseUrl;

    private SardineConnection(Sardine sardine, URL baseUrl) {
      this.sardine = checkNotNull(sardine);
      this.baseUrl = checkNotNull(baseUrl);
    }

    @Override
    public List<DavResource> list(Path path) throws IOException, DavResourceAccessException {
      URL url = buildCompleteUrl(path);
      try {
        List<DavResource> list = sardine.list(url.toExternalForm());
        LOGGER.info("list - {} : {}", url, list.size());
        return list;
      } catch (SardineException sardineExc) {
        throwResourceAccessException(url, sardineExc);
        throw sardineExc;
      }
    }

    @Override
    public Optional<DavResource> get(Path path) throws IOException {
      URL url = buildCompleteUrl(path);
      Optional<DavResource> resource = Optional.absent();
      if (sardine.exists(url.toExternalForm())) {
        resource = Optional.fromNullable(getDavResource(url));
      }
      LOGGER.info("get - {} : {}", url, resource);
      return resource;
    }

    @Nullable
    private DavResource getDavResource(URL url) throws IOException {
      DavResource ret = null;
      Path path = Paths.get(url.getPath());
      for (DavResource resource : sardine.list(url.toExternalForm())) {
        if (path.equals(Paths.get(resource.getPath()))) {
          ret = resource;
        }
      }
      return ret;
    }

    @NotNull
    private DavResource expectDavFile(URL url) throws IOException, DavResourceAccessException {
      DavResource resource = getDavResource(url);
      if ((resource != null) && !resource.isDirectory()) {
        return resource;
      } else {
        throw new DavResourceAccessException("resource not file [" + url + "] with type ["
            + ((resource != null) ? resource.getContentType() : null) + "]");
      }
    }

    @Override
    public byte[] load(Path filePath) throws IOException, DavResourceAccessException {
      URL url = buildCompleteUrl(filePath);
      try {
        expectDavFile(url);
        try (InputStream is = sardine.get(url.toExternalForm())) {
          byte[] content = IOUtils.toByteArray(is);
          LOGGER.info("load - {} : {} bytes", url, content.length);
          return content;
        }
      } catch (SardineException sardineExc) {
        throwResourceAccessException(url, sardineExc);
        throw sardineExc;
      }
    }

    @Override
    public void create(Path filePath, byte[] content) throws IOException,
        DavResourceAccessException {
      URL url = buildCompleteUrl(filePath);
      try {
        if (!sardine.exists(url.toExternalForm())) {
          sardine.put(url.toExternalForm(), content);
          LOGGER.info("create - {}", url);
        } else {
          throw new DavResourceAccessException("Already exists - " + url);
        }
      } catch (SardineException sardineExc) {
        throwResourceAccessException(url, sardineExc);
        throw sardineExc;
      }
    }

    @Override
    public void update(Path filePath, byte[] content) throws IOException,
        DavResourceAccessException {
      URL url = buildCompleteUrl(filePath);
      try {
        expectDavFile(url);
        sardine.put(url.toExternalForm(), content);
        LOGGER.info("update - {}", url);
      } catch (SardineException sardineExc) {
        throwResourceAccessException(url, sardineExc);
        throw sardineExc;
      }
    }

    @Override
    public void createOrUpdate(Path filePath, byte[] content) throws IOException,
        DavResourceAccessException {
      URL url = buildCompleteUrl(filePath);
      try {
        sardine.put(url.toExternalForm(), content);
        LOGGER.info("createOrUpdate - {}", url);
      } catch (SardineException sardineExc) {
        throwResourceAccessException(url, sardineExc);
        throw sardineExc;
      }
    }

    @Override
    public void delete(Path path) throws IOException, DavResourceAccessException {
      URL url = buildCompleteUrl(path);
      try {
        sardine.delete(url.toExternalForm());
        LOGGER.info("delete - {}", url);
      } catch (SardineException sardineExc) {
        throwResourceAccessException(url, sardineExc);
        throw sardineExc;
      }
    }

    @Override
    public void close() throws Exception {
      sardine.shutdown();
    }

    private URL buildCompleteUrl(Path path) {
      checkNotNull(path);
      try {
        return UriBuilder.fromUri(baseUrl.toURI()).path(
            path.normalize().toString()).build().toURL();
      } catch (URISyntaxException | UriBuilderException | MalformedURLException exc) {
        // this shouldn't happen since baseUrl and path are already well defined objects
        throw new IllegalArgumentException(MessageFormat.format("unable to build url with "
            + "base [{0}] and path [{1}]: [{2}]", baseUrl, path, exc.getMessage()), exc);
      }
    }

  }

  /**
   * throws a {@link DavResourceAccessException} for specific HTTP status codes concerning errors
   * for the given path. A different path may not cause such errors. More general error codes
   * should result in an {@link IOException}.
   */
  private static void throwResourceAccessException(URL url, SardineException exc)
      throws DavResourceAccessException {
    switch (exc.getStatusCode()) {
      case 403: // Forbidden
        throw new DavResourceAccessException("Forbidden - " + url, exc);
      case 404: // Not Found
        throw new DavResourceAccessException("Not Found - " + url, exc);
      case 409: // Conflict
        throw new DavResourceAccessException("Conflict - " + url, exc);
      case 410: // Gone
        throw new DavResourceAccessException("Gone - " + url, exc);
      case 418: // I'm a teapot - April Fools' Day 2019 ;)
        throw new DavResourceAccessException("Teapot - " + url, exc);
    }
  }

}
