# Leader Election:

Election algorithms are essential in distributed data systems to have a consensus between nodes, of which node is
labeled as the master node. It is responsible for coordination between nodes or centralized lookups.
The imperative question boils down to defining the criteria for choosing the leading node when there are multiple candidates.

# Bully algorithm:

Any node can trigger a request for an election. However, one node can only issue a singular request at one point in time.
The algorithm operates by identifying all the non-faulty nodes and electing the node with the largest identifier as the leader.
There can be three kinds of messages that nodes would exchange between each other during the bully algorithm:
* Election message
* OK message
* Coordinator message

This algorithm is particularly applicable in scenarios where a leader node is required for coordination and there is 
a possibility that the leader may fail or become unresponsive, necessitating the election of a new leader.

1. Initiation of Election:
   * If a node (let's call it Node A) detects that the current leader is not responding, it initiates an election.
   * Node A sends an election message to all nodes with higher IDs.

2. Response to Election:
   * Any node with a higher ID, upon receiving the election message, responds to Node A, indicating it will take over the election process.
   * Node A then waits for a certain time for responses.

3. Taking Charge:
   * If Node A receives no response (meaning there are no higher nodes active), it declares itself the leader.
   * If it receives a response, it steps down, allowing the responding node(s) to continue the election process.

4. Election by Higher Nodes:
   * Higher ID nodes follow the same process: sending election messages to even higher ID nodes, waiting for responses, and taking charge if no higher node responds.

5. Election Completion:
   * The process continues until the highest active node declares itself the leader.
   * The new leader then sends a victory message to all nodes with lower IDs to inform them of the leadership change.

# Chat Requirements (CZ):

* Programy musí podporovat interaktivní i dávkové řízení (např. přidání a odebrání procesu).
* Kromě správnosti algoritmu se zaměřte i na prezentaci výsledků. 
  * Cílem je aby bylo poznat co Váš program právě dělá.
* Srozumitelné výpisy logujte na konzoli i do souboru/ů. 
  * Výpisy budou opatřeny časovým razítkem logického času.
* Každý uzel bude mít jednoznačnou identifikaci. 
  * Doporučená je kombinace IP adresy a portu.
* Je doporučeno mít implementovanou možnost zpožďovat odeslání/příjem zprávy. 
  * Vhodné pro generování souběžných situací.
* Chatovací program 
  * Program umožnující zasílání zpráv mezi jednotlivými uzly. Zprávy bude vždy možné v rámci celého systému úplně uspořádat (k synchronizaci použijte buď 'vůdce' nebo výlučný přístup v rámci celého systému). Jednotlivé uzly budou implementovat minimálně tyto funkce: pošli/přijmi zprávu, odhlaš se ze systému, skonči bez odhlášení, přihlaš se do systému.

