import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.example.*;

public class Main {

  private static final String ADDRESS = "localhost";
  private static final int PORT = 50051;

  public static void main(String[] args) throws IOException, InterruptedException {
    var executorService = Executors.newSingleThreadExecutor();
    var channel =
        ManagedChannelBuilder.forAddress(ADDRESS, PORT)
            .executor(executorService)
            .usePlaintext()
            .build();

    uploadFileToServer(channel);

    executorService.shutdown();
    channel.shutdown();
  }

  public static void uploadFileToServer(ManagedChannel channel)
      throws IOException, InterruptedException {
    var stub = FileServiceGrpc.newStub(channel);
    var latch = new CountDownLatch(1);
    var streamObserver = stub.upload(new FileUploadObserver(latch));
    var path = Path.of("client/src/main/resources/input/foo.txt");
    var inputStream = Files.newInputStream(path);
    byte[] bytes = new byte[4096];
    int size;

    FileUploadRequest metadata =
        FileUploadRequest.newBuilder()
            .setMetadata(MetaData.newBuilder().setName("foo").setType("txt").build())
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
}
