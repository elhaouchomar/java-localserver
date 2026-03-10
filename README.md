## LocalServer

### Overview

Finally, you are going to understand how the internet works from the server side. The Hypertext Transfer Protocol was created in order to ensure a reliable way to communicate on a request/response basis.

This protocol is used by servers and clients (usually browsers) to serve content, and it is the backbone of the World Wide Web. Still, it is also used in many other cases that are far beyond the scope of this exercise.

For this project, you **must** use **Java**.

### Role Play

You are a backend engineer at a startup building a lightweight web server to handle internal APIs and static content with minimal dependencies. Your goal is to deliver a highly available, crash-proof solution that can be extended to support dynamic content via CGI scripts and configured to suit multiple environments.

### Learning Objective

By the end of this project, learners will be able to:

- Design and implement a custom HTTP/1.1-compliant server in Java
- Utilize non-blocking I/O mechanisms
- Parse and construct HTTP requests and responses manually
- Configure server routes, error pages, uploads, and CGI scripts
- Evaluate performance under stress and ensure memory and process safety

Technical skills:

- Socket programming
- Asynchronous I/O
- File and process management
- Configuration parsing

### Instructions

- The project must be written in **Java**.
- Use Java Core Libraries, namely the `java.nio` package for non-blocking I/O and `java.net` for network handling.
- Make use of an event-driven API for handling connections.

> You **cannot** use established server frameworks or asynchronous runtimes (e.g., `Netty`, `Jetty`, `Grizzly`).

#### The Server

Your goal is to write your own HTTP server to serve static web pages to browsers.

It must:

- **Never** crash.
- Timeout long requests.
- Listen on multiple ports and instantiate multiple servers.
- Use only one process and one thread.
- Receive requests and send HTTP/1.1-compliant responses.
- Handle `GET`, `POST`, and `DELETE`.
- Receive file uploads.
- Handle cookies and sessions.
- Provide default error pages for: 400, 403, 404, 405, 413, 500.
- Use an event-driven, non-blocking I/O API.
- Manage chunked and unchunked requests.
- Set the correct HTTP status in responses.

#### The CGI

- Execute one type of CGI (e.g., `.py`) using `ProcessBuilder`.
- Pass the file to process as the first argument.
- Use the `PATH_INFO` environment variable to define full paths.
- Ensure correct relative path handling.

#### Configuration File

Support configuration for:

- Host and multiple ports.
- Default server selection.
- Custom error page paths.
- Client body size limit.
- Routes with:
  - Accepted methods.
  - Redirections.
  - Directory/file roots.
  - Default file for directories.
  - CGI by file extension.
  - Directory listing toggle.
  - Default directory response file.

> No need for regex support.

#### Testing

- Use `siege -b [IP]:[PORT]` for stress testing (target 99.5% availability).
- Write comprehensive tests (redirections, configs, error pages, etc.).
- Test for memory leaks.

### Bonus Challenges

- Implement a second CGI handler.
- Create an admin dashboard or server metrics endpoint.

### Example Repository Structure

```
/java-server
├── /src
│   ├── Main.java         # Entry point
│   ├── Server.java       # Handles server lifecycle
│   ├── Router.java       # Routes requests
│   ├── CGIHandler.java   # Manages CGI execution
│   ├── ConfigLoader.java # Parses configuration file
│   ├── error.java        # Error responses
│   ├── utils/
│       ├── Session.java  # Session management
│       ├── Cookie.java   # Cookie utilities
├── config.json           # Server configuration
├── README.md             # Documentation
├── error_pages/          # Custom error HTML files
```

### Tips

- Avoid hardcoding; use the config file.
- Validate configs at startup.
- Sanitize inputs for CGI.
- Modularize components.
- Use thread-safe data structures.
- Prevent file descriptor and memory leaks.

### Resources

