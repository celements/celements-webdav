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
import java.util.Optional;

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
import com.celements.webdav.exception.DavFileNotExistsException;
import com.celements.webdav.exception.DavResourceAccessException;
import com.celements.webdav.exception.DavResourceAlreadyExistsException;
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
  ClassDefinition remoteLoginClass;

  @Requirement
  Execution execution;

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
    LOGGER.info("getConfiguredRemoteLogin - {}", webDavConfigDocRef);
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
    String key = getSardineExecutionContextKey(remoteLogin);
    Sardine sardine = (Sardine) execution.getContext().getProperty(key);
    if ((sardine == null) || !isConnected(sardine, remoteLogin)) {
      execution.getContext().setProperty(key, sardine = newSecureSardineInstance(remoteLogin));
    } else {
      LOGGER.trace("getSardine - returning cached instance [{}]", sardine.hashCode());
    }
    return sardine;
  }

  String getSardineExecutionContextKey(RemoteLogin remoteLogin) {
    return EC_KEY + "|" + Objects.hash(remoteLogin.getUrl(), remoteLogin.getUsername());
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
      if (isConnected(sardine, remoteLogin)) {
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

  private boolean isConnected(Sardine sardine, RemoteLogin remoteLogin) {
    try {
      return sardine.exists(remoteLogin.getUrl());
    } catch (IOException ioe) {
      return false;
    }
  }

  private URL getTrustStoreUrl() throws IOException {
    String cacertsPath = cfgSrc.getProperty("celements.security.cacerts");
    LOGGER.debug("getTrustStoreUrl - cacertsPath [{}]", cacertsPath);
    return context.getXWikiContext().getWiki().getResource(cacertsPath);
  }

  public class SardineConnection implements WebDavConnection {

    private final Sardine sardine;
    private final URL baseUrl;

    SardineConnection(Sardine sardine, URL baseUrl) {
      this.sardine = checkNotNull(sardine);
      this.baseUrl = checkNotNull(baseUrl);
    }

    URL buildCompleteUrl(Path path) {
      try {
        if (checkNotNull(path).isAbsolute()) {
          path = Paths.get("/", baseUrl.getPath()).relativize(path);
        }
        return UriBuilder.fromUri(baseUrl.toURI()).path(path.normalize().toString()).build().toURL();
      } catch (URISyntaxException | UriBuilderException | MalformedURLException exc) {
        // this shouldn't happen since baseUrl and path are already well defined objects
        throw new IllegalArgumentException(MessageFormat.format("unable to build url with "
            + "base [{0}] and path [{1}]: [{2}]", baseUrl, path, exc.getMessage()), exc);
      }
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
      Optional<DavResource> resource = Optional.empty();
      if (sardine.exists(url.toExternalForm())) {
        resource = Optional.ofNullable(getDavResource(url));
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
        throw new DavFileNotExistsException(url);
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
    public void createDirectory(Path dirPath) throws IOException,
        DavResourceAccessException {
      URL url = buildCompleteUrl(dirPath);
      try {
        if (!sardine.exists(url.toExternalForm())) {
          if (dirPath.getParent() != null) {
            createDirectory(dirPath.getParent());
          }
          sardine.createDirectory(url.toExternalForm());
          LOGGER.info("createDirectory - {}", url);
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
          throw new DavResourceAlreadyExistsException(url);
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
    public void close() throws IOException {
      sardine.shutdown();
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
        throw new DavResourceAccessException("Forbidden", url, exc);
      case 404: // Not found
        throw new DavResourceAccessException("Not Found", url, exc);
      case 405: // Method Not Allowed
        throw new DavResourceAccessException("Method not allowed", url, exc);
      case 409: // Conflict
        throw new DavResourceAccessException("Conflict", url, exc);
      case 410: // Gone
        throw new DavResourceAccessException("Gone", url, exc);
      case 418: // I'm a teapot - happy April Fools' Day 2019 ;)
        throw new DavResourceAccessException("Teapot", url, exc);
    }
  }

}
