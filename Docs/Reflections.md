# Reflection

## protocol design
We designed a line-based text protocol on top of TCP in lists.
the hardest part was to decide what the broker and the nodes should know.
we ended up with a stateful broker that remembers each connections type, id, role and subscriptions, and that the nodes should stay simple.
the nodes send READING and STATUS. if we did this again we would start a lot earlier than we did, we also tried to contact our last group member,
but he never answered us and ended up not doing anything.

## TCP instead of UDP
We decided to pick TCP since it gives us reliable and ordered delivery, and it also tells us when a peer disappears. the cost of TCP is that 
it is a stream so we needed our own message framing wich was one line per message. a downside with TCP is that
the latency is higher than with UDP but since we update the graph every 2 seconds we think it is an acceptable outcome that it is 
slightly slower than UDP

## Broker
Having a central broker made it easy to support many nodes and panels, check roles in one place and to run safety rules
even withouth having a panel open. the problem is that there is a single point of failure,
if the broker stops then nothing can be controlled. We reduced the impact on the broker with a "heartbeat" that pings every
3 seconds and times out after 9 seconds with an automatic reconnect so that the panels and nodes recovers when the broker comes back.

## failure handling
We learned that failures must be designed in from the start. Nodes have a buffor up to 500 readings while disconnected and sends them with their real time
(ageMs) after reconnecting. Safety reading ignore readings older than 5 seconds so that old
data dont raise a false alarm.

## How we worked
The group started with three members. the third member did not take part. the two of us shared the work
we worked in a short and intensive sprint over 3 days to try and get everything done.
we had some problems when we started since the one that made the repository managed to get a "main"
and a "master" branch so merging those together was a bit of a struggle. we also forgot to launch the GUI
towards the end and there was a few faults in the code wich we fixed up in. because we started so late we made an initial
idea of what we wanted and started coding right away. when we got stuck on something or something we were unsure on how to implement
decided we to use AI to help us speed up the process. we checked over the code of the ai and wrote every line ourselves too.

## Limitations
-our traffic is not encrypted and password are in plain text.

-we added one fixed password per role and a fixed alarm code.

-All states, event log and status is kept only in memory

-Devices are simulated and node ids are chosen by hand.

-guest visibility is decided per sensor type and not per individual sensor.

## what we would do with more time
-we would like to have added hashed passwords, and automatic node id that is assigned by the broker

-saving event log to the disk so not everything is lost upon closing the program

-Planning our sprints from the beginning of the assignment and starting way earlier than what we din