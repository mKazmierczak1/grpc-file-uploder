import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.example.*;

public class FileUploadService extends FileServiceGrpc.FileServiceImplBase {

  private static final Path SERVER_BASE_PATH = Path.of("file-server/src/main/resources/output");

  @Override
  public StreamObserver<FileUploadRequest> upload(
      StreamObserver<FileUploadResponse> responseObserver) {
    return new StreamObserver<>() {
      OutputStream writer;
      Status status = Status.IN_PROGRESS;

      @Override
      public void onNext(FileUploadRequest fileUploadRequest) {
        try {
          if (fileUploadRequest.hasMetadata()) {
            writer = getFilePath(fileUploadRequest);
          } else {
            writeFile(writer, fileUploadRequest.getFile().getContent());
          }
        } catch (IOException e) {
          this.onError(e);
        }
      }

      @Override
      public void onError(Throwable throwable) {
        status = Status.FAILED;
        System.err.println("UPLOAD FAILED!");
        throwable.printStackTrace();

        this.onCompleted();
      }

      @Override
      public void onCompleted() {
        closeFile(writer);
        System.out.println("Success! File was uploaded.");

        status = Status.IN_PROGRESS.equals(status) ? Status.SUCCESS : status;
        var response = FileUploadResponse.newBuilder().setStatus(status).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
      }
    };
  }

  @Override
  public void download(
      FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
    var fileName = request.getMetadata().getName() + "." + request.getMetadata().getType();
    System.out.println("Received request for file: " + fileName);

    try {
      var filePath = SERVER_BASE_PATH.resolve(fileName);
      var inputStream = Files.newInputStream(filePath);
      byte[] bytes = new byte[4096];
      int size;

      while ((size = inputStream.read(bytes)) > 0) {
        FileDownloadResponse uploadResponse =
            FileDownloadResponse.newBuilder()
                .setFile(File.newBuilder().setContent(ByteString.copyFrom(bytes, 0, size)).build())
                .setStatus(Status.IN_PROGRESS)
                .build();
        responseObserver.onNext(uploadResponse);
      }
    } catch (Exception e) {
      var response = FileDownloadResponse.newBuilder().setStatus(Status.FAILED).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    responseObserver.onNext(FileDownloadResponse.newBuilder().setStatus(Status.SUCCESS).build());

    System.out.println("File sent!");
    responseObserver.onCompleted();
  }

  private OutputStream getFilePath(FileUploadRequest request) throws IOException {
    var fileName = request.getMetadata().getName() + "." + request.getMetadata().getType();
    System.out.println("Created new file: " + fileName);

    return Files.newOutputStream(
        SERVER_BASE_PATH.resolve(fileName), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private void writeFile(OutputStream writer, ByteString content) throws IOException {
    writer.write(content.toByteArray());
    writer.flush();
  }

  private void closeFile(OutputStream writer) {
    try {
      if (writer != null) {
        writer.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
