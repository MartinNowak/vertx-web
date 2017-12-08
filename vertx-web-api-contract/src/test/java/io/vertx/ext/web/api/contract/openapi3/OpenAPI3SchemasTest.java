package io.vertx.ext.web.api.contract.openapi3;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.api.validation.WebTestValidationBase;
import io.vertx.ext.web.handler.StaticHandler;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

/**
 * This tests check the building of JSON schemas from OAS schemas and validation of JSON
 * @author Francesco Guardiani @slinkydeveloper
 */
public class OpenAPI3SchemasTest extends WebTestValidationBase {

  final String OAS_PATH = "./src/test/resources/swaggers/schemas_test_spec.yaml";

  OpenAPI3RouterFactory routerFactory;
  HttpServer schemaServer;

  final Handler<RoutingContext> handler = routingContext -> {
    routingContext.response().setStatusCode(200).end("OK");
  };

  final Handler<RoutingContext> FAILURE_HANDLER = routingContext -> {
    if (routingContext.failure() instanceof ValidationException)
      routingContext.response().setStatusCode(400).setStatusMessage("ValidationException").end(routingContext.failure().toString());
    else
      routingContext.response().setStatusCode(500).setStatusMessage("Error").end(routingContext.failure().toString());
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    stopServer(); // Have to stop default server of WebTestBase
    startSchemaServer();

    CountDownLatch latch = new CountDownLatch(1);
    OpenAPI3RouterFactory.createRouterFactoryFromFile(this.vertx, OAS_PATH, openAPI3RouterFactoryAsyncResult -> {
      assertTrue(openAPI3RouterFactoryAsyncResult.succeeded());
      assertNull(openAPI3RouterFactoryAsyncResult.cause());
      routerFactory = openAPI3RouterFactoryAsyncResult.result();
      routerFactory.enableValidationFailureHandler(true);
      routerFactory.setValidationFailureHandler(FAILURE_HANDLER);
      routerFactory.mountOperationsWithoutHandlers(false);
      latch.countDown();
    });
    awaitLatch(latch);

    client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8080));
  }

  @Override
  public void tearDown() throws Exception {
    if (client != null)
      client.close();

    stopSchemaServer();
    stopServer();
    super.tearDown();
  }

  private void startServer() throws Exception {
    router = routerFactory.getRouter();
    server = this.vertx.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"));
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(router::accept).listen(onSuccess(res -> {
      latch.countDown();
    }));
    awaitLatch(latch);
  }

  private void stopServer() throws Exception {
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      try {
        server.close((asyncResult) -> {
          latch.countDown();
        });
      } catch (IllegalStateException e) { // Server is already open
        latch.countDown();
      }
      awaitLatch(latch);
    }
  }

  private void startSchemaServer() throws Exception {
    Router r = Router.router(vertx);
    r.route().handler(StaticHandler.create("./src/test/resources/swaggers/schemas"));
    CountDownLatch latch = new CountDownLatch(1);
    schemaServer = vertx.createHttpServer(new HttpServerOptions().setPort(8081))
      .requestHandler(r::accept).listen(onSuccess(res -> {
        latch.countDown();
      }));
    awaitLatch(latch);;
  }

  private void stopSchemaServer() throws Exception {
    if (schemaServer != null) {
      CountDownLatch latch = new CountDownLatch(1);
      try {
        schemaServer.close((asyncResult) -> {
          latch.countDown();
        });
      } catch (IllegalStateException e) { // Server is already open
        latch.countDown();
      }
      awaitLatch(latch);
    }
  }

  private void assertRequestOk(String uri, String jsonName) throws Exception {
    String jsonString = String.join("", Files.readAllLines(Paths.get("./src/test/resources/swaggers/test_json", "schemas_test", jsonName), StandardCharsets.UTF_8));
    testRequestWithJSON(HttpMethod.GET, uri, new JsonObject(jsonString), 200, "OK");
  };

  private void assertRequestFail(String uri, String jsonName) throws Exception {
    String jsonString = String.join("", Files.readAllLines(Paths.get("./src/test/resources/swaggers/test_json", "schemas_test", jsonName), StandardCharsets.UTF_8));
    testRequestWithJSON(HttpMethod.GET, uri, new JsonObject(jsonString), 400, "ValidationException");
  };

  @Test
  public void test1() throws Exception {
    routerFactory.addHandlerByOperationId("test1", handler);
    startServer();
    assertRequestOk("/test1", "test1_ok.json");
    assertRequestFail("/test1", "test1_fail_1.json");
    assertRequestFail("/test1", "test1_fail_2.json");
  }

  @Test
  public void test2() throws Exception {
    routerFactory.addHandlerByOperationId("test2", handler);
    startServer();
    assertRequestOk("/test2", "test2_ok.json");
    assertRequestFail("/test2", "test2_fail_1.json");
    assertRequestFail("/test2", "test2_fail_2.json");
    assertRequestFail("/test2", "test2_fail_3.json");
  }

  @Test
  public void test3() throws Exception {
    routerFactory.addHandlerByOperationId("test3", handler);
    startServer();
    assertRequestOk("/test3", "test2_ok.json"); // Same as test2
    assertRequestFail("/test3", "test2_fail_1.json");
    assertRequestFail("/test3", "test2_fail_2.json");
    assertRequestFail("/test3", "test2_fail_3.json");
  }

  @Test
  @Ignore // This test doesn't work because it's affected by https://github.com/swagger-api/swagger-parser/issues/596
  public void test4() throws Exception {
    routerFactory.addHandlerByOperationId("test4", handler);
    startServer();
    assertRequestOk("/test4", "test2_ok.json"); // Same as test2
    assertRequestFail("/test4", "test2_fail_1.json");
    assertRequestFail("/test4", "test2_fail_2.json"); // TODO Parser doesn't load local ref, maybe a parser bug?
    assertRequestFail("/test4", "test2_fail_3.json");
  }

  @Test
  public void test5() throws Exception {
    routerFactory.addHandlerByOperationId("test5", handler);
    startServer();
    assertRequestOk("/test5", "test5_ok_1.json");
    assertRequestOk("/test5", "test5_ok_2.json");
    assertRequestFail("/test5", "test5_fail.json");
  }

  @Test
  public void test6() throws Exception {
    routerFactory.addHandlerByOperationId("test6", handler);
    startServer();
    assertRequestOk("/test6", "test6_ok.json");
    assertRequestFail("/test6", "test6_fail.json");
  }

  @Test
  public void test7() throws Exception {
    routerFactory.addHandlerByOperationId("test7", handler);
    startServer();
    assertRequestOk("/test7", "test2_ok.json"); // PersonComplex should work
    assertRequestOk("/test7", "test7_ok_1.json");
    assertRequestOk("/test7", "test7_ok_2.json");
    assertRequestFail("/test7", "test7_fail_1.json");
    assertRequestFail("/test7", "test7_fail_2.json");
  }

  @Test
  @Ignore // This test doesn't work because it's affected by https://github.com/swagger-api/swagger-parser/issues/596
  public void test8() throws Exception {
    routerFactory.addHandlerByOperationId("test8", handler);
    startServer();
    assertRequestOk("/test8", "test8_ok.json");
    assertRequestFail("/test8", "test8_fail_1.json");
    assertRequestFail("/test8", "test8_fail_2.json");
  }

  @Test
  @Ignore // This test doesn't work because it's affected by https://github.com/swagger-api/swagger-parser/issues/596
  public void test9() throws Exception {
    routerFactory.addHandlerByOperationId("test9", handler);
    startServer();
    assertRequestOk("/test9", "test6_ok.json"); // Test6 should work
    assertRequestOk("/test9", "test9_ok.json");
    assertRequestFail("/test8", "test9_fail.json");
  }

  @Test
  public void test10() throws Exception {
    routerFactory.addHandlerByOperationId("test10", handler);
    startServer();
    assertRequestOk("/test10", "test10_ok.json");
    assertRequestFail("/test10", "test10_fail.json");
  }

  @Test
  public void test11() throws Exception {
    routerFactory.addHandlerByOperationId("test11", handler);
    startServer();
    assertRequestOk("/test11", "test10_ok.json");
    assertRequestFail("/test11", "test10_fail.json");
  }

  @Test
  public void test12() throws Exception {
    routerFactory.addHandlerByOperationId("test12", handler);
    startServer();
    assertRequestOk("/test12", "test12_ok.json");
    assertRequestFail("/test12", "test12_fail.json");
  }

}
