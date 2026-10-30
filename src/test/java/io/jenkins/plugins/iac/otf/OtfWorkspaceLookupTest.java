package io.jenkins.plugins.iac.otf;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import io.jenkins.plugins.iac.core.IacHttpException;
import io.jenkins.plugins.iac.core.ResolvedTarget;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Workspace lookup by name against a stub of OTF's GET /api/v2/organizations/:organization/workspaces/:name. */
class OtfWorkspaceLookupTest {
 private HttpServer stub;
 private HttpJsonClient client;
 private final List<String> requests=new CopyOnWriteArrayList<>();
 private volatile int code=200;
 private volatile String body;

 @BeforeEach void setUp() throws IOException {
  stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  stub.createContext("/api/v2/organizations/",exchange -> {
   requests.add(exchange.getRequestMethod()+" "+exchange.getRequestURI().getRawPath()+" "+exchange.getRequestHeaders().getFirst("Authorization"));
   byte[] b=(code==200?body:"{\"errors\":[{\"status\":\""+code+"\"}]}").getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/vnd.api+json");
   exchange.sendResponseHeaders(code,b.length);try(OutputStream o=exchange.getResponseBody()){o.write(b);}
  });
  stub.start();
  client=new HttpJsonClient("http://127.0.0.1:"+stub.getAddress().getPort(),"unit-test-token",true);
 }
 @AfterEach void tearDown(){client.close();stub.stop(0);}

 private static String workspace(String type,String id,String name){
  return "{\"data\":{\"id\":\""+id+"\",\"type\":\""+type+"\",\"attributes\":{\"name\":\""+name+"\"}}}";
 }
 private String lookup(String organization,String name) throws Exception {
  return new OtfApi(client).workspaceIdByName(organization,name);
 }

 @Test void foundReturnsTheWorkspaceId() throws Exception {
  body=workspace("workspaces","ws-abc123","network.prod");
  assertEquals("ws-abc123",lookup("acme","network.prod"));
  assertEquals(List.of("GET /api/v2/organizations/acme/workspaces/network.prod Bearer unit-test-token"),requests);
 }
 @Test void notFoundNamesWorkspaceAndOrganization() throws Exception {
  code=404;
  var e=assertThrows(IllegalArgumentException.class,()->lookup("acme","network"));
  assertEquals("No OTF workspace named 'network' in organization 'acme'; check organizationName and workspaceName, or use workspaceId",e.getMessage());
  assertEquals(1,requests.size());
 }
 @Test void otherHttpErrorsPropagate(){
  code=403;
  assertEquals(403,assertThrows(IacHttpException.class,()->lookup("acme","network")).statusCode());
  code=500;
  assertEquals(500,assertThrows(IacHttpException.class,()->lookup("acme","network")).statusCode());
 }
 @Test void rejectsAnotherResourceType(){
  body=workspace("organizations","acme","network");
  var e=assertThrows(IOException.class,()->lookup("acme","network"));
  assertEquals("OTF did not return workspace 'network' of organization 'acme'",e.getMessage());
 }
 @Test void comparesTheReturnedNameExactly(){
  for(String other:new String[]{"Network","network ","network-dev"}){
   body=workspace("workspaces","ws-abc123",other);
   assertThrows(IOException.class,()->lookup("acme","network"),other);
  }
  body="{\"data\":{\"id\":\"ws-abc123\",\"type\":\"workspaces\",\"attributes\":{}}}";
  assertThrows(IOException.class,()->lookup("acme","network"));
 }
 @Test void rejectsAMissingOrInvalidId(){
  body="{\"data\":{\"type\":\"workspaces\",\"attributes\":{\"name\":\"network\"}}}";
  assertThrows(IllegalArgumentException.class,()->lookup("acme","network"));
  body=workspace("workspaces","ws/../x","network");
  assertThrows(IllegalArgumentException.class,()->lookup("acme","network"));
 }
 @Test void encodesEachNameAsOnePathSegment() throws Exception {
  String name="a/b c?d%é#",organization="my org/x";
  body="{\"data\":{\"id\":\"ws-1\",\"type\":\"workspaces\",\"attributes\":{\"name\":\"a/b c?d%é#\"}}}";
  assertEquals("ws-1",lookup(organization,name));
  assertEquals("GET /api/v2/organizations/my%20org%2Fx/workspaces/a%2Fb%20c%3Fd%25%C3%A9%23 Bearer unit-test-token",requests.get(0));
 }
 @Test void dotSegmentsAreRejectedBeforeAnyRequest(){
  assertThrows(IllegalArgumentException.class,()->lookup("acme",".."));
  assertThrows(IllegalArgumentException.class,()->lookup(".","network"));
  assertEquals(List.of(),requests);
 }

 @Test void backendUsesWorkspaceIdWithoutCallingOtf() throws Exception {
  Map<String,String> parameters=Map.of("mode","apply");
  ResolvedTarget resolved=new OtfBackend().resolve("otf-test","ws-1",parameters,null);
  assertEquals("ws-1",resolved.targetId());
  assertEquals(parameters,resolved.parameters());
  assertEquals(List.of(),requests);
 }
 @Test void backendRequiresWorkspaceIdOrBothNames(){
  assertThrows(IllegalArgumentException.class,()->new OtfBackend().resolve("otf-test",null,Map.of("mode","plan","workspaceName","network"),null));
  assertThrows(IllegalArgumentException.class,()->new OtfBackend().resolve("otf-test",null,Map.of("mode","plan","organizationName","acme"),null));
  assertEquals(List.of(),requests);
 }
}
