/*
 * Copyright (c) 2019 Mark S. Kolich
 * http://mark.koli.ch
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SECURITY;
import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String USERNAME_ENV_VARIABLE = "USERNAME";
    private static final String PASSWORD_ENV_VARIABLE = "PASSWORD";

    private static final String CONTEXT_PATH = "/";
    private static final String SERVLET_MAPPING_UNDER_CONTEXT = "/*";

    @Option(names = {"--port"}, paramLabel = "PORT", description = "Server port",
            defaultValue = "8080")
    private int port_;

    public static void main(String... args) {
        try {
            final Main main = new Main();
            new CommandLine(main).parseArgs(args);

            final Server server = main.buildServer();
            server.start();
            server.join();
        } catch (final Exception e) {
            LOG.error("Server startup failed.", e);
        }
    }

    private Server buildServer() throws Exception {
        final File workingDir = getWorkingDir();

        // Setup a new queued thread pool.
        final QueuedThreadPool pool = new QueuedThreadPool();
        // Use substring(1) to strip the leading "/" on the context name.
        pool.setName("jetty-" + CONTEXT_PATH.substring(1));

        // Instantiate a new server instance using said thread pool.
        final Server server = new Server(pool);

        // A little configuration goodness to hide ourselves a bit.
        final HttpConfiguration config = new HttpConfiguration();
        config.setSendXPoweredBy(false); // Hide X-Powered-By: Jetty
        config.setSendServerVersion(false); // Hide Server: Jetty-9.z

        // Grab a NIO connector for the server.
        final HttpConnectionFactory factory = new HttpConnectionFactory(config);
        final ServerConnector connector = new ServerConnector(server, factory);
        connector.setPort(port_);
        connector.setIdleTimeout(30000L); // 30-seconds
        // Attach the connector to the server.
        server.addConnector(connector);

        // Setup a new Servlet context based on our parameters.
        // See https://github.com/eclipse/jetty.project/issues/3963
        final WebAppContext context = new WebAppContext(server, CONTEXT_PATH, null, null, null, null,
                NO_SESSIONS | NO_SECURITY);
        // No sessions, and no security handlers.
        context.setSessionHandler(null);
        context.setSecurityHandler(null);
        context.setResourceBase(workingDir.getAbsolutePath());

        final ServletHolder holder = new ServletHolder(DefaultServlet.class);
        holder.setAsyncSupported(true); // Async supported = true
        holder.setInitOrder(1); // Load on startup = true
        holder.setInitParameter(DefaultServlet.CONTEXT_INIT + "acceptRanges", "true");
        context.addServlet(holder, SERVLET_MAPPING_UNDER_CONTEXT);

        // We do not use JSPs (Java Server Pages) so disable the defaults descriptor
        // which disables loading the JSP engine and slightly improves startup time.
        // http://jetty.4.x6.nabble.com/disable-jsp-engine-when-starting-jetty-td17393.html
        context.setDefaultsDescriptor(null);

        context.setContextPath(CONTEXT_PATH);
        // Intentionally skip scanning JARs for Servlet 3 annotations.
        context.setAttribute(WebInfConfiguration.WEBINF_JAR_PATTERN, "^$");
        context.setAttribute(WebInfConfiguration.CONTAINER_JAR_PATTERN, "^$");

        // If a username and password were provided via environment variables,
        // then setup HTTP basic (www) auth.
        final String username = System.getenv(USERNAME_ENV_VARIABLE);
        final String password = System.getenv(PASSWORD_ENV_VARIABLE);
        if (StringUtil.isNotBlank(username) &&
                StringUtil.isNotBlank(password)) {
            context.setSecurityHandler(buildBasicAuthSecurityHandler(username, password, "static"));
        }

        // Attach the context handler to the server, and go!
        server.setHandler(context);
        server.setStopAtShutdown(true);

        return server;
    }

    private static SecurityHandler buildBasicAuthSecurityHandler(
            final String username,
            final String password,
            final String realm) {
        final UserStore userStore = new UserStore();
        userStore.addUser(username, Credential.getCredential(password), new String[]{"user"});

        final HashLoginService loginService = new HashLoginService();
        loginService.setUserStore(userStore);
        loginService.setName(realm);

        final Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        final ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        final ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName(realm);
        csh.addConstraintMapping(cm);
        csh.setLoginService(loginService);

        return csh;
    }

    private static File getWorkingDir() {
        final Path currentRelativePath = Paths.get("");
        return currentRelativePath.toAbsolutePath().toFile();
    }

}
