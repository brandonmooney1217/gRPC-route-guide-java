# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a gRPC-based RouteGuide service implementation in Java using Maven. The service demonstrates all four types of gRPC communication patterns:
- **Unary RPC**: `GetFeature` - single request/response
- **Server-side streaming**: `ListFeatures` - single request, stream of responses
- **Client-side streaming**: `RecordRoute` - stream of requests, single response
- **Bidirectional streaming**: (not yet implemented)

The service operates on geographical features (points, locations) and includes production-grade features like interceptors, request hedging, and custom headers.

## Build and Run Commands

### Build the project
```bash
mvn clean package
```

This compiles Protocol Buffers, generates gRPC stubs, and creates a fat JAR with all dependencies at `target/route-guide-1.0-SNAPSHOT.jar`.

### Run the server
```bash
java -cp target/route-guide-1.0-SNAPSHOT.jar io.grpc.examples.routeguide.RouteGuideServer
```

The server listens on port 8980.

### Run the client
```bash
java -cp target/route-guide-1.0-SNAPSHOT.jar io.grpc.examples.routeguide.RouteGuideClient
```

By default, the client connects to `localhost:8980`, but accepts an optional target argument.

### Docker build and run
```bash
DOCKER_BUILDKIT=0 docker build -t routeguide-grpc:latest .
docker run -p 8980:8980 routeguide-grpc:latest
```

Note: DOCKER_BUILDKIT=0 is required due to build configuration.

## Architecture

### Protocol Buffers Definition
The service contract is defined in [src/main/proto/route-guide.proto](src/main/proto/route-guide.proto). Maven's protobuf plugin automatically generates Java classes during compilation into `target/generated-sources/protobuf/`.

### Server Architecture
[RouteGuideServer](src/main/java/io/grpc/examples/routeguide/RouteGuideServer.java) contains:
- **RouteGuideService**: Inner class implementing the gRPC service methods
- **Interceptor chain**: Applied in order (LatencyInjectionInterceptor → HeaderServerInterceptor)
- **Feature loading**: Loads geographical features from `route_guide_db.json` at startup

### Client Architecture
[RouteGuideClient](src/main/java/io/grpc/examples/routeguide/RouteGuideClient.java) demonstrates:
- **Blocking stub**: Used for synchronous unary calls (GetFeature, ListFeatures)
- **Async stub**: Used for streaming calls (RecordRoute)
- **Request hedging**: Configured via `hedging_service_config.json` to send backup requests after 1s delay
- **TLS override**: Currently configured to connect with authority override to `brandon-mooney.com`

### Interceptors
Two types of interceptors are implemented:

1. **LatencyInjectionInterceptor** ([src/main/java/io/grpc/examples/routeguide/interceptor/LatencyInjectionInterceptor.java](src/main/java/io/grpc/examples/routeguide/interceptor/LatencyInjectionInterceptor.java))
   - Randomly injects latency (2s-10s) into server responses
   - Used to demonstrate request hedging behavior

2. **Header Interceptors** ([HeaderServerInterceptor](src/main/java/io/grpc/examples/routeguide/header/HeaderServerInterceptor.java) and [HeaderClientInterceptor](src/main/java/io/grpc/examples/routeguide/header/HeaderClientInterceptor.java))
   - Add custom metadata headers to requests and responses
   - Server adds `custom_server_header_key` to responses
   - Client can add custom headers to requests

### Request Hedging Configuration
The [hedging_service_config.json](src/main/java/io/grpc/examples/routeguide/util/hedging_service_config.json) enables automatic backup requests for the GetFeature RPC:
- **maxAttempts**: 3
- **hedgingDelay**: 1 second (sends backup request if first doesn't complete within 1s)
- **retryThrottling**: Limits retry/hedge attempts using token bucket algorithm

## Key Implementation Details

### Service Methods
- **GetFeature**: Looks up a feature by exact lat/lon coordinates from in-memory feature list
- **ListFeatures**: Streams all features within a given rectangle (bounding box)
- **RecordRoute**: Client streams points, server counts total points and how many matched known features

### Data Flow
1. Features are loaded from `src/main/resources/io/grpc/examples/routeguide/route_guide_db.json` at server startup
2. Coordinates use E7 representation (multiplied by 10^7 for precision)
3. [RouteGuideUtil](src/main/java/io/grpc/examples/routeguide/RouteGuideUtil.java) provides helper methods for coordinate conversion and feature parsing

### Maven Configuration Notes
- Uses `protobuf-maven-plugin` to auto-generate Java classes from .proto files
- Uses `maven-shade-plugin` to create an executable fat JAR with `RouteGuideServer` as the main class
- Requires Java 11+
- gRPC version: 1.60.0
- Protobuf version: 3.25.1
