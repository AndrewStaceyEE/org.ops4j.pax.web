package org.ops4j.pax.web.itest.jetty;

import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.OptionUtils.combine;

import java.util.Dictionary;

import javax.servlet.ServletException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.web.itest.base.VersionUtil;
import org.ops4j.pax.web.itest.base.support.TestServlet;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests default virtual host and connector configuration for web apps Based on
 * JettyConfigurationIntegrationTest.java
 * 
 * @author Gareth Collins
 */
@RunWith(PaxExam.class)
public class JettyConfigurationExtendedTwoIntegrationTest extends ITestBase {

	private static final Logger LOG = LoggerFactory
			.getLogger(JettyConfigurationExtendedTwoIntegrationTest.class);

	private Bundle installWarBundle;

	@Configuration
	public static Option[] configure() {
		return combine(
				configureJetty(),
				mavenBundle().groupId("org.ops4j.pax.web.samples")
						.artifactId("jetty-config-fragment")
						.version(VersionUtil.getProjectVersion()).noStart(),
				systemProperty("org.ops4j.pax.web.default.virtualhosts").value(
						"127.0.0.1"),
				systemProperty("org.ops4j.pax.web.default.connectors").value(
						"default"));

	}

	@Before
	public void setUp() throws BundleException, InterruptedException, ServletException, NamespaceException {
		LOG.info("Setting up test");

		initWebListener();

		final String bundlePath = WEB_BUNDLE
				+ "mvn:org.ops4j.pax.web.samples/war/" + VersionUtil.getProjectVersion()
				+ "/war?" + WEB_CONTEXT_PATH + "=/test";
		installWarBundle = bundleContext.installBundle(bundlePath);
		installWarBundle.start();

		waitForWebListener();
		
		HttpService httpService = getHttpService(bundleContext);
		
		initServletListener(null);

		TestServlet servlet = new TestServlet();
		httpService.registerServlet("/test2", servlet, null, null);
		Assert.assertTrue("Servlet.init(ServletConfig) was not called", servlet.isInitCalled());
		
		waitForServletListener();
	}

	@After
	public void tearDown() throws BundleException {
		if (installWarBundle != null) {
			installWarBundle.stop();
			installWarBundle.uninstall();
		}
		HttpService httpService = getHttpService(bundleContext);
		httpService.unregister("/test2");
	}

	/**
	 * You will get a list of bundles installed by default plus your testcase,
	 * wrapped into a bundle called pax-exam-probe
	 */
	@Test
	public void listBundles() {
		for (final Bundle b : bundleContext.getBundles()) {
			if (b.getState() != Bundle.ACTIVE
					&& b.getState() != Bundle.RESOLVED) {
				fail("Bundle should be active: " + b);
			}

			final Dictionary<String, String> headers = b.getHeaders();
			final String ctxtPath = headers.get(WEB_CONTEXT_PATH);
			if (ctxtPath != null) {
				System.out.println("Bundle " + b.getBundleId() + " : "
						+ b.getSymbolicName() + " : " + ctxtPath);
			} else {
				System.out.println("Bundle " + b.getBundleId() + " : "
						+ b.getSymbolicName());
			}
		}

	}

	@Test
    @Ignore //can't be done with the same IP adress and localhost ... 
	public void testWeb() throws Exception {
		testWebPath("http://localhost:8181/test/wc/example", 404);
	}

	@Test
	public void testWebIP() throws Exception {
		testWebPath("http://127.0.0.1:8181/test/wc/example",
				"<h1>Hello World</h1>");
	}

	@Test
	@Ignore // can't be done since localhost and IP are actually the same!
	public void testWebJettyIP() throws Exception {
		testWebPath("http://127.0.0.1:8282/test/wc/example", 404);
	}

	@Test
	public void testWebJetty() throws Exception {
		testWebPath("http://localhost:8282/test/wc/example", 404);
	}
	
	
	@Test
    @Ignore //can't be done with the same IP adress and localhost ... 
	public void testHttpService() throws Exception {	
		testWebPath("http://localhost:8181/test2", 404);
	}
	
	@Test
	public void testHttpServiceIP() throws Exception {
		
		testWebPath("http://127.0.0.1:8181/test2", "TEST OK");
	}
	
	@Test
	@Ignore // can't be done since localhost and IP are actually the same!
	public void testHttpServiceJettyIP() throws Exception {
		testWebPath("http://127.0.0.1:8282/test2", 404);
	}
	
	@Test
	public void testHttpServiceJetty() throws Exception {
		testWebPath("http://localhost:8282/test2", 404);
	}
	
	private HttpService getHttpService(BundleContext bundleContext) {
		ServiceReference<HttpService> ref = bundleContext.getServiceReference(HttpService.class);
		Assert.assertNotNull("Failed to get HttpService", ref);
		HttpService httpService = (HttpService) bundleContext.getService(ref);
		Assert.assertNotNull("Failed to get HttpService", httpService);
		return httpService;
	}
}
