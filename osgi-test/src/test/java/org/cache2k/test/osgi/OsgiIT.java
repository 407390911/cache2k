package org.cache2k.test.osgi;

/*
 * #%L
 * osgi-test
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;

import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.bundle;

/**
 * Test the OSGi enabled bundle. Tests are run via the failsafe maven plugin and not with
 * surefire, since these are integration tests. This is critical since we must run
 * after the package phase for the the bundle package to exist.
 *
 * @author Jens Wilke
 */
@org.junit.runner.RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class OsgiIT {

  @Configuration
  public Option[] config() {
    String _userDir = System.getProperty("user.dir");
    String _ownPath = "/osgi-test";
    String _workspaceDir = _userDir;
    if (_workspaceDir.endsWith(_ownPath)) {
      _workspaceDir = _workspaceDir.substring(0,_workspaceDir.length() -  _ownPath.length());
    }
    return options(
      bundle("file:///" + _workspaceDir + "/variant/all/target/cache2k-all-" + System.getProperty("cache2k.version") + ".jar"),
      junitBundles()
    );
  }

  @Test
  public void testSimple() {
    Cache<String, String> c =
      Cache2kBuilder.of(String.class, String.class)
        .eternal(true)
        .build();
    c.put("abc", "123");
    assertTrue(c.containsKey("abc"));
    assertEquals("123", c.peek("abc"));
    c.close();
  }

}
