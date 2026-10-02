# ADR 0003: Deterministic source and canonical match identity

## Status

Accepted.

## Context

Provider records can be corrected without becoming different real-world
matches, and multiple providers can describe the same match. Raw warehouse
filenames and selected cricket facts therefore cannot provide stable identity.
The future reconciliation application must also be able to combine source
records without coupling warehouse joins to provider identifiers.

## Decision

- A provider adapter emits a source reference alongside the provider-neutral
  `BbbMatchData` payload.
- A source record ID is UUIDv5 over an immutable provider namespace and the
  provider's normalized local key. Cricsheet owns namespace
  `f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b`; its key is the unchanged safe
  source-file basename stem without the extension. Cricsheet basename stems
  must start with a letter or digit and may contain letters, digits, `_`, and
  `-`, so keys such as `wi_201706` remain provider-local identity. This
  namespace must never be regenerated or reassigned.
- A raw content digest is lowercase SHA-256 over the exact downloaded bytes.
  It is revision evidence and is not match identity.
- Source and canonical envelopes carry an explicit contract version.
- Until reconciliation is available, single-source promotion derives a
  canonical UUIDv5 in namespace `6b456afa-09d1-5cc1-a820-77617ad17ca2` from
  the source record UUID and records explicit single-source merge evidence.
- The reconciliation bounded context emits one canonical envelope with a
  stable canonical UUID, all source references, merge evidence, and the merged
  payload. The updater accepts that canonical contract only.
- The warehouse retains generated numeric keys for compact joins and stores a
  unique canonical UUID separately.

## Rejected alternatives

- Raw-byte and canonical-JSON identities change when a provider corrects a
  record.
- Natural cricket facts can be incomplete, corrected, or non-unique.
- UUID primary and foreign keys throughout the warehouse increase index and
  join storage without improving reconciliation semantics.

## Consequences

Reprocessing the same provider key is deterministic across machines, while a
changed payload is independently detectable. Provider namespaces prevent
equal local keys from colliding. Reconciliation remains replaceable and
reviewable rather than leaking into provider adapters or warehouse loading.