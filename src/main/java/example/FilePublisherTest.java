package example;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.zip.ZipOutputStream;

public final class FilePublisherTest {
  private FilePublisherTest() {}

  public static void main(String[] args) throws Exception {
    var requestHandler =
        new BlockingHandler(
            exchange -> {
              var bytes = exchange.getInputStream().readAllBytes();
              var string = new String(bytes, UTF_8);
              System.out.println(
                  "\t\t...server received file with contents: \""
                      + string
                      + "\"");
            });

    var server =
        Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(requestHandler)
                .build();

    server.start();
    testDefaultFs();
    testJimFs();
    testZipFs();
    server.stop();
  }

  // this works
  private static void testDefaultFs() throws Exception {
    var file = Files.createTempFile("FilePublisherTest", ".txt");
    try {
      Files.writeString(file, "default fs");
      sendFile(file);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  // this fails, prints UOE stack trace
  private static void testJimFs() throws Exception {
    try (var fs = Jimfs.newFileSystem(Configuration.unix())) {
      var file = fs.getPath("example.txt");
      Files.writeString(file, "in-memory fs");
      sendFile(file);
    }
  }

  // this fails, prints UOE stack trace
  private static void testZipFs() throws Exception {
    var zipFile = Files.createTempFile("FilePublisherTest", ".zip");
    try {

      // create an empty zip file
      try (var fos = Files.newOutputStream(zipFile);
           var bos = new BufferedOutputStream(fos);
           var zos = new ZipOutputStream(bos)) {}

      try (var fs = FileSystems.newFileSystem(zipFile)) {
        var file = fs.getPath("example.txt");
        Files.writeString(file, "zip fs");
        sendFile(file);
      }
    } finally {
      Files.deleteIfExists(zipFile);
    }
  }

  private static void sendFile(Path file)
      throws IOException, InterruptedException {

    Supplier<BodyPublisher> ofFile =
        () -> {
          try {
            return BodyPublishers.ofFile(file);
          } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
          }
        };

    Supplier<BodyPublisher> ofInputStream =
        () -> {
          return BodyPublishers.ofInputStream(() -> {
            try {
              return Files.newInputStream(file);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
        };

    System.out.println("sending file");
    System.out.println("\tfile:           " + file);
    System.out.println("\tfile system:    " + file.getFileSystem());
    System.out.println("\tfile contents:  \"" + Files.readString(file) + "\"");

    System.out.println("\ttrying BodyPublishers.ofFile...");
    try {
      sendFile(ofFile);
      System.out.println("\t\t...success!");
    } catch (UnsupportedOperationException e) {
      System.out.println("\t\t...failed with UOE");
      e.printStackTrace(System.out);
    }

    System.out.println("\ttrying BodyPublishers.ofInputStream...");
    try {
      sendFile(ofInputStream);
      System.out.println("\t\t...success!");
    } catch (UnsupportedOperationException e) {
      System.out.println("\t\t...failed with UOE");
      e.printStackTrace(System.out);
    }
  }

  private static void sendFile(Supplier<BodyPublisher> supplier)
      throws IOException, InterruptedException {

    var client = HttpClient.newHttpClient();
    var uri = URI.create("http://localhost:8080/file_publisher");
    var publisher = supplier.get(); // might throw UOE
    var request = HttpRequest.newBuilder(uri).POST(publisher).build();
    var handler = HttpResponse.BodyHandlers.discarding();
    var response = client.send(request, handler);
  }
}
