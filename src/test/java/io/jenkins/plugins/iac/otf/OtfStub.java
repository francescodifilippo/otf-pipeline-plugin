package io.jenkins.plugins.iac.otf;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-memory stub of the OTF workspace, variable and run API for organization acme. */
final class OtfStub implements AutoCloseable {
 private final HttpServer server;
 private final ExecutorService executor=Executors.newCachedThreadPool();
 /** Workspaces of organization acme by name. */
 final Map<String,String> workspaces=new ConcurrentHashMap<>();
 /** Variables by workspace ID, as "category:key". */
 final Map<String,List<String>> vars=new ConcurrentHashMap<>();
 final List<JsonNode> creates=new CopyOnWriteArrayList<>(),varCreates=new CopyOnWriteArrayList<>();
 final List<String> runs=new CopyOnWriteArrayList<>();
 /** When set, the next workspace create answers 409, as if another build had just created this workspace. */
 volatile String createdConcurrently;
 /** Variable key whose create answers 422. */
 volatile String failingVariable;
 /** When set, the next workspace create is stored and then not answered until {@link #close}. */
 volatile boolean holdCreate;
 /** Variable key whose create is stored and then not answered until {@link #close}. */
 volatile String holdVariable;
 /** Counted down when a request is held. */
 final CountDownLatch held=new CountDownLatch(1);
 private final CountDownLatch released=new CountDownLatch(1);

 OtfStub() throws IOException {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  // Several threads, so that a held request does not block the others.
  server.setExecutor(executor);
  server.createContext("/api/v2/organizations/acme/workspaces",this::workspaces);
  server.createContext("/api/v2/workspaces/",this::variables);
  server.createContext("/api/v2/runs",exchange -> {
   runs.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   respond(exchange,201,"{\"data\":{\"id\":\"run-1\",\"attributes\":{\"status\":\"planned_and_finished\"}}}");
  });
  server.start();
 }
 String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
 @Override public void close(){released.countDown();server.stop(0);executor.shutdownNow();}

 private void workspaces(HttpExchange exchange) throws IOException {
  String path=exchange.getRequestURI().getRawPath();
  if("POST".equals(exchange.getRequestMethod())){
   JsonNode body=HttpJsonClient.JSON.readTree(exchange.getRequestBody());
   creates.add(body);
   String name=body.path("data").path("attributes").path("name").asText();
   if(name.equals(createdConcurrently)){workspaces.put(name,"ws-other");createdConcurrently=null;}
   if(workspaces.containsKey(name)){respond(exchange,409,"{\"errors\":[{\"status\":\"409\"}]}");return;}
   String id="ws-new"+(workspaces.size()+1);
   workspaces.put(name,id);
   if(holdCreate){holdCreate=false;hold();}
   respond(exchange,201,workspace(id,name));
   return;
  }
  String name=path.substring(path.lastIndexOf('/')+1);
  String id=workspaces.get(name);
  if(id==null)respond(exchange,404,"{\"errors\":[{\"status\":\"404\"}]}");
  else respond(exchange,200,workspace(id,name));
 }
 private void variables(HttpExchange exchange) throws IOException {
  String id=exchange.getRequestURI().getRawPath().split("/")[4];
  List<String> list=vars.computeIfAbsent(id,k->new CopyOnWriteArrayList<>());
  if("POST".equals(exchange.getRequestMethod())){
   JsonNode body=HttpJsonClient.JSON.readTree(exchange.getRequestBody());
   varCreates.add(body);
   JsonNode a=body.path("data").path("attributes");
   if(a.path("key").asText().equals(failingVariable)){respond(exchange,422,"{\"errors\":[{\"status\":\"422\"}]}");return;}
   list.add(a.path("category").asText()+":"+a.path("key").asText());
   if(a.path("key").asText().equals(holdVariable)){holdVariable=null;hold();}
   respond(exchange,201,"{\"data\":{\"id\":\"var-"+list.size()+"\",\"type\":\"vars\"}}");
   return;
  }
  StringBuilder b=new StringBuilder("{\"data\":[");
  for(int i=0;i<list.size();i++){
   String[] v=list.get(i).split(":",2);
   b.append(i==0?"":",").append("{\"type\":\"vars\",\"attributes\":{\"category\":\"").append(v[0]).append("\",\"key\":\"").append(v[1]).append("\"}}");
  }
  respond(exchange,200,b.append("]}").toString());
 }

 /** Keeps the request open, as if the response were lost, until the stub closes. */
 private void hold(){
  held.countDown();
  try{released.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
 private static String workspace(String id,String name){
  return "{\"data\":{\"id\":\""+id+"\",\"type\":\"workspaces\",\"attributes\":{\"name\":\""+name+"\"}}}";
 }
 private static void respond(HttpExchange exchange,int code,String body) throws IOException {
  byte[] b=body.getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().set("Content-Type","application/vnd.api+json");
  exchange.sendResponseHeaders(code,b.length);
  try(OutputStream o=exchange.getResponseBody()){o.write(b);}
 }
}
