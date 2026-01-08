# FISC Connection Integration Tests

## Overview

This package contains comprehensive end-to-end integration tests for FISC (Financial Information Service Center) connectivity. The tests verify the complete communication workflow between the FEP system and FISC servers.

## Test Components

### 1. FiscConnectionSimulator

A complete FISC server simulator built with Netty that:
- Simulates FISC TCP/IP server behavior
- Supports ISO 8583 message encoding/decoding
- Provides configurable response handlers
- Tracks connection statistics
- Handles concurrent client connections

**Features:**
- Network Management (0800/0810) - Sign On, Sign Off, Echo Test
- Financial Transactions (0200/0210) - Withdrawal, Transfer, Balance Inquiry
- Reversals (0400/0410)
- Customizable message handlers

### 2. FiscConnectionIntegrationTest

Comprehensive test suite covering all aspects of FISC connectivity:

#### Connection Tests
- **01. TCP/IP Connection Establishment and Teardown**
  - Verifies successful connection to FISC
  - Validates proper disconnection handling
  - Checks connection state management

#### Network Management Tests
- **02. Sign On (0800/0810 code=001)**
  - Tests FISC sign-on procedure
  - Verifies response code handling
  - Validates state transition to SIGNED_ON

- **03. Echo Test (0800/0810 code=301)**
  - Tests heartbeat mechanism
  - Verifies keep-alive functionality

- **04. Sign Off (0800/0810 code=002)**
  - Tests graceful sign-off procedure
  - Validates proper session termination

#### Financial Transaction Tests
- **10. Withdrawal Transaction (0200/0210)**
  - Processing Code: 010000
  - Verifies withdrawal flow
  - Validates STAN matching

- **11. Transfer Transaction (0200/0210)**
  - Processing Code: 400000
  - Tests fund transfer
  - Validates account field handling

- **12. Balance Inquiry (0200/0210)**
  - Processing Code: 300000
  - Tests inquiry operations

#### Reversal Tests
- **20. Reversal Transaction (0400/0410)**
  - Tests original transaction + reversal flow
  - Validates original STAN reference (Field 90)

#### Concurrency Tests
- **30. Concurrent Transactions**
  - Sends 10 simultaneous transactions
  - Verifies correct STAN matching
  - Tests thread-safe operation

#### Error Handling Tests
- **40. Timeout Handling**
  - Tests request timeout (1 second)
  - Verifies timeout exception handling

- **41. Declined Transaction**
  - Tests response code 51 (Insufficient Funds)
  - Validates error response handling

#### Resilience Tests
- **50. Auto-Reconnection**
  - Tests automatic reconnection after disconnect
  - Verifies retry mechanism

#### Performance Tests
- **60. High Volume Transactions**
  - Processes 100 concurrent transactions
  - Measures TPS (Transactions Per Second)
  - Target: >10 TPS

#### Complete Lifecycle Test
- **100. Full Lifecycle Test**
  - Complete workflow: Connect → Sign On → Echo → Transactions → Sign Off → Disconnect
  - Includes: Withdrawal, Transfer, Balance Inquiry
  - Validates entire session lifecycle

## Test Results

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
Time elapsed: ~7-8 seconds
TPS achieved: >3000 (for 100 concurrent transactions)
```

## Running the Tests

### Run all FISC integration tests:
```bash
mvn test -pl fep-integration -Dtest=FiscConnectionIntegrationTest
```

### Run a specific test:
```bash
mvn test -pl fep-integration -Dtest=FiscConnectionIntegrationTest#testSignOn
```

### Run with debug logging:
```bash
mvn test -pl fep-integration -Dtest=FiscConnectionIntegrationTest -X
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Test Environment                          │
│                                                              │
│  ┌──────────────────┐              ┌──────────────────┐     │
│  │   FiscClient     │    TCP/IP    │ FiscConnection   │     │
│  │   (Under Test)   │◄────────────►│   Simulator      │     │
│  │                  │              │  (Mock Server)   │     │
│  └──────────────────┘              └──────────────────┘     │
│           │                                 │                │
│           │                                 │                │
│  ┌────────▼─────────┐              ┌───────▼──────────┐     │
│  │ Netty Client     │              │  Netty Server    │     │
│  │ - Encoder/Decoder│              │  - Encoder/Decoder│    │
│  │ - Handler        │              │  - Handler       │     │
│  └──────────────────┘              └──────────────────┘     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Message Flow

### Sign On Sequence
```
Client                          Simulator
  │                                │
  │──── Connect TCP ──────────────►│
  │                                │
  │──── 0800 (Sign On) ───────────►│
  │                                │
  │◄─── 0810 (Approved) ───────────│
  │     Field 39 = "00"            │
```

### Financial Transaction Sequence
```
Client                          Simulator
  │                                │
  │──── 0200 (Withdrawal) ────────►│
  │     Field 3 = 010000           │
  │     Field 4 = 000000010000     │
  │                                │
  │◄─── 0210 (Approved) ───────────│
  │     Field 39 = "00"            │
  │     STAN matches request       │
```

## Key Test Scenarios Covered

1. **Basic Connectivity**
   - Connection establishment
   - Connection termination
   - State management

2. **Protocol Compliance**
   - ISO 8583 message formatting
   - FISC-specific field definitions
   - BCD encoding/decoding
   - Bitmap handling

3. **Transaction Processing**
   - Request/response matching via STAN
   - Multiple transaction types
   - Concurrent request handling

4. **Error Handling**
   - Timeout scenarios
   - Declined transactions
   - Connection failures
   - Invalid messages

5. **Reliability**
   - Auto-reconnection
   - Message retransmission
   - State recovery

6. **Performance**
   - High-volume transaction processing
   - Concurrent connection handling
   - TPS measurement

## Configuration

Tests use the following configuration:
- **Host**: localhost
- **Port**: Random available port (assigned by OS)
- **Connect Timeout**: 5 seconds
- **Read Timeout**: 10 seconds
- **Institution ID**: 0004

## Dependencies

- **fep-communication**: Client communication components
- **fep-message**: ISO 8583 message handling
- **Netty**: Network I/O framework
- **JUnit 5**: Testing framework

## Notes

- Each test is isolated with its own client instance
- The simulator is shared across all tests (started once in @BeforeAll)
- Tests are ordered to progress from simple to complex scenarios
- All tests include proper cleanup in @AfterEach
- Thread.sleep() is used strategically to ensure proper connection state propagation

## Future Enhancements

- [ ] Add HSM integration tests
- [ ] Add MAC validation tests
- [ ] Add PIN block encryption tests
- [ ] Add batch transaction tests
- [ ] Add failover scenario tests
- [ ] Add network partition tests