- [RFC 2616 – HTTP/1.1 Specification](https://www.rfc-editor.org/rfc/rfc9112.html)
- [Java NIO Docs](https://docs.oracle.com/javase/tutorial/essential/io/)
- [CGI Protocol Overview](https://en.wikipedia.org/wiki/Common_Gateway_Interface)
- [siege Load Testing Tool](https://github.com/JoeDog/siege)

### Disclaimer

This project is for educational use only. Using siege or any stress testing tool against a third-party server without explicit permission is illegal and unethical.

### project audit
Localhost is about creating your own HTTP server and test it with an actual browser.

##### Take the necessary time to understand the project and to test it, looking into the source code will help a lot.

#### Functional

**_Is the student able to justify his choices and explain the following:_**
**_Note:_** Ask the student to show you the implementation in the source code when necessary.

###### How does an HTTP server works?

###### Which function was used for I/O Multiplexing and how does it works?

###### Is the server using only one select (or equivalent) to read the client requests and write answers?

###### Why is it important to use only one select and how was it achieved?

###### Read the code that goes from the select (or equivalent) to the read and write of a client, is there only one read or write per client per select (or equivalent)?

###### Are the return values for I/O functions checked properly?

###### If an error is returned by the previous functions on a socket, is the client removed?

###### Is writing and reading ALWAYS done through a select (or equivalent)?

#### Configuration file

Check the configuration file and modify it if necessary.
**_Are the following configurations working properly:_**

###### Setup a single server with a single port.

###### Setup multiple servers with different port.

###### Setup multiple servers with different hostnames (for example: curl --resolve test.com:80:127.0.0.1 http://test.com/). This aims to confirm if your server correctly distinguishes between requests for different hostnames even though they resolve to the same IP and port.

###### Setup custom error pages.

###### Limit the client body (for example: curl -X POST -H "Content-Type: plain/text" --data "BODY with something shorter or longer than body limit").

###### Setup routes and ensure they are taken into account.

###### Setup a default file in case the path is a directory.

###### Setup a list of accepted methods for a route (for example: try to DELETE something with and without permission).

#### Methods and cookies

**_For each method be sure to check the status code (200, 404 etc):_**

###### Are the GET requests working properly?

###### Are the POST requests working properly?

###### Are the DELETE requests working properly?

###### Test a WRONG request, is the server still working properly?

###### Upload some files to the server and get them back to test they were not corrupted.

###### A working session and cookies system is present on the server?

#### Interaction with the browser

Open the browser used by the team during tests and its developer tools panel to help you with tests.

###### Is the browser connecting with the server with no issues?

###### Are the request and response headers correct? (It should serve a full static website without any problem).

###### Try a wrong URL on the server, is it handled properly?

###### Try to list a directory, is it handled properly?

###### Try a redirected URL, is it handled properly?

###### Check the implemented CGI, does it works properly with chunked and unchunked data?

#### Port issues

###### Configure multiple ports and websites and ensure it is working as expected.

###### Configure the same port multiple times. The server should find the error.

What we mean to check is how your server handles configuration errors or conflicts. If you configure the same port multiple times within your server's configuration file for the same or different servers, the expectation is that your server should catch this error and handle it appropriately. It's about ensuring that your server can identify the misconfigurations.

###### Configure multiple servers at the same time with different configurations but with common ports. Ask why the server should work if one of the configurations isn't working.

This aims to validate your server's configuration handling.
You'll configure multiple servers simultaneously, each with different configurations but with shared ports. If one of these configurations isn't valid or encounters an issue, your server should continue to function for the other configurations without being entirely disrupted. The purpose is to ensure that an error in one server's configuration doesn't bring down the entire server if other configurations are correctly set up.

#### Siege & stress test

###### Use siege with a GET method on an empty page, availability should be at least 99.5% with the command `siege -b [IP]:[PORT]`.

###### Check if there is no hanging connection.

#### General

###### +There is more than one CGI system such as [Python,C++,Perl].

###### +There is an admin dashboard or server metrics endpoint.