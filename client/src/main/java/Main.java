import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.example.*;

public class Main {

  private static final String ADDRESS = "localhost";
  private static final int PORT = 50051;
  private static final Path CLIENT_BASE_PATH = Path.of("client/src/main/resources/input");

  public static void main(String[] args) throws IOException, InterruptedException {
    var executorService = Executors.newFixedThreadPool(3);
    var channel =
        ManagedChannelBuilder.forAddress(ADDRESS, PORT)
            .executor(executorService)
            .usePlaintext()
            .build();

    // uploadFileToServer(channel);

    var fileDownloadRequest =
        FileDownloadRequest.newBuilder()
            .setMetadata(MetaData.newBuilder().setName("grpc-icon-color").setType("png"))
            .build();

    var stub = FileServiceGrpc.newStub(channel);
    stub.download(
        fileDownloadRequest,
        new FileDownloadObserver(new CountDownLatch(1), "grpc-icon-color.png"));

    Thread.sleep(5000L);
    executorService.shutdown();
    channel.shutdown();
  }

  public static void uploadFileToServer(ManagedChannel channel)
      throws IOException, InterruptedException {
    var stub = FileServiceGrpc.newStub(channel);
    var latch = new CountDownLatch(1);
    var streamObserver = stub.upload(new FileUploadObserver(latch));
    var path = CLIENT_BASE_PATH.resolve("grpc-icon-color.png");
    var inputStream = Files.newInputStream(path);
    byte[] bytes = new byte[4096];
    int size;

    FileUploadRequest metadata =
        FileUploadRequest.newBuilder()
            .setMetadata(MetaData.newBuilder().setName("grpc-icon-color").setType("png").build())
            .build();

    streamObserver.onNext(metadata);

    while ((size = inputStream.read(bytes)) > 0) {
      FileUploadRequest uploadRequest =
          FileUploadRequest.newBuilder()
              .setFile(File.newBuilder().setContent(ByteString.copyFrom(bytes, 0, size)).build())
              .build();
      streamObserver.onNext(uploadRequest);
    }

    inputStream.close();
    streamObserver.onCompleted();
    latch.await();
  }

  private static class FileUploadObserver implements StreamObserver<FileUploadResponse> {

    private final CountDownLatch latch;

    public FileUploadObserver(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onNext(FileUploadResponse fileUploadResponse) {
      System.out.println("File upload status: " + fileUploadResponse.getStatus());
    }

    @Override
    public void onError(Throwable throwable) {
      throwable.printStackTrace();
    }

    @Override
    public void onCompleted() {
      System.out.println("Done");
      this.latch.countDown();
    }
  }

  private static class FileDownloadObserver implements StreamObserver<FileDownloadResponse> {

    private final CountDownLatch latch;
    private final OutputStream writer;

    public FileDownloadObserver(CountDownLatch latch, String fileName) throws IOException {
      this.latch = latch;
      this.writer =
          Files.newOutputStream(
              CLIENT_BASE_PATH.resolve(fileName),
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
    }

    @Override
    public void onNext(FileDownloadResponse fileDownloadResponse) {
      System.out.println("File upload status: " + fileDownloadResponse.getStatus());

      if (fileDownloadResponse.getStatus() == Status.IN_PROGRESS) {
        try {
          writeFile(writer, fileDownloadResponse.getFile().getContent());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    @Override
    public void onError(Throwable throwable) {
      throwable.printStackTrace();
    }

    @Override
    public void onCompleted() {
      System.out.println("Done");
      this.latch.countDown();
    }

    private void writeFile(OutputStream writer, ByteString content) throws IOException {
      writer.write(content.toByteArray());
      writer.flush();
    }
  }
}
