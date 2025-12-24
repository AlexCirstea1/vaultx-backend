# VaultX User Context Service

A secure, end-to-end encrypted messaging backend built with Spring Boot 3.4, featuring blockchain integration for audit trails, real-time WebSocket communication, and enterprise-grade security.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Core Components](#core-components)
  - [Authentication Module](#authentication-module)
  - [User Management](#user-management)
  - [Chat System](#chat-system)
  - [File Transfer](#file-transfer)
  - [Blockchain Integration](#blockchain-integration)
  - [Real-time Communication](#real-time-communication)
- [Data Flow](#data-flow)
- [Security Model](#security-model)
- [Database Schema](#database-schema)
- [API Documentation](#api-documentation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Testing](#testing)

---

## Overview

**VaultX User Context Service** is a microservice responsible for user authentication, secure messaging, file transfer, and blockchain-based audit logging. It serves as the backend for a privacy-focused communication platform where all messages are end-to-end encrypted on the client side.

### Key Features

- **JWT-based Authentication** with RSA-256 signed tokens and refresh token rotation
- **End-to-End Encrypted Messaging** with client-side encryption (ciphertext stored server-side)
- **Real-time WebSocket Communication** using STOMP protocol
- **Chat Request System** requiring explicit consent before initiating conversations
- **Group Chat Support** with multi-participant messaging
- **Encrypted File Transfer** via MinIO object storage
- **Blockchain Audit Trail** via Hyperledger Fabric integration
- **User Presence Tracking** (online/offline status with last seen)
- **User Blocking & Reporting** functionality
- **Activity Logging** for security monitoring

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Applications                            │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │     REST API / WebSocket  │
                    └─────────────┬─────────────┘
                                  │
┌─────────────────────────────────┴───────────────────────────────────────────┐
│                         User Context Service                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│  │    Auth      │ │    Chat      │ │    File      │ │  Blockchain  │        │
│  │  Controller  │ │  Controller  │ │  Controller  │ │  Controller  │        │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘        │
│         │                │                │                │                │
│  ┌──────┴───────┐ ┌──────┴───────┐ ┌──────┴───────┐ ┌──────┴───────┐        │
│  │   Login      │ │   Private    │ │    Chat      │ │  Blockchain  │        │
│  │   Service    │ │   Chat Svc   │ │   File Svc   │ │   Service    │        │
│  │   Token Svc  │ │   Group Svc  │ │   Storage    │ │              │        │
│  │   PIN Svc    │ │   Request    │ │   Service    │ │              │        │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘        │
│         │                │                │                │                │
└─────────┼────────────────┼────────────────┼────────────────┼────────────────┘
          │                │                │                │
    ┌─────┴─────┐    ┌─────┴─────┐    ┌─────┴─────┐    ┌─────┴─────┐
    │ PostgreSQL│    │   Redis   │    │   MinIO   │    │   Kafka   │
    │    (DB)   │    │ (Session) │    │  (Files)  │    │  (Events) │
    └───────────┘    └───────────┘    └───────────┘    └─────┬─────┘
                                                             │
                                                       ┌─────┴─────┐
                                                       │Hyperledger│
                                                       │  Fabric   │
                                                       └───────────┘
```

---

## Technology Stack

| Category | Technology | Version |
|----------|------------|---------|
| **Framework** | Spring Boot | 3.4.4 |
| **Language** | Java | 21 |
| **Database** | PostgreSQL | Latest |
| **Cache/Session** | Redis | Latest |
| **Object Storage** | MinIO | 8.5.2 |
| **Message Broker** | Apache Kafka | 7.4.0 |
| **Blockchain** | Hyperledger Fabric | External Service |
| **Security** | Spring Security OAuth2 + JWT | — |
| **WebSocket** | Spring WebSocket (STOMP) | — |
| **API Docs** | SpringDoc OpenAPI | 2.8.5 |
| **Build Tool** | Maven | — |
| **Mapping** | MapStruct + ModelMapper | 1.5.5 / 3.2.0 |

---

## Project Structure

```
src/main/java/com/vaultx/user/context/
├── UserContextApplication.java          # Application entry point, role initialization
├── configuration/
│   ├── KafkaConfig.java                 # Kafka producer configuration
│   ├── MinioConfig.java                 # MinIO client configuration
│   ├── ModelMapperConfiguration.java    # ModelMapper bean setup
│   ├── OpenApiConfig.java               # Swagger/OpenAPI configuration
│   ├── RedisConfig.java                 # Redis template configuration
│   ├── RestTemplateConfig.java          # HTTP client for Hyperledger
│   ├── security/
│   │   ├── CorsConfig.java              # CORS policy configuration
│   │   └── SecurityConfiguration.java   # Spring Security filter chain, JWT setup
│   └── websocket/
│       ├── WebSocketConfig.java         # STOMP broker configuration
│       └── listener/                    # WebSocket event listeners
├── controller/
│   ├── AuthenticationController.java    # Login, register, token refresh, PIN
│   ├── BlockchainController.java        # DID event queries, export, stats
│   ├── ChatController.java              # Messaging, chat requests, groups
│   ├── FileController.java              # Encrypted file upload/download
│   ├── PresenceController.java          # User heartbeat (online status)
│   └── UserController.java              # Profile, public keys, blocking, reports
├── exception/
│   ├── CustomBadCredentialsException.java
│   ├── GlobalExceptionHandler.java      # Centralized error handling
│   ├── UserAlreadyExistsException.java
│   └── UserNotFoundException.java
├── jobs/
│   └── ChatRequestExpirationJob.java    # Scheduled task to expire old requests
├── mapper/
│   ├── ChatMessageMapper.java           # Entity ↔ DTO mapping (MapStruct)
│   └── ChatRequestMapper.java
├── model/
│   ├── activity/                        # Activity logging entities
│   ├── authentication/                  # DTOs for login, registration, tokens
│   ├── blockchain/                      # DIDEvent, EventType, EventHistory
│   ├── file/                            # ChatFile, FileUploadMeta, etc.
│   ├── messaging/                       # ChatMessage, ChatRequest, GroupChat
│   └── user/                            # User, Role, UserBlock, UserReport
├── repository/                          # Spring Data JPA repositories
├── service/
│   ├── authentication/
│   │   ├── LoginService.java            # Authentication, token generation
│   │   ├── PinService.java              # 6-digit PIN management
│   │   ├── RegistrationService.java     # User registration logic
│   │   └── TokenService.java            # JWT encode/decode, session storage
│   ├── chat/
│   │   ├── ChatRequestService.java      # Chat invitation workflow
│   │   ├── ChatService.java             # Facade for all chat operations
│   │   ├── GroupChatService.java        # Group messaging logic
│   │   └── PrivateChatService.java      # 1:1 messaging, read receipts
│   ├── file/
│   │   ├── ChatFileService.java         # File metadata, authorization
│   │   └── FileStorageService.java      # MinIO operations
│   ├── kafka/
│   │   ├── JsonSerializer.java          # Custom Kafka serializer
│   │   └── KafkaProducer.java           # Event publishing to topics
│   └── user/
│       ├── ActivityService.java         # Activity logging
│       ├── BlockchainService.java       # Hyperledger Fabric integration
│       ├── BlockService.java            # User blocking logic
│       ├── KeyManagementService.java    # Public key storage, rotation
│       ├── PresenceService.java         # Online/offline status
│       ├── ReportService.java           # User reporting
│       ├── SessionService.java          # Session extraction from JWT
│       └── UserService.java             # Core user operations
└── utils/
    ├── CipherUtils.java                 # Hashing utilities for blockchain
    ├── KeyGeneratorUtility.java         # RSA key pair generation
    ├── RSAKeyProperties.java            # RSA key configuration holder
    └── Utils.java                       # IP extraction, general helpers
```

---

## Core Components

### Authentication Module

The authentication system uses **JWT tokens** with RSA-256 signatures for stateless authentication.

#### Key Services

| Service | Responsibility |
|---------|----------------|
| `LoginService` | Validates credentials, generates access/refresh tokens, logs activities |
| `TokenService` | Encodes/decodes JWTs, manages refresh tokens in Redis, validates sessions |
| `RegistrationService` | Creates new users with password hashing, assigns roles, generates avatars |
| `PinService` | Manages optional 6-digit PIN for additional security |

#### Authentication Flow

```
1. Client sends POST /api/auth/login with {username, password}
2. LoginService validates credentials via AuthenticationManager
3. TokenService generates JWT (30 min TTL) and refresh token (60 min TTL)
4. Session stored in Redis with client IP and User-Agent
5. ActivityService logs the login event
6. Response includes access_token, refresh_token, and user profile
```

#### Token Claims Structure

```json
{
  "sub": "user-uuid",
  "username": "john_doe",
  "email": "john@example.com",
  "role": "ROLE_USER ROLE_VERIFIED",
  "publicKey": "base64-encoded-rsa-public-key",
  "iat": 1703424000,
  "exp": 1703425800
}
```

#### Security Features

- **Failed Login Tracking**: After 3 failed attempts within 30 minutes, a security alert is logged
- **Suspicious Activity Detection**: IP address changes trigger security events
- **Refresh Token Rotation**: New refresh token issued on each refresh

---

### User Management

The `UserService` orchestrates user operations by delegating to specialized services.

#### User Entity

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `username` | String | Unique identifier |
| `email` | String | Unique email address |
| `password` | String | BCrypt hashed |
| `pin` | String | Optional 6-digit PIN (hashed) |
| `publicKey` | String | Client's E2E encryption public key |
| `currentKeyVersion` | String | Key rotation version identifier |
| `profileImage` | String | SVG avatar (base64) |
| `blockchainConsent` | boolean | Consent for blockchain audit |
| `isOnline` | boolean | Current presence status |
| `lastSeen` | Instant | Last activity timestamp |
| `strikeCount` | int | Number of reports received |

#### Role System

| Role | Description |
|------|-------------|
| `ROLE_USER` | Default role for all users |
| `ROLE_VERIFIED` | Registered with email verification |
| `ROLE_ANONYMOUS` | Quick-registration users |
| `ROLE_ADMIN` | Administrative privileges |

#### Key Management

- Users can rotate their encryption keys via `KeyManagementService`
- Key history is maintained in `UserKeyHistory` for message decryption
- Key rotation events are recorded on the blockchain

---

### Chat System

The messaging system supports **end-to-end encrypted** private and group chats.

#### Service Architecture

```
ChatService (Facade)
    ├── PrivateChatService    → 1:1 messaging
    ├── GroupChatService      → Multi-participant chats
    └── ChatRequestService    → Consent-based chat initiation
```

#### Chat Request Workflow

Before users can exchange messages, they must go through a consent flow:

```
1. Sender calls POST /api/chat-requests with encrypted intro message
2. System validates no blocking exists between users
3. ChatRequest saved with status=PENDING
4. Recipient receives WebSocket notification at /user/{id}/queue/chatRequests
5. Recipient accepts/rejects via POST /api/chat-requests/{id}/accept or /reject
6. On accept: Initial message is persisted, conversation can begin
7. Pending requests auto-expire after 2 days (scheduled job)
```

#### Message Structure

Messages are stored with **client-side encrypted content**:

```java
ChatMessage {
    sender: User
    recipient: User
    ciphertext: String          // AES-encrypted content (base64)
    iv: String                  // Initialization vector
    encryptedKeyForSender: String    // AES key encrypted with sender's public key
    encryptedKeyForRecipient: String // AES key encrypted with recipient's public key
    senderKeyVersion: String
    recipientKeyVersion: String
    messageType: NORMAL | FILE
    oneTime: boolean            // Self-destructing message flag
    isRead: boolean
    readTimestamp: LocalDateTime
}
```

#### Real-time Messaging

Private messages are delivered via WebSocket:

```
Client → POST /app/sendPrivateMessage
Server → Persists message in PostgreSQL
Server → Publishes to /user/{recipientId}/queue/private
Server → Confirms to /user/{senderId}/queue/confirmation
```

#### Read Receipts

- Clients mark messages as read via `/app/markAsRead` or REST endpoint
- Read receipts are broadcast back to the sender

#### Caching Strategy

- Conversations are cached in Redis for 24 hours
- Cache invalidation occurs on new message or read status change

---

### File Transfer

Encrypted file transfer is tightly integrated with the chat system.

#### Upload Flow

```
1. Client encrypts file locally, generates FileUploadMeta
2. Client sends ChatMessage with messageType=FILE via WebSocket
3. Message persisted with placeholder ciphertext "__FILE__"
4. Client POSTs to /api/files with encrypted blob + metadata
5. ChatFileService validates sender is authorized for message
6. FileStorageService saves to MinIO bucket "vaultx-files"
7. BlockchainService records file hash for integrity verification
```

#### Download Flow

```
1. Client requests GET /api/files/{fileId}
2. ChatFileService verifies requester is sender or recipient
3. FileStorageService retrieves blob from MinIO
4. Response includes original filename and MIME type
```

#### File Validation

Files can be validated against blockchain records:

```
POST /api/files/{fileId}/validate
- Computes SHA-256 hash of stored/uploaded file
- Compares against payloadHash in blockchain event
- Returns match status with metadata
```

---

### Blockchain Integration

The service integrates with **Hyperledger Fabric** for immutable audit trails.

#### Event Types

| Event | Trigger |
|-------|---------|
| `USER_REGISTERED` | New user registration |
| `USER_KEY_ROTATED` | Public key rotation |
| `USER_ROLE_CHANGED` | Role assignment change |
| `CHAT_CREATED` | New conversation started |
| `FILE_UPLOAD` | File attachment uploaded |

#### DIDEvent Structure

```java
DIDEvent {
    eventId: UUID
    userId: UUID
    publicKey: String
    eventType: EventType
    timestamp: Instant
    payload: String          // JSON-serialized event data
    payloadHash: String      // SHA-256 of payload
    kafkaOffset: long
}
```

#### Kafka Topics

Events are published to topic-specific Kafka queues:

| Topic | Events |
|-------|--------|
| `users.registration` | USER_REGISTERED |
| `users.key-rotation` | USER_KEY_ROTATED |
| `users.role-change` | USER_ROLE_CHANGED |
| `chats.events` | CHAT_CREATED |
| `blockchain.transactions` | Default/FILE_UPLOAD |

#### Querying Events

The `BlockchainController` provides REST endpoints:

- `GET /api/blockchain/events?userId=...` - List events with optional filters
- `GET /api/blockchain/events/{id}` - Event details
- `GET /api/blockchain/events/{id}/history` - On-chain history
- `GET /api/blockchain/events/export?userId=...` - CSV export
- `GET /api/blockchain/events/stats?userId=...` - Event count by type

---

### Real-time Communication

WebSocket communication uses **STOMP** over SockJS.

#### Configuration

```java
// Broker destinations
registry.enableSimpleBroker("/topic", "/queue");
registry.setApplicationDestinationPrefixes("/app");
registry.setUserDestinationPrefix("/user");

// Endpoint
registry.addEndpoint("/ws");
```

#### Message Destinations

| Type | Destination | Purpose |
|------|-------------|---------|
| **Subscribe** | `/user/{id}/queue/private` | Receive private messages |
| **Subscribe** | `/user/{id}/queue/chatRequests` | Receive chat requests |
| **Subscribe** | `/user/{id}/queue/readReceipts` | Receive read receipts |
| **Subscribe** | `/user/{id}/queue/userSearchResults` | Receive search results |
| **Subscribe** | `/topic/presence` | Global presence updates |
| **Send** | `/app/sendPrivateMessage` | Send private message |
| **Send** | `/app/markAsRead` | Mark messages as read |
| **Send** | `/app/userSearch` | Search for users |
| **Send** | `/app/heartbeat` | Keep-alive presence |

#### Presence System

The `PresenceService` tracks user online status:

```
1. Client connects to /ws
2. WebSocket listener marks user online
3. Client sends periodic /app/heartbeat
4. On disconnect, user marked offline with lastSeen timestamp
5. Status changes broadcast to /topic/presence
```

---

## Data Flow

### Complete Message Flow

```
┌────────┐         ┌────────────────┐         ┌────────────────┐
│ Sender │         │ User Context   │         │   Recipient    │
└───┬────┘         │    Service     │         └───────┬────────┘
    │              └───────┬────────┘                 │
    │  1. Encrypt message  │                          │
    │  with AES key        │                          │
    │                      │                          │
    │  2. Encrypt AES key  │                          │
    │  with both public    │                          │
    │  keys                │                          │
    │                      │                          │
    │──────────────────────>                          │
    │  3. Send via WS      │                          │
    │  /app/sendPrivate    │                          │
    │                      │                          │
    │              ┌───────┴────────┐                 │
    │              │ 4. Persist to  │                 │
    │              │   PostgreSQL   │                 │
    │              └───────┬────────┘                 │
    │                      │                          │
    │              ┌───────┴────────┐                 │
    │              │ 5. Invalidate  │                 │
    │              │  Redis cache   │                 │
    │              └───────┬────────┘                 │
    │                      │                          │
    │                      │──────────────────────────>
    │                      │  6. Push to              │
    │                      │  /user/{id}/queue/private│
    │                      │                          │
    │<─────────────────────│                          │
    │  7. Confirm to       │                          │
    │  /queue/confirmation │                          │
    │                      │                          │
    │                      │                          │
    │                      │<─────────────────────────│
    │                      │  8. markAsRead           │
    │                      │                          │
    │<─────────────────────│                          │
    │  9. Read receipt     │                          │
    │  notification        │                          │
```

---

## Security Model

### Authentication Chain

```
Request → CorsFilter → SecurityFilterChain → JwtDecoder → Controller
```

### Endpoint Security

| Pattern | Access |
|---------|--------|
| `/api/auth/**` | Public |
| `/api/user/public/**` | Public |
| `/swagger-ui/**`, `/api-docs/**` | Public |
| `/api/admin/**` | ROLE_ADMIN |
| `/api/user/**` | ROLE_ADMIN, ROLE_USER |
| `/ws/**` | Authenticated |
| Everything else | Authenticated |

### Session Management

- **Stateless**: No server-side HTTP sessions
- **Redis Sessions**: User sessions stored in Redis for token validation
- **Session Data**: Includes user info, tokens, client IP, user agent

---

## Database Schema

### Core Tables

```
users
├── id (UUID, PK)
├── username (UNIQUE)
├── email (UNIQUE)
├── password
├── pin
├── public_key
├── current_key_version
├── profile_image
├── blockchain_consent
├── is_online
├── last_seen
├── created_at
└── updated_at

roles
├── id (INT, PK)
└── authority (UNIQUE)

user_role (M:N)
├── user_id (FK)
└── role_id (FK)

chat_messages
├── id (UUID, PK)
├── sender_id (FK → users)
├── recipient_id (FK → users)
├── cipher_text
├── iv
├── encrypted_key_for_sender
├── encrypted_key_for_recipient
├── sender_key_version
├── recipient_key_version
├── message_type
├── is_read
├── one_time
├── read_timestamp
└── timestamp

chat_requests
├── id (UUID, PK)
├── requester_id (FK → users)
├── recipient_id (FK → users)
├── ciphertext
├── iv
├── encrypted_key_*
├── status (PENDING/ACCEPTED/REJECTED/EXPIRED/CANCELLED)
└── timestamp

chat_files
├── id (UUID, PK)
├── message_id (FK → chat_messages)
├── file_name
├── mime_type
├── size_bytes
├── iv
├── encrypted_key_sender
├── encrypted_key_recipient
└── key versions

group_chats
├── id (UUID, PK)
├── name
├── participants (M:N → users)
└── created_at

activities
├── id (UUID, PK)
├── user_id (FK → users)
├── activity_type
├── description
├── is_suspicious
├── metadata
└── timestamp

user_blocks
├── id (UUID, PK)
├── blocker_id (FK → users)
├── blocked_id (FK → users)
└── created_at

user_reports
├── id (UUID, PK)
├── reporter_id (FK → users)
├── reported_id (FK → users)
├── reason
└── timestamp
```

---

## API Documentation

Interactive API documentation is available via **Swagger UI** at:

```
http://localhost:8081/swagger-ui.html
```

### Main Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Authenticate user |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Invalidate session |
| GET | `/api/user` | Get current user profile |
| POST | `/api/user/publicKey` | Update encryption public key |
| POST | `/api/user/report` | Report a user |
| POST | `/api/user/block/{id}` | Block a user |
| GET | `/api/messages?recipientId=` | Get conversation |
| GET | `/api/chats` | Get chat summaries |
| POST | `/api/chat-requests` | Send chat request |
| POST | `/api/chat-requests/{id}/accept` | Accept request |
| POST | `/api/group-chats` | Create group chat |
| POST | `/api/files` | Upload encrypted file |
| GET | `/api/files/{id}` | Download file |
| GET | `/api/blockchain/events` | Query blockchain events |

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `HYPERLEDGER_URL` | Hyperledger Fabric API URL | Required |
| `HYPERLEDGER_USER` | Hyperledger basic auth user | Required |
| `HYPERLEDGER_PASSWORD` | Hyperledger basic auth password | Required |

### Application Properties

```properties
# Server
server.port=8081
spring.application.name=user-context

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/user_context
spring.datasource.username=postgres
spring.datasource.password=pazzword
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.session.store-type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# MinIO
vaultx.minio.endpoint=https://minio.api.example.com
vaultx.minio.access-key=<access-key>
vaultx.minio.secret-key=<secret-key>
vaultx.files.bucket-name=vaultx-files

# JWT
jwt.ttlInMinutes=30
jwt.refreshTtlInMinutes=60
```

---

## Running the Application

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Redis (port 6379)
- Kafka + Zookeeper (port 9092)
- MinIO (ports 9000, 9001)
- HashiCorp Vault (port 8201)

### Run the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Or with environment variables
export HYPERLEDGER_URL=https://your-hyperledger-api
export HYPERLEDGER_USER=admin
export HYPERLEDGER_PASSWORD=secret
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Build JAR

```bash
./mvnw clean package -DskipTests
java -jar target/user-context-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

---

## Testing

### Unit Tests

```bash
./mvnw test
```

### Integration Tests

Integration tests use **Testcontainers** for PostgreSQL and Kafka:

```bash
./mvnw verify
```

Test classes:
- `AuthenticationIT` - Authentication flow tests
- `UserIT` - User management tests
- `ChatRequestIT` - Chat request workflow tests
- `PrivateChatIT` - Messaging tests
- `ChatServiceTest` - Service unit tests

---

## Scheduled Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| `ChatRequestExpirationJob` | Daily at 03:15 | Expires PENDING chat requests older than 2 days |

---

## License

This project is part of a Master's Thesis at ISM and is not licensed for public use.

---

## Author

Developed as part of the VaultX secure communication platform.

