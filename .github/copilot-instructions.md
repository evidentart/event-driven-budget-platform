# Repository Instructions

## Project

* This is `evidentart/event-driven-budget-platform`, a Spring Boot event-driven budget platform.
* Preserve the existing architecture and intentional design choices unless the task explicitly requests an architectural change.
* Prefer the smallest correct change over broad refactoring.

## Engineering

* Inspect the existing implementation before proposing or making non-trivial changes.
* Follow existing conventions for Java, Spring Boot, Protobuf, Kafka, RabbitMQ, gRPC, persistence, validation, and testing.
* Do not introduce new frameworks, infrastructure, patterns, or services unless explicitly requested or clearly required.
* Do not change dependency versions or communication architecture without explicit approval.
* Do not replace an existing design merely because another approach is more common.

## Change Boundaries

* Implement only the requested and approved change.
* Do not modify unrelated audit findings.
* Do not redesign Kafka reliability or introduce an outbox unless explicitly approved.
* Do not change authentication/security architecture unless explicitly requested.
* Preserve existing resilience and graceful-degradation behavior unless there is clear evidence it is incorrect.

## Verification

* Prefer repository evidence over assumptions.
* When requirements are ambiguous, inspect the relevant code and report the ambiguity before making an architectural decision.
* Preserve existing event schemas and conventions where possible.
* Add focused tests for changed behavior and important failure cases.
* Run relevant tests and build/validation commands after implementation.
* Report exactly what changed and what passed or failed.
* Keep responses concise but sufficiently detailed to explain the important reasoning, evidence, risks, and conclusions.
* Do not narrate routine tool calls, searches, file inspection, or intermediate steps.
* Avoid unnecessary repetition, filler, and long walkthroughs unless explicitly requested.
* Prefer structured summaries and focused bullet points over lengthy prose.

## Git

* Do not commit, push, force-push, rewrite history, or create/delete branches unless explicitly requested.
* Keep changes limited to the approved scope.
