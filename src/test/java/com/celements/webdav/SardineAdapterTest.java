package com.celements.webdav;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.celements.common.test.AbstractComponentTest;
import com.xpn.xwiki.web.Utils;

public class SardineAdapterTest extends AbstractComponentTest {

  private SardineAdapter sardineAdapter;

  @Before
  public void prepareTest() {
    sardineAdapter = (SardineAdapter) Utils.getComponent(WebDavService.class);
  }

  @Test
  public void test() {
    assertNotNull(sardineAdapter);
  }

}
