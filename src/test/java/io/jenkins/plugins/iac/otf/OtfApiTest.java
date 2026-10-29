package io.jenkins.plugins.iac.otf;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
   assertTrue(request.get().contains("\"auto-apply\":false"));
   assertTrue(api.status("run-1").successful());
  } finally {stub.stop(0);}
 }
 /** Every runstatus.Status constant of leg100/otf internal/runstatus/runstatus.go, plus non-OTF strings. */
 @ParameterizedTest(name="{0}")
 @CsvSource({
  "pending,false,false,false",
  "plan_queued,false,false,false",
  "planning,false,false,false",
  "planned,false,false,true",
  "cost_estimated,false,false,true",
  "confirmed,false,false,false",
  "apply_queued,false,false,false",
  "applying,false,false,false",
  "applied,true,true,false",
  "planned_and_finished,true,true,false",
  "errored,true,false,false",
  "canceled,true,false,false",
  "force_canceled,true,false,false",
  "discarded,true,false,false",
  "needs_confirmation,false,false,false",
  "policy_checked,false,false,false",
  "something_new,false,false,false"
 })
 void mapsOtfRunStatuses(String status,boolean completed,boolean successful,boolean needsApproval) throws Exception {
  JobResult r=OtfApi.parse(HttpJsonClient.JSON.readTree("{\"data\":{\"id\":\"run-1\",\"attributes\":{\"status\":\""+status+"\"}}}"));
  assertEquals(status,r.status());
  assertEquals(completed,r.completed(),"completed");
  assertEquals(successful,r.successful(),"successful");
  assertEquals(needsApproval,r.needsApproval(),"needsApproval");
 }
}
