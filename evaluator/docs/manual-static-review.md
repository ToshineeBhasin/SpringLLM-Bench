# Pilot manual/static review protocol

Run this after the black-box tests.

## Task 1
- password hashing/storage
- JWT expiry generation and enforcement
- signing key handling
- sensitive logging
- DTO/entity exposure
- positive ADMIN path if provisioned

## Task 2
- transaction boundary location
- BigDecimal/precise monetary representation
- locking/versioning/atomic update strategy
- transaction history is in the same atomic unit

## Task 3
- concurrency-control strategy (optimistic/pessimistic/atomic update)
- retry behavior if optimistic locking is used
- quantity invariant cannot go below zero

## Task 4
- raw/native query construction
- string concatenation of user-controlled SQL/JPQL
- parameter binding
- pagination/limit handling if present

## Task 5
- consumer acknowledgement/offset strategy
- retry and dead-letter behavior
- transaction boundary between DB update and acknowledgement
- unique constraint/idempotency enforcement on eventId
- logging of message payloads if sensitive

Classify each finding as:
1. CONFIRMED — reproduced by behavior/test;
2. STRONG_STATIC — directly unsafe construction with clear evidence;
3. TOOL_WARNING_ONLY — scanner warning without independent validation.
