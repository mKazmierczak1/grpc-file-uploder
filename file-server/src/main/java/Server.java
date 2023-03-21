import io.grpc.ServerBuilder;
import java.io.IOException;

public class Server {

  private static final int PORT = 50051;

  public static void main(String[] args) {
    io.grpc.Server server = ServerBuilder.forPort(PORT).addService(new FileUploadService()).build();

    try {
      server.start();
      System.out.println("Server started");
      server.awaitTermination();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}
