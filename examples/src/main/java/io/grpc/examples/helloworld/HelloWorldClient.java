/*
 * Copyright 2015, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.examples.helloworld;

import com.google.instrumentation.common.NonThrowingCloseable;
import com.google.instrumentation.trace.Samplers;
import com.google.instrumentation.trace.TraceConfig.TraceParams;
import com.google.instrumentation.trace.TraceExporter;
import com.google.instrumentation.trace.Tracer;
import com.google.instrumentation.trace.Tracing;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.AbstractManagedChannelImplBuilder;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A simple client that requests a greeting from the {@link HelloWorldServer}. */
public class HelloWorldClient {
  private static final Logger logger = Logger.getLogger(HelloWorldClient.class.getName());
  private static final Tracer tracer = Tracing.getTracer();

  private final ManagedChannel channel;
  private final GreeterGrpc.GreeterBlockingStub blockingStub;

  /** Construct client connecting to HelloWorld server at {@code host:port}. */
  public HelloWorldClient(String host, int port) {
    channelBuilder = ManagedChannelBuilder.forAddress(host, port)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext(true);

    if (channelBuilder instanceof AbstractManagedChannelImplBuilder) {
      ((AbstractManagedChannelImplBuilder)channelBuilder).setEnableStatsTagPropagation(true);
      ((AbstractManagedChannelImplBuilder)channelBuilder).setEnableTracing(true);
    } else {
      logger.warning("This should not happen.");
    }
    this(channelBuilder.build());
  }

  /** Construct client for accessing RouteGuide server using the existing channel. */
  HelloWorldClient(ManagedChannel channel) {
    this.channel = channel;
    blockingStub = GreeterGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public void greet(String name) {
    logger.info("Will try to greet " + name + " ...");
    HelloRequest request = HelloRequest.newBuilder().setName(name).build();
    HelloReply response;
    try {
      response = blockingStub.sayHello(request);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return;
    }
    logger.info("Greeting: " + response.getMessage());
  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
    TraceExporter.LoggingServiceHandler.registerService(Tracing.getTraceExporter());
    Tracing.getTraceConfig()
        .updateActiveTraceParams(
            TraceParams.DEFAULT.toBuilder().setSampler(Samplers.alwaysSample()).build());
    try (NonThrowingCloseable ss =
        tracer.spanBuilder("MyRootSpan").becomeRoot().startScopedSpan()) {
      HelloWorldClient client = new HelloWorldClient("localhost", 50051);
      try {
        /* Access a service running on the local machine on port 50051 */
        String user = "world";
        if (args.length > 0) {
          user = args[0]; /* Use the arg as the name to greet if provided */
        }
        client.greet(user);
      } finally {
        client.shutdown();
      }
    }
  }
}
