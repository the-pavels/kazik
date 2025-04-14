## Description 

The project is a distributed, real-time system written in Scala that manages roulette tables and user betting. It
leverages asynchronous and concurrent processing using fs2 Streams and Cats Effect, with state persistence via Redis and
messaging with Pulsar.

At a high level, the system works as follows:

Event Ingestion and Dispatch:
User actions (e.g., joining tables, placing bets) and table events (e.g., bets accepted, bets closed) are sent as
messages. These messages are produced and dispatched using Pulsar. Each module responsible for processing events (for
example, the user-handler and table-handler) consumes messages from Pulsar, acknowledges them (using techniques such as
parallel evaluation via fs2’s parEvalMap), and dispatches the corresponding events.

State Management:
The state of tables and users is managed via a Redis-backed state storage. The system uses compare-and-set (CAS)
operations implemented with Lua scripts (see module/core/src/fr/adapter/redis/StateStorage.scala) to ensure state
updates are atomic and consistent despite concurrent updates.

Module Responsibilities:

The **sticky** module handles the WebSocket connections for real-time communication. It uses a pipeline that decodes
incoming WebSocket frames into user actions and encodes outgoing events.
The **table-handler** processes events related to table operations (e.g., placing bets, joining/leaving tables) and manages
table state. It groups events by table then processes them concurrently while ensuring that events for each table are
handled in order if needed.
The **user-handler** processes user-level events such as socket connections, placing bets, and receiving updates. It
combines events from both user actions and table events to update user state and broadcast events back to the client.
Concurrency and Error Handling:
The system processes messages concurrently (using operators like parEvalMap), which enables it to handle bursts of
activity. Each processing stream includes error handling—acknowledging successfully processed messages while nacking (
negatively acknowledging) those that fail, ensuring partial success and resilience.

Overall Flow:
A client connects via WebSocket (managed in module/sticky/src/fr/sticky/WebSocketHandler.scala), sends user inputs, and
incoming actions are dispatched to user-handler and table-handler modules. These modules update state and produce new
events that propagate back to users or trigger further processing such as starting a game, closing bets, or calculating
wins.

This design creates a responsive system that processes concurrent events efficiently while maintaining consistency
across distributed state and respecting the sequence of critical operations (like bet placement and game management).

## Running
To run the project, you need to have Docker installed. The project uses Docker Compose to set up the necessary services.
1. Clone the repository:
```bash
git clone https://github.com/the-pavels/kazik
```
2. Navigate to the project directory:
```bash
cd kazik
```
3. Run Docker images:
```bash
docker-compose up -d
```
4. Run the application:
```bash
sbt devapp/run
```
5. Start simulation in a separate terminal:
```bash
sbt "simulator/testOnly fr.simulator.scenario.HappyPathSuite"
```