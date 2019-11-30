# jetty9-embedded-static-server

Demonstrates how to use embedded Jetty 9's `DefaultServlet` to serve static content:

[DefaultServlet.java](https://github.com/eclipse/jetty.project/blob/d7cf3729a5076331297bc6295e55f568bf132671/jetty-servlet/src/main/java/org/eclipse/jetty/servlet/DefaultServlet.java)

Fully configures a server instance in code, with no `web.xml` or `jetty.xml`.

Supports `Accept-Range` requests, and [HTTP Basic authentication](https://en.wikipedia.org/wiki/Basic_access_authentication) too.

## Building & running

Run `mvn clean package` to generate a runnable JAR.

Then, start the web-server:

```
java -jar dist/jetty9-embedded-static-server-0.1-runnable.jar
```

The server serves content from the directory from which it was started (the `$PWD`).

Once running, hit http://localhost:8080 in your favorite browser.

### Listen port

By default, the server listens on port 8080. You can change the listen port with the `--port` argument:

```
java -jar dist/jetty9-embedded-static-server-0.1-runnable.jar --port=9000
```

### Authentication

If you wish to run the server with HTTP basic authentication, start the app with `USERNAME` and `PASSWORD` environment variables:

```
USERNAME=foo PASSWORD=bar \
  java -jar dist/jetty9-embedded-static-server-0.1-runnable.jar
```

In this mode, the browser will prompt you for a username and password and will only let you in if the provided credentials match the username and password from the `USERNAME` and `PASSWORD` environment variables.