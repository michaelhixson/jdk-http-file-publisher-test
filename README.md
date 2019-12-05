This is a demonstration of an issue with the implementation of
[`HttpRequest.BodyPublishers#ofFile(Path)`][body-publishers-of-file] in Java 13.
That method throws `UnsupportedOperationException` when the specified path uses
a file system other than the default file system, such as a
[zip file system][zipfs] or a [Jimfs in-memory file system][jimfs].

To build:

    mvn clean package

To run:

    java -jar target/test.jar

[body-publishers-of-file]: https://docs.oracle.com/en/java/javase/13/docs/api/java.net.http/java/net/http/HttpRequest.BodyPublishers.html#ofFile(java.nio.file.Path)
[zipfs]: https://docs.oracle.com/en/java/javase/13/docs/api/jdk.zipfs/module-summary.html
[jimfs]: https://github.com/google/jimfs
