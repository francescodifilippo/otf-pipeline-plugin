package io.jenkins.plugins.iac.otf;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import io.jenkins.plugins.iac.core.JobResult;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
class OtfApiTest {
 @Test void submitPlanAndReadTerminalStatus() throws Exception {
  HttpServer stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  AtomicReference<String> request=new AtomicReference<>();
  stub.createContext("/api/v2/runs", exchange -> {
   request.set(exchange.getRequestMethod()+" "+exchange.getRequestURI()+" "+new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   byte[] body="{\"data\":{\"id\":\"run-1\",\"attributes\":{\"status\":\"pending\"}}}".getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/vnd.api+json");
   exchange.sendResponseHeaders(201,body.length);try(OutputStream o=exchange.getResponseBody()){o.write(body);}
  });
  stub.createContext("/api/v2/runs/run-1", exchange -> {
   byte[] body="{\"data\":{\"id\":\"run-1\",\"attributes\":{\"status\":\"planned_and_finished\"}}}".getBytes(StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,body.length);try(OutputStream o=exchange.getResponseBody()){o.write(body);}
  });
  stub.start();
  try(HttpJsonClient client=new HttpJsonClient("http://127.0.0.1:"+stub.getAddress().getPort(),"unit-test-token",true)) {
   OtfApi api=new OtfApi(client);
   JobResult result=api.submit("ws-1","plan");
   assertEquals("run-1",result.id());
   assertFalse(result.completed());
   assertTrue(request.get().startsWith("POST /api/v2/runs"));
   assertTrue(request.get().contains("\"plan-only\":true"));
   assertTrue(api.status("run-1").successful());
  } finally {stub.stop(0);}
 }
}
